package com.patrol.platform.scheduler;

import com.patrol.platform.common.BusinessConstants;
import com.patrol.platform.entity.Device;
import com.patrol.platform.repository.DeviceRepository;
import com.patrol.platform.service.AlarmService;
import com.patrol.platform.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 设备离线定时检测器(接口文档 §2.2.5 + backend.mdc §6)。
 * <p>
 * 规则: 周期 5s 扫描, lastHeartbeat 超 15s(3 个心跳周期)未更新的设备置 OFFLINE 并生成离线告警。
 * <p>
 * 实现要点:
 * <ul>
 *   <li>仅扫描 ONLINE 设备: 已经是 OFFLINE 的不重复告警</li>
 *   <li>使用 Mongo 简单查询(@Indexed 字段已在实体上声明); 数据规模 ≤ 10 台设备可全表扫描</li>
 *   <li>每台离线设备仅生成一次告警(状态置 OFFLINE 后下次扫描跳过)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOfflineScheduler {

    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final AlarmService alarmService;

    /**
     * 定时任务: 每 5 秒执行一次(架构 §5 离线检测)。
     */
    @Scheduled(fixedDelay = BusinessConstants.HEARTBEAT_SCAN_INTERVAL_MS,
               initialDelay = BusinessConstants.HEARTBEAT_SCAN_INTERVAL_MS)
    public void scanOfflineDevices() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minusSeconds(BusinessConstants.HEARTBEAT_OFFLINE_THRESHOLD_SECONDS);

        // 仅扫描 ONLINE 设备 + 有过心跳的(无心跳的设备从未在线, 不视为离线)
        // 仿真规模 ≤ 10 台, 使用全表 findByStatus 避免 Pageable 装配复杂度
        List<Device> candidates = deviceRepository.findByStatus(BusinessConstants.DEVICE_STATUS_ONLINE);

        int offlineCount = 0;
        for (Device device : candidates) {
            if (device.getLastHeartbeat() != null && device.getLastHeartbeat().isBefore(threshold)) {
                log.info("Device offline detected: deviceId={}, lastHeartbeat={}, now={}",
                        device.getDeviceId(), device.getLastHeartbeat(), now);
                deviceService.markOffline(device.getDeviceId());
                alarmService.createOfflineAlarm(device);
                offlineCount++;
            }
        }
        if (offlineCount > 0) {
            log.info("Offline scan completed: scanned={}, offlineNew={}", candidates.size(), offlineCount);
        }
    }
}
