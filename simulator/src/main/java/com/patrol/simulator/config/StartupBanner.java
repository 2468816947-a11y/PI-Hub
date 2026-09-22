package com.patrol.simulator.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动 Banner: 醒目地打印关键信息,便于演示/排错时一眼看到配置。
 * <p>
 * 包括: 应用名、Kafka 地址、设备规模、各 Topic。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class StartupBanner implements ApplicationRunner {

    private final SimulatorProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        SimulatorProperties.Simulator sim = properties.getSimulator();
        SimulatorProperties.Topics t = sim.getTopics();
        log.info("");
        log.info("=================================================================");
        log.info("  Patrol Platform - Device Simulator");
        log.info("-----------------------------------------------------------------");
        log.info("  Drones       : {}", sim.getDroneCount());
        log.info("  Robot Dogs   : {}", sim.getRobotDogCount());
        log.info("  Heartbeat    : {} ms", sim.getHeartbeatIntervalMs());
        log.info("  Fault Inject : {} per heartbeat", sim.getFaultInjectionRate());
        log.info("  Temp Over    : {} per thermal point", sim.getTempOverRate());
        log.info("  Image Base64 : {}", sim.isImageBase64Enabled() ? "enabled" : "disabled");
        log.info("-----------------------------------------------------------------");
        log.info("  Topics:");
        log.info("    register     = {}", t.getRegister());
        log.info("    heartbeat    = {}", t.getHeartbeat());
        log.info("    status       = {}", t.getStatus());
        log.info("    drone-image  = {}", t.getDroneImage());
        log.info("    dog-thermal  = {}", t.getDogThermal());
        log.info("    dog-sensor   = {}", t.getDogSensor());
        log.info("    task-result  = {}", t.getTaskResult());
        log.info("    task-command = {}", t.getTaskCommand());
        log.info("=================================================================");
        log.info("");
    }
}
