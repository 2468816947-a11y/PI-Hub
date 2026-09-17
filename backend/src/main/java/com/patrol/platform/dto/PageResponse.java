package com.patrol.platform.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 统一分页响应信封(接口文档 §1.2)。
 * data 字段固定为: {total, page, size, list}
 */
public record PageResponse<T>(long total, int page, int size, List<T> list) {

    /**
     * 从 Spring Data Page 构建响应。
     * 注意: Spring Data Page 的 page 从 0 开始, 前端期望 page 从 1 开始, 这里 +1 转换。
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.getContent()
        );
    }
}
