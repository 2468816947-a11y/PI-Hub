package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 健康检查(免鉴权): 容器 healthcheck 依赖; instance 字段用于验证 Nginx 轮询
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthInfo> health() {
        return ApiResponse.ok(new HealthInfo(
                "UP",
                System.getenv().getOrDefault("HOSTNAME", "unknown"),
                OffsetDateTime.now().toString()));
    }

    public record HealthInfo(String status, String instance, String time) {
    }
}
