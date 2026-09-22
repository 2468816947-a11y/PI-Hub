package com.patrol.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 位置坐标 DTO。
 * 对外统一使用 {lng, lat} 格式。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PositionDto(
        Double lng,
        Double lat
) {}
