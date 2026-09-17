package com.patrol.platform.service;

import com.patrol.platform.common.BusinessException;
import com.patrol.platform.common.ErrorCode;
import com.patrol.platform.entity.Alarm;
import com.patrol.platform.entity.Device;
import com.patrol.platform.repository.AlarmRepository;
import com.patrol.platform.repository.DeviceRepository;
import com.patrol.platform.repository.HdfsFileRepository;
import com.patrol.platform.repository.PatrolTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告生成 Service(接口文档 §2.6.1)。
 * <p>
 * 数据来源: MongoDB 聚合为主, ES 作为时间趋势来源; 数据时效性以 MongoDB 为权威。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DeviceRepository deviceRepository;
    private final AlarmRepository alarmRepository;
    private final PatrolTaskRepository patrolTaskRepository;
    private final HdfsFileRepository hdfsFileRepository;
    private final PatrolEventSearchService eventSearchService;

    /**
     * 生成巡检报告(POST /api/reports)。
     * <p>
     * 校验: from/to 必须 ISO 8601 解析成功, to > from, 跨度 ≤ 30 天。
     * statistics 各字段含义与接口文档 §2.6.1 一致。
     */
    public Map<String, Object> generate(String fromIso, String toIso, String title) {
        OffsetDateTime fromTs, toTs;
        try {
            fromTs = OffsetDateTime.parse(fromIso);
            toTs = OffsetDateTime.parse(toIso);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "时间格式非法, 需 ISO 8601");
        }
        if (!toTs.isAfter(fromTs)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "to 必须大于 from");
        }
        long spanSeconds = toTs.toEpochSecond() - fromTs.toEpochSecond();
        if (spanSeconds > 30L * 86400) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "时间跨度不能超过 30 天");
        }

        // 1) statistics 聚合
        Map<String, Object> statistics = buildStatistics(fromTs, toTs);

        // 2) highlights(关键告警摘要)
        List<String> highlights = buildHighlights(fromTs, toTs);

        // 3) 报告对象
        Map<String, Object> report = new HashMap<>();
        report.put("reportId", "R-" + System.currentTimeMillis());
        report.put("title", title != null ? title
                : String.format("%s ~ %s 巡检报告",
                    fromTs.toLocalDate(), toTs.toLocalDate()));
        report.put("from", fromIso);
        report.put("to", toIso);
        report.put("generatedAt", OffsetDateTime.now().toString());
        report.put("statistics", statistics);
        report.put("highlights", highlights);
        return report;
    }

    /**
     * statistics 各字段: 与接口文档 §2.6.1 完全一致。
     */
    private Map<String, Object> buildStatistics(OffsetDateTime fromTs, OffsetDateTime toTs) {
        Map<String, Object> s = new HashMap<>();
        long deviceTotal = deviceRepository.count();
        long onlineDeviceTotal = deviceRepository.countByStatus(com.patrol.platform.common.BusinessConstants.DEVICE_STATUS_ONLINE);
        long offlineCount = deviceRepository.countByStatus(com.patrol.platform.common.BusinessConstants.DEVICE_STATUS_OFFLINE);
        long taskTotal = patrolTaskRepository.countByCreateTimeBetween(fromTs, toTs);
        long alarmTotal = alarmRepository.countByCreateTimeBetween(fromTs, toTs);
        // 事件数从 ES 取(权威统计源); 文件数从 MongoDB 取
        long eventTotal = 0L;
        try {
            Map<String, Object> esStats = eventSearchService.aggregateStats(
                    fromTs.toString(), toTs.toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> totals = (Map<String, Object>) esStats.getOrDefault("totals", new HashMap<>());
            Object e = totals.get("events");
            if (e instanceof Number n) eventTotal = n.longValue();
        } catch (Exception ignore) {
            // ES 失败时事件数置 0, 不阻塞报告生成
        }
        long fileTotal = hdfsFileRepository.countByUploadTimeBetween(fromTs, toTs);

        s.put("deviceTotal", deviceTotal);
        s.put("onlineDeviceTotal", onlineDeviceTotal);
        s.put("offlineCount", offlineCount);
        s.put("taskTotal", taskTotal);
        s.put("alarmTotal", alarmTotal);
        s.put("eventTotal", eventTotal);
        s.put("fileTotal", fileTotal);
        s.put("alarmTypeDist", alarmTypeDistribution(fromTs, toTs));
        s.put("deviceRank", deviceRank(fromTs, toTs));
        return s;
    }

    /**
     * 按类型分布统计告警。
     */
    private List<Map<String, Object>> alarmTypeDistribution(OffsetDateTime fromTs, OffsetDateTime toTs) {
        List<Alarm> alarms = alarmRepository.findByCreateTimeBetween(fromTs, toTs);
        Map<String, Long> typeCount = new HashMap<>();
        for (Alarm a : alarms) {
            typeCount.merge(a.getAlarmType(), 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        typeCount.forEach((k, v) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("key", k);
            item.put("count", v);
            result.add(item);
        });
        return result;
    }

    /**
     * 设备排行: 按 alarm 数 / event 数排序, top 10。
     * <p>
     * 简化: 用 alarm 数降序, 任务数作为辅助。
     */
    private List<Map<String, Object>> deviceRank(OffsetDateTime fromTs, OffsetDateTime toTs) {
        List<Alarm> alarms = alarmRepository.findByCreateTimeBetween(fromTs, toTs);
        Map<String, Long> deviceAlarm = new HashMap<>();
        for (Alarm a : alarms) {
            if (a.getDeviceId() != null) {
                deviceAlarm.merge(a.getDeviceId(), 1L, Long::sum);
            }
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(deviceAlarm.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("deviceId", sorted.get(i).getKey());
            item.put("count", sorted.get(i).getValue());
            result.add(item);
        }
        return result;
    }

    /**
     * highlights: 严重告警摘要(CRITICAL 等级, 时间区间内)。
     */
    private List<String> buildHighlights(OffsetDateTime fromTs, OffsetDateTime toTs) {
        List<Alarm> critical = alarmRepository.findByCreateTimeBetween(fromTs, toTs);
        List<String> lines = new ArrayList<>();
        int maxHighlights = 10;
        int count = 0;
        for (Alarm a : critical) {
            if (!com.patrol.platform.common.BusinessConstants.ALARM_LEVEL_CRITICAL.equals(a.getLevel())) continue;
            if (a.getAlarmType() != null && a.getAlarmType().contains("TEMP_OVER") && a.getValue() != null) {
                Device device = a.getDeviceId() == null ? null : deviceRepository.findById(a.getDeviceId()).orElse(null);
                lines.add(String.format("%s 热点温度%.1f℃,超过严重阈值%.0f℃,已%s",
                        device != null ? device.getName() : a.getDeviceId(),
                        a.getValue(),
                        a.getThreshold() != null ? a.getThreshold() : 0,
                        com.patrol.platform.common.BusinessConstants.ALARM_STATUS_PROCESSED.equals(a.getStatus())
                                ? "处置" : "未处置"));
            } else if (com.patrol.platform.common.BusinessConstants.ALARM_TYPE_OFFLINE.equals(a.getAlarmType())) {
                String when = a.getCreateTime() != null
                        ? a.getCreateTime().toLocalDateTime().toString().substring(0, 16).replace('T', ' ')
                        : "?";
                lines.add(String.format("%s 于 %s 离线", a.getDeviceId(), when));
            } else {
                lines.add(a.getDescription() != null ? a.getDescription() : "严重告警");
            }
            if (++count >= maxHighlights) break;
        }
        return lines;
    }
}
