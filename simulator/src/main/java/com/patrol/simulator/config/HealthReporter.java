package com.patrol.simulator.config;

import com.patrol.simulator.device.DeviceSim;
import com.patrol.simulator.messaging.SimulatorConsumer;
import com.patrol.simulator.task.TaskDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仿真器侧健康摘要: 周期性打印设备状态、消息量、任务在飞数。
 * <p>
 * 默认 30s 一次,便于演示与排错(终端可直观看到心跳/上报是否正常)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthReporter {

    private final List<DeviceSim> simulatedDevices;
    private final TaskDispatcher taskDispatcher;
    private final SimulatorConsumer consumer;

    /** 每 30s 打印一次状态摘要 */
    @Scheduled(fixedDelay = 30000L, initialDelay = 15000L)
    public void report() {
        int online = 0, faulty = 0, busy = 0, lowBattery = 0;
        for (DeviceSim d : simulatedDevices) {
            var ctx = d.getCtx();
            if (ctx.getBattery() > 0) online++;
            if (!ctx.getFaultCode().isEmpty()) faulty++;
            if ("TASK".equals(ctx.getMode())) busy++;
            if (ctx.getBattery() < 20 && ctx.getBattery() >= 0) lowBattery++;
        }
        log.info("[HEALTH] devices={} online={} faulty={} busy={} lowBat={} cmdQueue={} runningTasks={}",
                simulatedDevices.size(), online, faulty, busy, lowBattery,
                consumer.queueSize(),
                taskDispatcher.runningTaskCount());
    }
}
