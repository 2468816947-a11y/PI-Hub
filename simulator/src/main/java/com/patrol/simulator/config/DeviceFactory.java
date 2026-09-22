package com.patrol.simulator.config;

import com.patrol.simulator.device.DeviceContext;
import com.patrol.simulator.device.DeviceSim;
import com.patrol.simulator.device.DroneSimulator;
import com.patrol.simulator.device.RobotDogSimulator;
import com.patrol.simulator.messaging.EventBus;
import com.patrol.simulator.util.Ids;
import com.patrol.simulator.util.RandomData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 仿真器设备工厂: 根据配置创建无人机/机器狗列表,启动后保持运行。
 * <p>
 * 每台设备拥有独立线程(参见 {@link DeviceSim}),便于:
 * <ul>
 *   <li>故障隔离: 单台异常不影响其他</li>
 *   <li>独立调度: 心跳/任务执行节奏互不干扰</li>
 * </ul>
 * <p>
 * 设备列表通过 {@link #simulatedDevices()} Bean 暴露,供 {@link com.patrol.simulator.task.TaskDispatcher} 注入。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DeviceFactory {

    private final EventBus eventBus;
    private final SimulatorProperties properties;

    /** 所有仿真设备(可变,启动后填充;Bean 暴露为不可变视图) */
    private final List<DeviceSim> devices = new ArrayList<>();

    /**
     * 暴露设备列表为 Bean(TaskDispatcher 按 List<DeviceSim> 注入)。
     * 返回不可变视图,防止外部修改。
     */
    @Bean
    public List<DeviceSim> simulatedDevices() {
        return Collections.unmodifiableList(devices);
    }

    @PostConstruct
    public void bootDevices() {
        SimulatorProperties.DevicePool pool = properties.getDevicePool();
        SimulatorProperties.Center center = pool.getCenter();

        // 1) 无人机
        for (int i = 1; i <= properties.getSimulator().getDroneCount(); i++) {
            String id = Ids.droneId(i);
            String area = pool.getAreas().isEmpty() ? "默认区域" : RandomData.pick(pool.getAreas());
            String model = pool.getDroneModels().isEmpty() ? "UAV-Default" : RandomData.pick(pool.getDroneModels());
            double lng = jitter(center.getLng(), pool.getPositionJitter());
            double lat = jitter(center.getLat(), pool.getPositionJitter());

            DeviceContext ctx = new DeviceContext(id, "UAV",
                    String.format("无人机%d号", i), model, area, lng, lat);
            DroneSimulator d = new DroneSimulator(ctx, eventBus, properties);
            d.start();
            devices.add(d);
        }

        // 2) 机器狗
        for (int i = 1; i <= properties.getSimulator().getRobotDogCount(); i++) {
            String id = Ids.robotDogId(i);
            String area = pool.getAreas().isEmpty() ? "默认区域" : RandomData.pick(pool.getAreas());
            String model = pool.getDogModels().isEmpty() ? "DOG-Default" : RandomData.pick(pool.getDogModels());
            double lng = jitter(center.getLng(), pool.getPositionJitter());
            double lat = jitter(center.getLat(), pool.getPositionJitter());

            DeviceContext ctx = new DeviceContext(id, "ROBOT_DOG",
                    String.format("机器狗%d号", i), model, area, lng, lat);
            RobotDogSimulator d = new RobotDogSimulator(ctx, eventBus, properties);
            d.start();
            devices.add(d);
        }

        log.info("[FACTORY] booted {} drones + {} dogs = {} devices",
                properties.getSimulator().getDroneCount(),
                properties.getSimulator().getRobotDogCount(),
                devices.size());
    }

    @PreDestroy
    public void shutdownDevices() {
        log.info("[FACTORY] shutting down {} devices...", devices.size());
        for (DeviceSim d : devices) {
            try {
                d.stop();
            } catch (Exception e) {
                log.warn("Stop device {} failed", d.getCtx().getDeviceId(), e);
            }
        }
    }

    private static double jitter(double center, double jitter) {
        return RandomData.jitter(center, jitter);
    }
}
