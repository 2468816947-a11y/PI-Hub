package com.patrol.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仿真器总配置(对应 application.yml 中 simulator.* / device-pool.* 两个根)。
 * <p>
 * 拆分两个嵌套类便于阅读:
 * <ul>
 *   <li>{@link Simulator} —— 设备规模/节奏/故障注入</li>
 *   <li>{@link DevicePool} —— 设备命名/区域/位置</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "")
public class SimulatorProperties {

    @NestedConfigurationProperty
    private Simulator simulator = new Simulator();

    @NestedConfigurationProperty
    private DevicePool devicePool = new DevicePool();

    @Data
    public static class Simulator {
        /** 无人机数量 */
        private int droneCount = 6;
        /** 机器狗数量 */
        private int robotDogCount = 4;
        /** 心跳周期(毫秒) */
        private long heartbeatIntervalMs = 5000L;
        /** 周期状态上报间隔(毫秒) */
        private long statusIntervalMs = 30000L;
        /** 无人机空闲巡航图像上报间隔(毫秒) */
        private long droneImageIntervalMs = 8000L;
        /** 机器狗空闲环境传感器上报间隔(毫秒) */
        private long dogSensorIntervalMs = 10000L;
        /** THERMAL 任务相邻测点间隔(毫秒) */
        private long thermalPointIntervalMs = 1500L;
        /** IMAGE 任务相邻图像间隔(毫秒) */
        private long imageIntervalMs = 2000L;
        /** 每心跳周期注入故障的概率(0~1) */
        private double faultInjectionRate = 0.02;
        /** THERMAL 测点超阈值概率(0~1) */
        private double tempOverRate = 0.10;
        /** 电量消耗速率(%/小时) */
        private double batteryDrainPerHour = 2.0;
        /** IMAGE 消息是否附带 imageBase64(便于演示 HDFS 写入) */
        private boolean imageBase64Enabled = true;
        /** Kafka Topic 名称(可被环境变量覆盖) */
        @NestedConfigurationProperty
        private Topics topics = new Topics();
    }

    @Data
    public static class Topics {
        private String register = "patrol.device.register";
        private String heartbeat = "patrol.device.heartbeat";
        private String status = "patrol.device.status";
        private String droneImage = "patrol.drone.image";
        private String dogThermal = "patrol.dog.thermal";
        private String dogSensor = "patrol.dog.sensor";
        private String taskResult = "patrol.task.result";
        private String taskCommand = "patrol.task.command";
    }

    @Data
    public static class DevicePool {
        /** 可选区域列表 */
        private List<String> areas = List.of("1号变电站·东区", "1号变电站·西区");
        private List<String> droneModels = List.of("巡检无人机-M300");
        private List<String> dogModels = List.of("四足机器狗-宇树Go2");
        /** 场站中心点 */
        private Center center = new Center();
        /** 设备坐标随机偏移量(度) */
        private double positionJitter = 0.005;
    }

    @Data
    public static class Center {
        private double lng = 116.397;
        private double lat = 39.908;
    }
}
