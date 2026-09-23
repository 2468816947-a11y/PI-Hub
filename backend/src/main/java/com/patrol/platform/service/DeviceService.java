package com.patrol.platform.service;

import com.patrol.platform.common.BusinessConstants;
import com.patrol.platform.common.BusinessException;
import com.patrol.platform.common.ErrorCode;
import com.patrol.platform.common.IdGenerator;
import com.patrol.platform.entity.Device;
import com.patrol.platform.entity.Device.Position;
import com.patrol.platform.entity.PatrolTask;
import com.patrol.platform.kafka.KafkaMessage;
import com.patrol.platform.repository.DeviceRepository;
import com.patrol.platform.repository.PatrolTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备业务 Service。
 * <p>
 * 处理来自 Kafka 的 REGISTER / HEARTBEAT / STATUS 消息, 维护设备台账状态。
 * <p>
 * 权威状态规则(接口文档 §2.2):
 * <ul>
 *   <li>REGISTER: 创建设备记录(初始 OFFLINE, battery=100, 位置由 REGISTER data 提供)</li>
 *   <li>HEARTBEAT: 更新 lastHeartbeat / battery; 若当前为 OFFLINE 则置 ONLINE(首条心跳置 ONLINE)</li>
 *   <li>STATUS: 强制置 ONLINE, 更新 battery / faultCode / position</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final PatrolTaskRepository patrolTaskRepository;
    private final IdGenerator idGenerator;

    /**
     * 处理 REGISTER 消息(接口文档附录 A.2: REGISTER data = {name, model, area, lng, lat})。
     * <p>
     * 后端行为: 收到即创建设备记录; 已存在则更新 name/model/area/position(避免重注册覆盖电量/状态)。
     */
    public Device registerOrUpdate(KafkaMessage msg) {
        Map<String, Object> data = msg.getData();
        String deviceId = msg.getDeviceId();

        Optional<Device> existing = deviceRepository.findById(deviceId);
        Device device = existing.orElseGet(() -> Device.builder()
                .deviceId(deviceId)
                .deviceType(msg.getDeviceType())
                .status(BusinessConstants.DEVICE_STATUS_OFFLINE)
                .battery(BusinessConstants.INITIAL_BATTERY)
                .faultCode("")
                .registerTime(OffsetDateTime.now())
                .build());

        // 始终更新这些字段(后注册以最新为准)
        device.setDeviceType(msg.getDeviceType());
        if (data != null) {
            applyString(data, "name").ifPresent(device::setName);
            applyString(data, "model").ifPresent(device::setModel);
            applyString(data, "area").ifPresent(device::setArea);
            Double lng = applyDouble(data, "lng");
            Double lat = applyDouble(data, "lat");
            if (lng != null && lat != null) {
                device.setPosition(Position.builder().lng(lng).lat(lat).build());
            }
        }
        // REGISTER 不改变 status/battery, 由心跳/离线检测维护

        Device saved = deviceRepository.save(device);
        log.info("Device registered: deviceId={}, name={}, area={}",
                saved.getDeviceId(), saved.getName(), saved.getArea());
        return saved;
    }

    /**
     * 处理 HEARTBEAT 消息(接口文档附录 A.2: HEARTBEAT data = {battery})。
     * <p>
     * 更新 lastHeartbeat 与 battery; 当前 OFFLINE 设备收到心跳后置 ONLINE。
     */
    public void updateHeartbeat(KafkaMessage msg) {
        String deviceId = msg.getDeviceId();
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            log.warn("Heartbeat for unknown device: deviceId={}, skip", deviceId);
            return;
        }
        device.setLastHeartbeat(parseTimestamp(msg.getTimestamp()));
        if (msg.getData() != null) {
            Integer battery = applyInteger(msg.getData(), "battery");
            if (battery != null) {
                device.setBattery(battery);
            }
        }
        // 权威规则: 收到心跳即置 ONLINE(可能此前已被离线检测置为 OFFLINE)
        if (BusinessConstants.DEVICE_STATUS_OFFLINE.equals(device.getStatus())) {
            log.info("Device transition: OFFLINE -> ONLINE via heartbeat, deviceId={}", deviceId);
            device.setStatus(BusinessConstants.DEVICE_STATUS_ONLINE);
        }
        deviceRepository.save(device);
    }

    /**
     * 处理 STATUS 消息(接口文档附录 A.2: STATUS data = {online, battery, faultCode, mode})。
     * <p>
     * 强制置 ONLINE, 更新 battery / faultCode / position(若有)。
     */
    public void updateStatus(KafkaMessage msg) {
        String deviceId = msg.getDeviceId();
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            log.warn("Status for unknown device: deviceId={}, skip", deviceId);
            return;
        }
        Map<String, Object> data = msg.getData();
        if (data != null) {
            // online=false 仅作参考, 真实状态由离线检测定时扫描决定; 这里永远置 ONLINE
            device.setStatus(BusinessConstants.DEVICE_STATUS_ONLINE);
            Integer battery = applyInteger(data, "battery");
            if (battery != null) {
                device.setBattery(battery);
            }
            String faultCode = applyString(data, "faultCode").orElse(null);
            if (faultCode != null) {
                device.setFaultCode(faultCode);
            }
        }
        device.setLastHeartbeat(parseTimestamp(msg.getTimestamp()));
        deviceRepository.save(device);
    }

    /**
     * 设备离线判定: lastHeartbeat 超阈值 → OFFLINE。
     * 由 DeviceOfflineScheduler 调用。
     */
    public void markOffline(String deviceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            if (BusinessConstants.DEVICE_STATUS_ONLINE.equals(device.getStatus())) {
                device.setStatus(BusinessConstants.DEVICE_STATUS_OFFLINE);
                deviceRepository.save(device);
            }
        });
    }

    // ==================== REST API 业务方法 ====================

    /**
     * 分页查询设备列表(支持 status / type / area 多条件)。
     */
    public Page<Device> listDevices(String status, String type, String area, Pageable pageable) {
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasType = type != null && !type.isEmpty();
        boolean hasArea = area != null && !area.isEmpty();

        if (hasType && hasStatus && hasArea) {
            return deviceRepository.findByDeviceTypeAndStatusAndAreaContainingIgnoreCase(type, status, area, pageable);
        } else if (hasType && hasStatus) {
            return deviceRepository.findByDeviceTypeAndStatus(type, status, pageable);
        } else if (hasType) {
            return deviceRepository.findByDeviceType(type, pageable);
        } else if (hasStatus) {
            return deviceRepository.findByStatus(status, pageable);
        } else if (hasArea) {
            return deviceRepository.findByAreaContainingIgnoreCase(area, pageable);
        }
        return deviceRepository.findAll(pageable);
    }

    /**
     * 获取设备详情, 不存在抛 404。
     */
    public Device getDeviceOrThrow(String deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "设备不存在: " + deviceId));
    }

    /**
     * 查询设备, 不存在返回 null(用于手工上传场景, deviceId 可选)。
     */
    public Device getDeviceOrNull(String deviceId) {
        if (deviceId == null) return null;
        return deviceRepository.findById(deviceId).orElse(null);
    }

    /**
     * 新增设备(POST /api/devices)。
     * deviceId 缺省时由后端生成; 传入必须唯一, 否则 409。
     */
    public Device createDevice(String deviceId, String deviceType, String name, String model,
                                String area, Device.Position position) {
        String finalId = (deviceId == null || deviceId.isEmpty())
                ? idGenerator.nextDeviceId(deviceType)
                : deviceId;

        if (deviceRepository.existsByDeviceId(finalId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "deviceId 已存在: " + finalId);
        }

        Device device = Device.builder()
                .deviceId(finalId)
                .deviceType(deviceType)
                .name(name)
                .model(model)
                .area(area)
                .position(position != null ? position : Position.builder().lng(0.0).lat(0.0).build())
                .status(BusinessConstants.DEVICE_STATUS_OFFLINE)
                .battery(BusinessConstants.INITIAL_BATTERY)
                .faultCode("")
                .registerTime(OffsetDateTime.now())
                .build();
        return deviceRepository.save(device);
    }

    /**
     * 更新设备(PUT /api/devices/{id}), 部分更新。
     * deviceId/deviceType/status 不可改(状态由心跳驱动)。
     */
    public Device updateDevice(String deviceId, String name, String model, String area,
                                Device.Position position, String faultCode) {
        Device device = getDeviceOrThrow(deviceId);
        if (name != null) device.setName(name);
        if (model != null) device.setModel(model);
        if (area != null) device.setArea(area);
        if (position != null) device.setPosition(position);
        if (faultCode != null) device.setFaultCode(faultCode);
        return deviceRepository.save(device);
    }

    /**
     * 删除设备(DELETE /api/devices/{id})。
     * 若存在未完成任务(CREATED/DISPATCHED/RUNNING)则 409。
     * <p>
     * 不级联删除历史数据(任务/告警/事件/文件按 deviceId 留存, 供事后审计)。
     */
    public void deleteDevice(String deviceId) {
        Device device = getDeviceOrThrow(deviceId);
        List<String> activeStatuses = List.of(
                BusinessConstants.TASK_STATUS_CREATED,
                BusinessConstants.TASK_STATUS_DISPATCHED,
                BusinessConstants.TASK_STATUS_RUNNING);
        List<PatrolTask> activeTasks = patrolTaskRepository
                .findByDeviceIdsContainingAndStatusIn(deviceId, activeStatuses);
        if (!activeTasks.isEmpty()) {
            String taskIds = activeTasks.stream().map(PatrolTask::getTaskId).reduce((a, b) -> a + "," + b).orElse("");
            throw new BusinessException(ErrorCode.CONFLICT,
                    "设备存在未完成任务: " + taskIds + ", 请先结束或删除任务");
        }
        deviceRepository.delete(device);
        log.info("Device deleted: deviceId={}", deviceId);
    }

    private OffsetDateTime parseTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(iso);
        } catch (Exception e) {
            log.warn("Invalid timestamp format, fallback to now: {}", iso);
            return OffsetDateTime.now();
        }
    }

    private Optional<String> applyString(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return Optional.empty();
        return Optional.of(String.valueOf(v));
    }

    private Integer applyInteger(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double applyDouble(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
