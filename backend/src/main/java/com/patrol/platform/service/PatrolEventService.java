package com.patrol.platform.service;

import com.patrol.platform.common.BusinessConstants;
import com.patrol.platform.common.IdGenerator;
import com.patrol.platform.elasticsearch.PatrolEventDocument;
import com.patrol.platform.entity.Device;
import com.patrol.platform.kafka.KafkaMessage;
import com.patrol.platform.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 巡检事件索引 Service。
 * <p>
 * 将 IMAGE/THERMAL/SENSOR 消息写入 ES patrol-event 索引(架构 §6.3)。
 * ALARM 事件由 AlarmService 单独写入, 此处不重复。
 * <p>
 * 注意: 经纬度转换遵循 backend.mdc §5(对外 {lng,lat}, ES geo_point 需 {lat,lon})。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatrolEventService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final DeviceRepository deviceRepository;
    private final IdGenerator idGenerator;

    /**
     * 索引 IMAGE 事件。
     */
    public void indexImage(KafkaMessage msg, boolean hasHdfsFile, String hdfsPath) {
        if (msg.getData() == null) return;
        Device device = deviceRepository.findById(msg.getDeviceId()).orElse(null);
        PatrolEventDocument doc = PatrolEventDocument.builder()
                .eventId(idGenerator.nextEventId())
                .deviceId(msg.getDeviceId())
                .deviceType(msg.getDeviceType())
                .deviceName(device != null ? device.getName() : null)
                .eventType(BusinessConstants.EVENT_TYPE_IMAGE)
                .taskId(asString(msg.getData().get("taskId")).orElse(null))
                .area(device != null ? device.getArea() : null)
                .position(toGeoPoint(msg.getData()))
                .eventTime(parseTimestamp(msg.getTimestamp()))
                .description(buildImageDescription(msg, hasHdfsFile, hdfsPath))
                .build();
        indexSafely(doc);
    }

    /**
     * 索引 THERMAL 事件。
     */
    public void indexThermal(KafkaMessage msg) {
        if (msg.getData() == null) return;
        Device device = deviceRepository.findById(msg.getDeviceId()).orElse(null);
        Double temperature = asDouble(msg.getData().get("temperature"));
        // THERMAL 消息 data 中通常不含 threshold; 此处如需展示可在阶段 4 由 AlarmService 反向回填
        PatrolEventDocument doc = PatrolEventDocument.builder()
                .eventId(idGenerator.nextEventId())
                .deviceId(msg.getDeviceId())
                .deviceType(msg.getDeviceType())
                .deviceName(device != null ? device.getName() : null)
                .eventType(BusinessConstants.EVENT_TYPE_THERMAL)
                .taskId(asString(msg.getData().get("taskId")).orElse(null))
                .area(device != null ? device.getArea() : null)
                .temperature(temperature)
                .position(toGeoPoint(msg.getData()))
                .eventTime(parseTimestamp(msg.getTimestamp()))
                .description(String.format("%s 测点温度 %.1f℃",
                        asString(msg.getData().get("targetDevice")).orElse("目标设备"),
                        temperature != null ? temperature : 0.0))
                .build();
        indexSafely(doc);
    }

    /**
     * 索引 SENSOR 事件。
     */
    public void indexSensor(KafkaMessage msg) {
        if (msg.getData() == null) return;
        Device device = deviceRepository.findById(msg.getDeviceId()).orElse(null);
        PatrolEventDocument doc = PatrolEventDocument.builder()
                .eventId(idGenerator.nextEventId())
                .deviceId(msg.getDeviceId())
                .deviceType(msg.getDeviceType())
                .deviceName(device != null ? device.getName() : null)
                .eventType(BusinessConstants.EVENT_TYPE_SENSOR)
                .area(device != null ? device.getArea() : null)
                .position(toGeoPoint(msg.getData()))
                .eventTime(parseTimestamp(msg.getTimestamp()))
                .description(String.format("环境传感器: 温度 %s℃, 湿度 %s%%, 气体 %s",
                        msg.getData().getOrDefault("temperature", "?"),
                        msg.getData().getOrDefault("humidity", "?"),
                        msg.getData().getOrDefault("gas", "?")))
                .build();
        indexSafely(doc);
    }

    /**
     * 安全写入: 索引不存在则自动创建; 写入异常不影响主流程(Mongo 已落库)。
     */
    private void indexSafely(PatrolEventDocument doc) {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(PatrolEventDocument.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping(indexOps.createMapping(PatrolEventDocument.class));
                log.info("ES index created: {}", indexOps.getIndexCoordinates().getIndexName());
            }
            IndexQuery query = new IndexQueryBuilder().withId(doc.getEventId()).withObject(doc).build();
            elasticsearchOperations.index(query, indexOps.getIndexCoordinates());
        } catch (Exception e) {
            log.error("Failed to index event to ES: eventId={}, type={}",
                    doc.getEventId(), doc.getEventType(), e);
        }
    }

    private GeoPoint toGeoPoint(java.util.Map<String, Object> data) {
        Double lng = asDouble(data.get("lng"));
        Double lat = asDouble(data.get("lat"));
        if (lng == null || lat == null) return null;
        // 接口文档: 对外 {lng, lat} → ES geo_point {lat, lon}
        return new GeoPoint(lat, lng);
    }

    private String parseTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) return OffsetDateTime.now().toString();
        try {
            return OffsetDateTime.parse(iso).toString();
        } catch (Exception e) {
            return OffsetDateTime.now().toString();
        }
    }

    private Optional<String> asString(Object v) {
        if (v == null) return Optional.empty();
        return Optional.of(String.valueOf(v));
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

    private String buildImageDescription(KafkaMessage msg, boolean hasHdfsFile, String hdfsPath) {
        String fileName = asString(msg.getData().get("fileName")).orElse("未知");
        String camera = asString(msg.getData().get("cameraType")).orElse("未知");
        return String.format("航拍图像: %s, 相机: %s, 已归档: %s, 路径: %s",
                fileName, camera, hasHdfsFile ? "是" : "否", hasHdfsFile ? hdfsPath : "-");
    }
}
