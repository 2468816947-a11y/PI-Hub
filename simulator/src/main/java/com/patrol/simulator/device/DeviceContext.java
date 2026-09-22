package com.patrol.simulator.device;

import lombok.Data;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备运行时状态(每台设备一份, 由 {@link DeviceSim} 持有)。
 * <p>
 * 集中维护所有"会变化"的字段: 电量、故障码、当前模式、最近心跳时间等;
 * 仿真器对自身的状态修改必须加锁(本类内置 {@link #lock} 或外部 synchronized),
 * 但目前所有写操作都在同一台设备的专用调度线程中,无并发。
 * <p>
 * 关键状态含义:
 * <ul>
 *   <li>{@code battery} —— 电量百分比(0~100)</li>
 *   <li>{@code faultCode} —— 故障码;空串表示无故障</li>
 *   <li>{@code mode} —— 当前模式: IDLE / PATROL / TASK / OFFLINE</li>
 *   <li>{@code currentTaskId} —— 若非空,该设备正在执行某任务</li>
 *   <li>{@code registered} —— 是否已发送过 REGISTER 消息</li>
 * </ul>
 */
@Data
public class DeviceContext {

    /** 模式常量 */
    public static final String MODE_IDLE = "IDLE";
    public static final String MODE_PATROL = "PATROL";
    public static final String MODE_TASK = "TASK";

    private final String deviceId;
    private final String deviceType;
    private final String name;
    private final String model;
    private final String area;
    private double lng;
    private double lat;

    private volatile int battery = 100;
    private volatile String faultCode = "";
    private volatile String mode = MODE_IDLE;
    private volatile String currentTaskId;
    private volatile boolean registered;

    /** 最近心跳时间(Instant, 用于健康监控, 不上送业务字段) */
    private final AtomicLong lastHeartbeatAtMs = new AtomicLong(Instant.now().toEpochMilli());

    public DeviceContext(String deviceId, String deviceType, String name, String model,
                         String area, double lng, double lat) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.name = name;
        this.model = model;
        this.area = area;
        this.lng = lng;
        this.lat = lat;
    }

    /** 记录心跳时间戳(由上报线程在每周期开始时调用) */
    public void markHeartbeat() {
        lastHeartbeatAtMs.set(Instant.now().toEpochMilli());
    }

    /** 距上次心跳的毫秒数(健康监控用) */
    public long millisSinceLastHeartbeat() {
        return Instant.now().toEpochMilli() - lastHeartbeatAtMs.get();
    }

    /**
     * 减少电量(根据调度周期折算为应扣减的百分比)。
     *
     * @param batteryDrainPerHour 配置项: 电量消耗速率(%/小时)
     * @param elapsedMs            自上次扣减以来的实际间隔
     */
    public void drainBattery(double batteryDrainPerHour, long elapsedMs) {
        double delta = batteryDrainPerHour * elapsedMs / 3_600_000.0;
        int next = (int) Math.max(0, Math.min(100, battery - delta));
        this.battery = next;
    }

    /**
     * 强制设置电量到指定值(0~100,用于演示用注入器/测试)。
     * 不受 drainBattery 累加路径影响,可瞬间置位。
     */
    public void forceBattery(int value) {
        this.battery = Math.max(0, Math.min(100, value));
    }
}
