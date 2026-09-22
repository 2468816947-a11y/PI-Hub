package com.patrol.simulator.device;

import com.patrol.simulator.config.SimulatorProperties;
import com.patrol.simulator.messaging.EventBus;
import com.patrol.simulator.util.RandomData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 设备仿真抽象基类。
 * <p>
 * 每台设备独占一个单线程 ScheduledExecutor(架构 §9: 仿真器进程×N 模拟多设备)——
 * 单线程避免共享状态同步,故障隔离也更好(一台设备异常不影响其他)。
 * <p>
 * 调度节奏:
 * <ul>
 *   <li>{@link #heartbeatIntervalMs} —— 心跳(必修)</li>
 *   <li>子类定义的"空闲巡航"周期(无人机图像 / 机器狗环境传感器)</li>
 *   <li>故障注入: 每心跳周期以 {@code faultInjectionRate} 概率随机注入故障</li>
 * </ul>
 * 任务执行: 由独立的 TaskDispatcher 通过 {@link #startTask} 触发,执行期间
 * 设备进入 {@code MODE_TASK},空闲上报暂停。
 * <p>
 * 故障码约定:
 * <ul>
 *   <li>E001 —— 电机异常</li>
 *   <li>E002 —— 传感器失准</li>
 *   <li>E003 —— 通信链路抖动</li>
 *   <li>E004 —— 存储模块异常</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class DeviceSim {

    /** 故障码池(便于触发不同告警展示) */
    private static final String[] FAULT_CODES = {"E001", "E002", "E003", "E004"};

    @Getter
    protected final DeviceContext ctx;

    protected final EventBus eventBus;
    protected final SimulatorProperties properties;

    /** 单设备独立调度线程(不在初始化器中引用 ctx,改在 start() 中创建) */
    private ScheduledExecutorService scheduler;

    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> patrolTask;
    private volatile boolean started;

    /* ============================================================
     * 生命周期
     * ============================================================ */

    /** 启动设备: 注册 + 心跳 + 空闲巡航 */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;

        // 单设备调度线程:在此处创建以保证 ctx 已注入
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-" + ctx.getDeviceId());
            t.setDaemon(true);
            return t;
        });

        // 1) 注册: 一上线立刻发
        register();

        // 2) 心跳: 固定周期(架构 §5: 5s)
        long hbMs = properties.getSimulator().getHeartbeatIntervalMs();
        heartbeatTask = scheduler.scheduleAtFixedRate(this::tickHeartbeat, hbMs, hbMs, TimeUnit.MILLISECONDS);

        // 3) 空闲巡航: 由子类调度自身遥测
        patrolTask = scheduler.scheduleAtFixedRate(this::tickPatrol,
                idlePatrolIntervalMs(), idlePatrolIntervalMs(), TimeUnit.MILLISECONDS);

        log.info("[BOOT] device={} type={} area={} mode={}",
                ctx.getDeviceId(), ctx.getDeviceType(), ctx.getArea(), ctx.getMode());
    }

    /** 停止设备(测试/优雅关闭) */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (patrolTask != null) patrolTask.cancel(false);
        scheduler.shutdownNow();
        log.info("[STOP] device={}", ctx.getDeviceId());
    }

    /* ============================================================
     * 模板方法: 子类实现差异化行为
     * ============================================================ */

    /** 空闲巡航上报周期(毫秒) */
    protected abstract long idlePatrolIntervalMs();

    /** 空闲时上报遥测数据(无人机图像 / 机器狗传感器) */
    protected abstract void onIdlePatrol();

    /** 执行任务(由 TaskDispatcher 调用,异步,执行期间应阻塞) */
    public abstract void executeTask(String taskId, String taskType, String area, java.util.Map<String, Object> params);

    /* ============================================================
     * 公共调度片段
     * ============================================================ */

    /** 单条消息: 发送心跳 */
    private void tickHeartbeat() {
        try {
            ctx.markHeartbeat();
            // 按经过时长扣电
            ctx.drainBattery(properties.getSimulator().getBatteryDrainPerHour(),
                    properties.getSimulator().getHeartbeatIntervalMs());

            // 故障注入(仅非任务态)
            if (!DeviceContext.MODE_TASK.equals(ctx.getMode())
                    && RandomData.chance(properties.getSimulator().getFaultInjectionRate())) {
                ctx.setFaultCode(RandomData.pick(FAULT_CODES));
                // 故障立即报一次状态
                sendStatus(true);
            }
            // 故障解除(任务态不自动解除,便于演示"故障中执行")
            if (!DeviceContext.MODE_TASK.equals(ctx.getMode())
                    && !ctx.getFaultCode().isEmpty()
                    && RandomData.chance(0.3)) {
                log.info("[RECOVERY] device={} 解除故障 {}", ctx.getDeviceId(), ctx.getFaultCode());
                ctx.setFaultCode("");
                sendStatus(true);
            }

            eventBus.sendHeartbeat(ctx.getDeviceId(), ctx.getDeviceType(), ctx.getBattery());
        } catch (Throwable t) {
            log.error("Heartbeat tick failed: device={}", ctx.getDeviceId(), t);
        }
    }

    /** 单条消息: 空闲巡航(子类实现 onIdlePatrol) */
    private void tickPatrol() {
        if (DeviceContext.MODE_TASK.equals(ctx.getMode())) {
            // 任务执行期间不进行空闲上报
            return;
        }
        try {
            ctx.setMode(DeviceContext.MODE_PATROL);
            onIdlePatrol();
        } catch (Throwable t) {
            log.error("Idle patrol failed: device={}", ctx.getDeviceId(), t);
        }
    }

    /* ============================================================
     * 公共发送: 注册/状态
     * ============================================================ */

    /** 设备注册(§A.2 REGISTER 载荷) */
    public void register() {
        if (ctx.isRegistered()) {
            return;
        }
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", ctx.getName());
        data.put("model", ctx.getModel());
        data.put("area", ctx.getArea());
        data.put("lng", ctx.getLng());
        data.put("lat", ctx.getLat());
        eventBus.sendRegister(ctx.getDeviceId(), ctx.getDeviceType(), data);
        ctx.setRegistered(true);
    }

    /** 主动上报状态变化(故障/电量变化时调用) */
    public void sendStatus(boolean online) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("online", online);
        data.put("battery", ctx.getBattery());
        data.put("faultCode", ctx.getFaultCode());
        data.put("mode", ctx.getMode());
        eventBus.sendStatus(ctx.getDeviceId(), ctx.getDeviceType(),
                online, ctx.getBattery(), ctx.getFaultCode(), ctx.getMode());
    }
}
