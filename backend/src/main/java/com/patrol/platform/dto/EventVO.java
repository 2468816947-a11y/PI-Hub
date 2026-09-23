package com.patrol.platform.dto;

/**
 * 巡检事件响应对象(接口文档 §2.5.1)。
 * <p>
 * 与 ES 文档 PatrolEventDocument 的唯一差异: position 对外为 {lng, lat}
 * (接口文档 §1.5 经纬度约定), ES 内部 geo_point 为 {lat, lon}, 由
 * {@link com.patrol.platform.service.PatrolEventSearchService} 在响应映射时转换。
 */
public record EventVO(
        String eventId,
        String deviceId,
        String deviceType,
        String deviceName,
        String eventType,
        String alarmType,
        String alarmLevel,
        String taskId,
        String area,
        Double temperature,
        Double threshold,
        Position position,
        String eventTime,
        String description) {

    /**
     * 对外坐标: {lng, lat}(经度在前, 与前端地图/接口文档一致)。
     */
    public record Position(Double lng, Double lat) {
    }
}
