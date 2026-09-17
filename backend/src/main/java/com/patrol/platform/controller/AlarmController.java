package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import com.patrol.platform.dto.AlarmDto.AlarmProcessRequest;
import com.patrol.platform.dto.AlarmDto.AlarmVO;
import com.patrol.platform.dto.PageResponse;
import com.patrol.platform.entity.Alarm;
import com.patrol.platform.service.AlarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * 告警管理(接口文档 §2.4 共 3 个接口)。
 */
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    /** 告警分页筛选(GET /api/alarms) */
    @GetMapping
    public ApiResponse<PageResponse<AlarmVO>> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(size, 1), 100));
        Page<Alarm> result = alarmService.listAlarms(level, type, status, pageable);
        return ApiResponse.ok(PageResponse.from(result.map(AlarmVO::from)));
    }

    /** 告警详情(GET /api/alarms/{id}) */
    @GetMapping("/{id}")
    public ApiResponse<AlarmVO> get(@PathVariable("id") String alarmId) {
        return ApiResponse.ok(AlarmVO.from(alarmService.getAlarmOrThrow(alarmId)));
    }

    /** 告警处置(PUT /api/alarms/{id}/process) */
    @PutMapping("/{id}/process")
    public ApiResponse<AlarmVO> process(@PathVariable("id") String alarmId,
                                          @Valid @RequestBody AlarmProcessRequest req) {
        return ApiResponse.ok(AlarmVO.from(alarmService.processAlarm(alarmId, req.processNote())));
    }
}
