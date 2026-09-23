package com.patrol.platform.service;

import com.patrol.platform.common.BusinessConstants;
import com.patrol.platform.common.BusinessException;
import com.patrol.platform.common.ErrorCode;
import com.patrol.platform.common.IdGenerator;
import com.patrol.platform.entity.Alarm;
import com.patrol.platform.entity.Alarm.AlarmPosition;
import com.patrol.platform.entity.Device;
import com.patrol.platform.elasticsearch.PatrolEventDocument;
import com.patrol.platform.kafka.KafkaMessage;
import com.patrol.platform.repository.AlarmRepository;
import com.patrol.platform.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 告警规则判定与持久化服务。
 * <p>
 * 四类告警规则(backend.mdc §7 + 接口文档 §2.2):
 * <ul>
 *   <li>TEMP_OVER: 测温 > 任务 params.tempThreshold(缺省 80); 严重>80, 重要 60~80</li>
 *   <li>BATTERY_LOW: 电量 < 20%</li>
 *   <li>OFFLINE: lastHeartbeat 超 15s</li>
 *   <li>FAULT: faultCode 非空</li>
 * </ul>
 * 告警同时写 MongoDB 与 Elasticsearch, 保证列表查询(走 Mongo)与全文检索(走 ES)各自高效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final DeviceRepository deviceRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 处理 STATUS 消息中的电量与故障判定。
     * <p>
     * 同一事件多告警时各自写入; 相同设备+相同类型的未处置(NEW)告警在 createAlarm 中
     * 自动合并刷新(不重复新建), 避免设备持续上报低电量/故障时告警列表刷屏。
     */
    public void checkStatusAlarms(KafkaMessage msg) {
        Device device = deviceRepository.findById(msg.getDeviceId()).orElse(null);
        if (device == null) return;

        Map<String, Object> data = msg.getData();
        if (data == null) return;

        Integer battery = asInteger(data.get("battery"));
        String faultCode = asString(data.get("faultCode")).orElse(null);

        if (battery != null && battery < BusinessConstants.BATTERY_LOW_THRESHOLD) {
            createAlarm(
                    msg.getDeviceId(),
                    null,
                    BusinessConstants.ALARM_TYPE_BATTERY_LOW,
                    BusinessConstants.ALARM_LEVEL_MAJOR,
                    battery.doubleValue(),
                    (double) BusinessConstants.BATTERY_LOW_THRESHOLD,
                    device,
                    String.format("设备电量 %d%%, 低于阈值 %d%%", battery, BusinessConstants.BATTERY_LOW_THRESHOLD)
            );
        }

        if (faultCode != null && !faultCode.isEmpty()) {
            createAlarm(
                    msg.getDeviceId(),
                    null,
                    BusinessConstants.ALARM_TYPE_FAULT,
                    BusinessConstants.ALARM_LEVEL_CRITICAL,
                    null,
                    null,
                    device,
                    String.format("设备上报故障码: %s", faultCode)
            );
        }
    }

    /**
     * 处理 THERMAL 消息的超温告警判定。
     * <p>
     * 阈值: 优先取任务的 params.tempThreshold; 缺省 80。
     * 分级: temperature > threshold → CRITICAL; 介于 threshold-20 ~ threshold → MAJOR。
     */
    public void checkThermalAlarm(KafkaMessage msg, Double tempThreshold) {
        if (tempThreshold == null) {
            tempThreshold = BusinessConstants.DEFAULT_TEMP_THRESHOLD;
        }
        Double temperature = asDouble(msg.getData() != null ? msg.getData().get("temperature") : null);
        if (temperature == null) return;

        Device device = deviceRepository.findById(msg.getDeviceId()).orElse(null);
        if (device == null) return;

        if (temperature > tempThreshold) {
            createAlarm(
                    msg.getDeviceId(),
                    asString(msg.getData().get("taskId")).orElse(null),
                    BusinessConstants.ALARM_TYPE_TEMP_OVER,
                    BusinessConstants.ALARM_LEVEL_CRITICAL,
                    temperature,
                    tempThreshold,
                    device,
                    String.format("%s 热点温度 %.1f℃, 超过严重阈值 %.1f℃",
                            asString(msg.getData().get("targetDevice")).orElse("目标设备"),
                            temperature, tempThreshold)
            );
        } else if (temperature > tempThreshold - 20 && temperature <= tempThreshold) {
            // 一般阈值: 60~80 区间(MAJOR)
            createAlarm(
                    msg.getDeviceId(),
                    asString(msg.getData().get("taskId")).orElse(null),
                    BusinessConstants.ALARM_TYPE_TEMP_OVER,
                    BusinessConstants.ALARM_LEVEL_MAJOR,
                    temperature,
                    tempThreshold,
                    device,
                    String.format("%s 热点温度 %.1f℃, 处于一般阈值区间",
                            asString(msg.getData().get("targetDevice")).orElse("目标设备"),
                            temperature)
            );
        }
    }

    /**
     * 处理设备离线告警(由 DeviceOfflineScheduler 调用)。
     */
    public void createOfflineAlarm(Device device) {
        createAlarm(
                device.getDeviceId(),
                null,
                BusinessConstants.ALARM_TYPE_OFFLINE,
                BusinessConstants.ALARM_LEVEL_CRITICAL,
                null,
                null,
                device,
                String.format("设备离线: 已超过 %d 秒未上报心跳", BusinessConstants.HEARTBEAT_OFFLINE_THRESHOLD_SECONDS)
        );
    }

    /**
     * 通用告警创建: 写 Mongo + 写 ES。
     * <p>
     * 去重合并策略: 同一设备 + 同一告警类型 + 存在未处置(NEW)告警时,
     * 刷新该告警的数值/描述/时间, 不重复新建(离线告警同理, 见测试组文档缺口 3)。
     */
    private void createAlarm(String deviceId, String taskId, String alarmType, String level,
                             Double value, Double threshold, Device device, String description) {
        OffsetDateTime now = OffsetDateTime.now();

        Optional<Alarm> existing = alarmRepository
                .findTopByDeviceIdAndAlarmTypeAndStatusOrderByCreateTimeDesc(
                        deviceId, alarmType, BusinessConstants.ALARM_STATUS_NEW);
        if (existing.isPresent()) {
            Alarm merged = existing.get();
            merged.setTaskId(taskId);
            merged.setLevel(level);
            merged.setValue(value);
            merged.setThreshold(threshold);
            merged.setDescription(description);
            merged.setCreateTime(now);
            alarmRepository.save(merged);
            log.info("Alarm merged (existing NEW): alarmId={}, type={}, deviceId={}",
                    merged.getAlarmId(), alarmType, deviceId);
            // 同步刷新 ES 中同一条告警事件(文档 id 为 alarmId, 直接覆盖)
            indexAlarmEvent(merged, device);
            return;
        }

        Alarm.AlarmPosition pos = device != null && device.getPosition() != null
                ? Alarm.AlarmPosition.builder()
                    .lng(device.getPosition().getLng())
                    .lat(device.getPosition().getLat())
                    .build()
                : null;

        Alarm alarm = Alarm.builder()
                .alarmId(IdGenerator.nextAlarmId())
                .deviceId(deviceId)
                .taskId(taskId)
                .alarmType(alarmType)
                .level(level)
                .value(value)
                .threshold(threshold)
                .position(pos)
                .description(description)
                .status(BusinessConstants.ALARM_STATUS_NEW)
                .createTime(now)
                .build();

        alarmRepository.save(alarm);
        log.info("Alarm created: alarmId={}, type={}, level={}, deviceId={}",
                alarm.getAlarmId(), alarmType, level, deviceId);

        // 同步写入 ES(供检索聚合)
        indexAlarmEvent(alarm, device);
    }

    /**
     * 将告警作为事件写入 ES(架构 §6.3: eventType=ALARM)。
     * <p>
     * 经纬度转换: Mongo 中存 {lng, lat}, ES geo_point 需 {lat, lon}。
     */
    private void indexAlarmEvent(Alarm alarm, Device device) {
        try {
            PatrolEventDocument doc = PatrolEventDocument.builder()
                    .eventId("ALM-" + alarm.getAlarmId())
                    .deviceId(alarm.getDeviceId())
                    .deviceType(device != null ? device.getDeviceType() : null)
                    .deviceName(device != null ? device.getName() : null)
                    .eventType(BusinessConstants.EVENT_TYPE_ALARM)
                    .alarmType(alarm.getAlarmType())
                    .alarmLevel(alarm.getLevel())
                    .taskId(alarm.getTaskId())
                    .area(device != null ? device.getArea() : null)
                    .temperature(alarm.getValue())
                    .threshold(alarm.getThreshold())
                    .position(toGeoPoint(alarm.getPosition()))
                    .eventTime(alarm.getCreateTime() != null ? alarm.getCreateTime().toString() : null)
                    .description(alarm.getDescription())
                    .build();

            IndexOperations indexOps = elasticsearchOperations.indexOps(PatrolEventDocument.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping(indexOps.createMapping(PatrolEventDocument.class));
            }
            IndexQuery query = new IndexQueryBuilder().withId(doc.getEventId()).withObject(doc).build();
            elasticsearchOperations.index(query, indexOps.getIndexCoordinates());
        } catch (Exception e) {
            // ES 写入失败不应影响主流程(Mongo 已落库)
            log.error("Failed to index alarm event to ES: alarmId={}", alarm.getAlarmId(), e);
        }
    }

    private GeoPoint toGeoPoint(Alarm.AlarmPosition pos) {
        if (pos == null || pos.getLat() == null || pos.getLng() == null) return null;
        // 接口文档: 对外 {lng, lat} → ES geo_point {lat, lon}
        return new GeoPoint(pos.getLat(), pos.getLng());
    }

    // ==================== REST API 业务方法 ====================

    /**
     * 分页查询告警(支持 level / type / status 多条件)。
     */
    public Page<Alarm> listAlarms(String level, String alarmType, String status, Pageable pageable) {
        boolean hasLevel = level != null && !level.isEmpty();
        boolean hasType = alarmType != null && !alarmType.isEmpty();
        boolean hasStatus = status != null && !status.isEmpty();

        if (hasLevel && hasType && hasStatus) {
            return alarmRepository.findByLevelAndAlarmTypeAndStatusOrderByCreateTimeDesc(level, alarmType, status, pageable);
        } else if (hasLevel) {
            return alarmRepository.findByLevelOrderByCreateTimeDesc(level, pageable);
        } else if (hasType) {
            return alarmRepository.findByAlarmTypeOrderByCreateTimeDesc(alarmType, pageable);
        } else if (hasStatus) {
            return alarmRepository.findByStatusOrderByCreateTimeDesc(status, pageable);
        }
        return alarmRepository.findAll(pageable);
    }

    /**
     * 告警详情(404 if not exist)。
     */
    public Alarm getAlarmOrThrow(String alarmId) {
        Alarm a = alarmRepository.findByAlarmId(alarmId);
        if (a == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "告警不存在: " + alarmId);
        }
        return a;
    }

    /**
     * 处置告警(PUT /api/alarms/{id}/process)。
     * 已处置返回 409。
     */
    public Alarm processAlarm(String alarmId, String processNote) {
        Alarm alarm = getAlarmOrThrow(alarmId);
        if (BusinessConstants.ALARM_STATUS_PROCESSED.equals(alarm.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "告警已处置, 不能重复处置");
        }
        alarm.setStatus(BusinessConstants.ALARM_STATUS_PROCESSED);
        alarm.setProcessTime(OffsetDateTime.now());
        alarm.setProcessNote(processNote);
        return alarmRepository.save(alarm);
    }

    private Optional<String> asString(Object v) {
        if (v == null) return Optional.empty();
        return Optional.of(String.valueOf(v));
    }

    private Integer asInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
