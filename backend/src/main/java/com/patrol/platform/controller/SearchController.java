package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import com.patrol.platform.dto.EventVO;
import com.patrol.platform.dto.PageResponse;
import com.patrol.platform.dto.SearchDto.EventSearchRequest;
import com.patrol.platform.repository.AlarmRepository;
import com.patrol.platform.repository.DeviceRepository;
import com.patrol.platform.service.PatrolEventSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 检索分析(接口文档 §2.5 共 2 个接口)。
 * <p>
 * 实现:
 * <ul>
 *   <li>POST /api/search/events: bool query 组合检索, 分页按 eventTime desc</li>
 *   <li>GET /api/search/stats: totals + alarmTypeDist + deviceRank + timeTrend + areaDist</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final PatrolEventSearchService searchService;
    private final DeviceRepository deviceRepository;
    private final AlarmRepository alarmRepository;

    /**
     * 巡检事件检索(POST /api/search/events)。
     * <p>
     * 支持 deviceId/eventTypes/alarmType/alarmLevel/area/from/to/keyword/geo/bbox 任意组合(geo 与 bbox 互斥)。
     */
    @PostMapping("/events")
    public ApiResponse<PageResponse<EventVO>> searchEvents(@RequestBody EventSearchRequest req) {
        Page<EventVO> result = searchService.searchEvents(req);
        return ApiResponse.ok(new PageResponse<>(
                result.getTotalElements(),
                result.getNumber() + 1,
                result.getSize(),
                result.getContent()));
    }

    /**
     * 聚合统计(GET /api/search/stats)。
     * <p>
     * 返回 totals + alarmTypeDist + deviceRank + timeTrend + areaDist, 数据来源 ES + MongoDB。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Map<String, Object> data = searchService.aggregateStats(from, to);

        // 补全 totals 中的 devices / alarms(从 MongoDB)
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) data.getOrDefault("totals", new HashMap<>());
        totals.put("devices", deviceRepository.count());
        totals.put("alarms", alarmRepository.count());
        data.put("totals", totals);
        return ApiResponse.ok(data);
    }
}
