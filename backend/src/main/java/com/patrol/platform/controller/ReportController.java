package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import com.patrol.platform.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报告服务(接口文档 §2.6 共 1 个接口)。
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 生成巡检报告(POST /api/reports)。
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> generate(@Valid @RequestBody ReportRequest req) {
        return ApiResponse.ok(reportService.generate(req.from(), req.to(), req.title()));
    }

    public record ReportRequest(
            @NotBlank(message = "from 不能为空") String from,
            @NotBlank(message = "to 不能为空") String to,
            String title
    ) {}
}
