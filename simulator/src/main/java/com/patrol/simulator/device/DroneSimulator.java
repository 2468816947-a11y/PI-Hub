package com.patrol.simulator.device;

import com.patrol.simulator.config.SimulatorProperties;
import com.patrol.simulator.messaging.EventBus;
import com.patrol.simulator.messaging.MsgType;
import com.patrol.simulator.util.Ids;
import com.patrol.simulator.util.ImageGenerator;
import com.patrol.simulator.util.RandomData;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 无人机仿真器。
 * <p>
 * 行为:
 * <ul>
 *   <li>空闲巡航: 按 {@code droneImageIntervalMs} 节奏上报航拍图像(IMAGE)</li>
 *   <li>支持 VISIBLE / INFRARED 两种相机类型(每张图随机)</li>
 *   <li>位置缓慢漂移(模拟飞行)</li>
 *   <li>高度 80~150m 范围随机</li>
 *   <li>任务执行(IMAGE / COMPREHENSIVE): 连续上报 N 张图像</li>
 * </ul>
 */
@Slf4j
public class DroneSimulator extends DeviceSim {

    /** 任务默认测点数(对应 §A.2 TASK_COMMAND.params.points) */
    private static final int DEFAULT_IMAGE_POINTS = 8;
    private static final int DEFAULT_VISIBLE_COUNT = 6;
    private static final int DEFAULT_INFRARED_COUNT = 2;

    public DroneSimulator(DeviceContext ctx, EventBus eventBus, SimulatorProperties properties) {
        super(ctx, eventBus, properties);
    }

    @Override
    protected long idlePatrolIntervalMs() {
        return properties.getSimulator().getDroneImageIntervalMs();
    }

    @Override
    protected void onIdlePatrol() {
        // 缓慢漂移位置
        driftPosition();

        String cameraType = RandomData.pick(new String[]{MsgType.CAMERA_VISIBLE, MsgType.CAMERA_VISIBLE, MsgType.CAMERA_INFRARED});
        String imageId = Ids.imageId();
        String fileName = String.format("%s_%s_%s.jpg",
                ctx.getDeviceId(),
                java.time.LocalDate.now().toString().replace("-", ""),
                java.time.LocalTime.now().toString().replace(":", "").substring(0, 6));

        Map<String, Object> data = new HashMap<>();
        data.put("imageId", imageId);
        data.put("cameraType", cameraType);
        data.put("lng", ctx.getLng());
        data.put("lat", ctx.getLat());
        data.put("altitude", RandomData.randomDouble(80.0, 150.0));
        // 空闲上报不绑定任务
        data.put("taskId", null);
        data.put("fileName", fileName);

        // 可选 base64(模拟图片 ≤ 200KB)
        if (properties.getSimulator().isImageBase64Enabled()) {
            byte[] jpeg = ImageGenerator.generateJpeg(cameraType, ctx.getDeviceId(), "IDLE");
            data.put("fileSize", (long) jpeg.length);
            data.put("imageBase64", ImageGenerator.toBase64(jpeg));
        } else {
            data.put("fileSize", RandomData.randomLong(120_000L, 200_000L));
        }

        eventBus.sendImage(ctx.getDeviceId(), data);
    }

    @Override
    public void executeTask(String taskId, String taskType, String area, Map<String, Object> params) {
        // 切到任务模式:空闲调度会自动暂停上报
        ctx.setMode(DeviceContext.MODE_TASK);
        ctx.setCurrentTaskId(taskId);

        int totalPoints;
        if (taskType.equals(MsgType.TASK_TYPE_IMAGE) || taskType.equals(MsgType.TASK_TYPE_COMPREHENSIVE)) {
            totalPoints = params != null && params.get("points") instanceof Number n
                    ? n.intValue()
                    : DEFAULT_IMAGE_POINTS;
        } else {
            // 非图像类任务由无人机执行时,回 0 张图(机器狗主导)
            log.info("[TASK] drone {} skip non-IMAGE task {}", ctx.getDeviceId(), taskType);
            ctx.setMode(DeviceContext.MODE_PATROL);
            ctx.setCurrentTaskId(null);
            return;
        }

        log.info("[TASK] drone {} start IMAGE task {} points={}", ctx.getDeviceId(), taskId, totalPoints);
        long intervalMs = properties.getSimulator().getImageIntervalMs();

        // 同步按节奏上报: 阻塞在调度线程(无人机的调度线程此时专用于任务)
        for (int i = 1; i <= totalPoints; i++) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("[TASK] drone {} interrupted during task {}", ctx.getDeviceId(), taskId);
                break;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            driftPosition();

            // 红外图只占少数
            String cameraType = (i <= DEFAULT_INFRARED_COUNT)
                    ? MsgType.CAMERA_INFRARED
                    : MsgType.CAMERA_VISIBLE;

            String imageId = Ids.imageId();
            String fileName = String.format("%s_%s_%s_pt%02d.jpg",
                    ctx.getDeviceId(),
                    java.time.LocalDate.now().toString().replace("-", ""),
                    taskId,
                    i);

            Map<String, Object> data = new HashMap<>();
            data.put("imageId", imageId);
            data.put("cameraType", cameraType);
            data.put("lng", ctx.getLng());
            data.put("lat", ctx.getLat());
            data.put("altitude", RandomData.randomDouble(80.0, 150.0));
            data.put("taskId", taskId);
            data.put("fileName", fileName);

            if (properties.getSimulator().isImageBase64Enabled()) {
                byte[] jpeg = ImageGenerator.generateJpeg(cameraType, ctx.getDeviceId(), taskId);
                data.put("fileSize", (long) jpeg.length);
                data.put("imageBase64", ImageGenerator.toBase64(jpeg));
            } else {
                data.put("fileSize", RandomData.randomLong(120_000L, 200_000L));
            }

            eventBus.sendImage(ctx.getDeviceId(), data);
        }

        log.info("[TASK] drone {} finish IMAGE task {}", ctx.getDeviceId(), taskId);
        // 恢复空闲态
        ctx.setMode(DeviceContext.MODE_PATROL);
        ctx.setCurrentTaskId(null);
        // 主动报一次状态(mode 变了)
        sendStatus(true);
    }

    /** 位置缓慢漂移 */
    private void driftPosition() {
        double jitter = properties.getDevicePool().getPositionJitter();
        ctx.setLng(clampLng(ctx.getLng() + RandomData.randomDouble(-jitter / 10, jitter / 10)));
        ctx.setLat(clampLat(ctx.getLat() + RandomData.randomDouble(-jitter / 10, jitter / 10)));
    }

    private static double clampLng(double v) {
        return Math.max(-180.0, Math.min(180.0, v));
    }

    private static double clampLat(double v) {
        return Math.max(-90.0, Math.min(90.0, v));
    }
}
