package com.patrol.simulator.device;

import com.patrol.simulator.config.SimulatorProperties;
import com.patrol.simulator.messaging.EventBus;
import com.patrol.simulator.messaging.MsgType;
import com.patrol.simulator.util.Ids;
import com.patrol.simulator.util.RandomData;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 机器狗仿真器。
 * <p>
 * 行为:
 * <ul>
 *   <li>空闲巡航: 按 {@code dogSensorIntervalMs} 节奏上报环境传感器(SENSOR)</li>
 *   <li>支持任务:
 *     <ul>
 *       <li>THERMAL —— 红外测温,逐点上报 THERMAL(每点间隔 {@code thermalPointIntervalMs})</li>
 *       <li>SENSOR / COMPREHENSIVE —— 连续上报环境传感器</li>
 *     </ul>
 *   </li>
 *   <li>测温超阈值(由 {@code params.tempThreshold} 决定): 按 {@code tempOverRate} 概率触发,
 *       模拟产生告警所需的数据(后端规则引擎据此写 ES)</li>
 * </ul>
 * <p>
 * THERMAL 数据载荷字段见接口文档 §A.2 THERMAL。
 */
@Slf4j
public class RobotDogSimulator extends DeviceSim {

    /** 任务默认测点数 / 超温阈值 */
    private static final int DEFAULT_THERMAL_POINTS = 12;
    private static final double DEFAULT_THERMAL_THRESHOLD = 80.0;

    /** 测温采集时的环境温度基准(°C) */
    private static final double AMBIENT_BASE = 28.0;
    /** 正常设备温度基准(°C) */
    private static final double NORMAL_TEMP_BASE = 45.0;
    /** 正常测温点 stddev */
    private static final double NORMAL_TEMP_STD = 5.0;

    public RobotDogSimulator(DeviceContext ctx, EventBus eventBus, SimulatorProperties properties) {
        super(ctx, eventBus, properties);
    }

    @Override
    protected long idlePatrolIntervalMs() {
        return properties.getSimulator().getDogSensorIntervalMs();
    }

    @Override
    protected void onIdlePatrol() {
        // 机器狗地面位移更小
        driftPositionLight();

        Map<String, Object> data = new HashMap<>();
        data.put("temperature", round1(RandomData.gaussianClamped(28.0, 2.0, 18.0, 40.0)));
        data.put("humidity", round1(RandomData.randomDouble(40.0, 80.0)));
        data.put("gas", round3(RandomData.randomDouble(0.0, 0.1)));
        data.put("lng", ctx.getLng());
        data.put("lat", ctx.getLat());

        eventBus.sendSensor(ctx.getDeviceId(), data);
    }

    @Override
    public void executeTask(String taskId, String taskType, String area, Map<String, Object> params) {
        ctx.setMode(DeviceContext.MODE_TASK);
        ctx.setCurrentTaskId(taskId);

        if (MsgType.TASK_TYPE_THERMAL.equals(taskType)) {
            executeThermalTask(taskId, params);
        } else if (MsgType.TASK_TYPE_SENSOR.equals(taskType)
                || MsgType.TASK_TYPE_COMPREHENSIVE.equals(taskType)) {
            executeSensorTask(taskId, params);
        } else {
            log.info("[TASK] dog {} unsupported task type {}", ctx.getDeviceId(), taskType);
        }

        ctx.setMode(DeviceContext.MODE_PATROL);
        ctx.setCurrentTaskId(null);
        sendStatus(true);
    }

    /**
     * 执行红外测温任务: 按 params.points 个点位,逐个上报 THERMAL。
     * params.tempThreshold 为后端告警判定阈值(§A.2 TASK_COMMAND);
     * 仿真器不直接发告警,只让温度数据携带"超阈值"信息,告警由后端规则引擎生成。
     */
    private void executeThermalTask(String taskId, Map<String, Object> params) {
        int points = (params != null && params.get("points") instanceof Number n)
                ? n.intValue()
                : DEFAULT_THERMAL_POINTS;
        double threshold = (params != null && params.get("tempThreshold") instanceof Number tn)
                ? tn.doubleValue()
                : DEFAULT_THERMAL_THRESHOLD;

        long intervalMs = properties.getSimulator().getThermalPointIntervalMs();
        log.info("[TASK] dog {} start THERMAL task {} points={} threshold={}",
                ctx.getDeviceId(), taskId, points, threshold);

        double ambient = RandomData.gaussianClamped(AMBIENT_BASE, 1.5, 22.0, 35.0);
        for (int i = 1; i <= points; i++) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("[TASK] dog {} interrupted during thermal task {}", ctx.getDeviceId(), taskId);
                break;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            driftPositionLight();

            // 决定该测点是"正常"还是"超阈值"
            double temperature;
            if (RandomData.chance(properties.getSimulator().getTempOverRate())) {
                // 超阈值: 高于 threshold 0~30℃
                temperature = threshold + RandomData.randomDouble(0.5, 30.0);
            } else {
                // 正常: NORMAL_TEMP_BASE 附近波动,且 ≤ threshold
                double v = RandomData.gaussianClamped(NORMAL_TEMP_BASE, NORMAL_TEMP_STD, 25.0, threshold - 0.5);
                temperature = v;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("pointId", Ids.thermalPointId(i));
            data.put("targetDevice", String.format("%s-%s测点%d", ctx.getArea(), ctx.getDeviceId(), i));
            data.put("temperature", round1(temperature));
            data.put("ambientTemp", round1(ambient));
            data.put("lng", ctx.getLng());
            data.put("lat", ctx.getLat());
            data.put("taskId", taskId);

            eventBus.sendThermal(ctx.getDeviceId(), data);
        }

        log.info("[TASK] dog {} finish THERMAL task {}", ctx.getDeviceId(), taskId);
    }

    /** 环境传感器任务: 持续若干轮 */
    private void executeSensorTask(String taskId, Map<String, Object> params) {
        int rounds = (params != null && params.get("points") instanceof Number n)
                ? n.intValue()
                : 10;
        long intervalMs = properties.getSimulator().getDogSensorIntervalMs();
        log.info("[TASK] dog {} start SENSOR task {} rounds={}", ctx.getDeviceId(), taskId, rounds);

        for (int i = 0; i < rounds; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            driftPositionLight();

            Map<String, Object> data = new HashMap<>();
            data.put("temperature", round1(RandomData.gaussianClamped(28.0, 2.0, 18.0, 40.0)));
            data.put("humidity", round1(RandomData.randomDouble(40.0, 80.0)));
            data.put("gas", round3(RandomData.randomDouble(0.0, 0.1)));
            data.put("lng", ctx.getLng());
            data.put("lat", ctx.getLat());
            eventBus.sendSensor(ctx.getDeviceId(), data);
        }

        log.info("[TASK] dog {} finish SENSOR task {}", ctx.getDeviceId(), taskId);
    }

    private void driftPositionLight() {
        double jitter = properties.getDevicePool().getPositionJitter();
        ctx.setLng(clamp(ctx.getLng() + RandomData.randomDouble(-jitter / 20, jitter / 20), -180, 180));
        ctx.setLat(clamp(ctx.getLat() + RandomData.randomDouble(-jitter / 20, jitter / 20), -90, 90));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
