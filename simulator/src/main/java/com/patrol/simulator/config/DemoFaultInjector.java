package com.patrol.simulator.config;

import com.patrol.simulator.device.DeviceSim;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 演示用故障注入器: 每隔一段时间随机挑一台设备注入故障/低电量,
 * 便于演示告警(后端规则引擎据此写 Mongo + ES + Kibana)。
 * <p>
 * 默认 90s 一次,演示时建议调小间隔。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoFaultInjector {

    private final List<DeviceSim> simulatedDevices;

    /** 注入故障码池(同 DeviceSim.FAULT_CODES, 这里复述便于演示日志语义) */
    private static final String[] FAULTS = {"E001", "E002", "E003", "E004"};

    /** 每 90s 演示一次:50% 注入故障, 30% 模拟电量低, 20% 恢复 */
    @Scheduled(fixedDelay = 90000L, initialDelay = 60000L)
    public void maybeInject() {
        if (simulatedDevices.isEmpty()) {
            return;
        }
        DeviceSim target = simulatedDevices.get(ThreadLocalRandom.current().nextInt(simulatedDevices.size()));
        var ctx = target.getCtx();
        double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.5) {
            String fault = FAULTS[ThreadLocalRandom.current().nextInt(FAULTS.length)];
            ctx.setFaultCode(fault);
            log.info("[DEMO] inject fault {} to device {}", fault, ctx.getDeviceId());
        } else if (r < 0.8) {
            // 强制设置电量为 15%(低于低电量阈值)
            ctx.forceBattery(ThreadLocalRandom.current().nextInt(3, 15));
            log.info("[DEMO] force low battery on device {} (now={})", ctx.getDeviceId(), ctx.getBattery());
        } else {
            // 恢复
            ctx.setFaultCode("");
            log.info("[DEMO] clear fault on device {}", ctx.getDeviceId());
        }
        // 主动上报一次状态让后端立刻看到变化
        target.sendStatus(true);
    }
}
