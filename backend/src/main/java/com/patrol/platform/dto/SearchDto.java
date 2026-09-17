package com.patrol.platform.dto;

import java.util.List;

/**
 * 检索分析相关 DTO(接口文档 §2.5)。
 */
public final class SearchDto {

    private SearchDto() {}

    /**
     * 巡检事件检索请求体(接口文档 §2.5.1)。
     */
    public record EventSearchRequest(
            String deviceId,
            List<String> eventTypes,
            String alarmType,
            String alarmLevel,
            String area,
            String from,
            String to,
            String keyword,
            Geo geo,
            Bbox bbox,
            Integer page,
            Integer size
    ) {}

    /** 圆形地理范围(与 bbox 二选一) */
    public record Geo(GeoPoint center, Double radiusKm) {}

    /** 矩形地理范围(与 geo 二选一) */
    public record Bbox(GeoPoint topLeft, GeoPoint bottomRight) {}

    /** 经纬度(对外统一 {lng, lat}) */
    public record GeoPoint(Double lng, Double lat) {}
}
