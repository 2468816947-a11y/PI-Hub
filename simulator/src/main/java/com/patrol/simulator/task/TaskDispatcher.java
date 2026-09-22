package com.patrol.simulator.task;

import com.patrol.simulator.device.DeviceContext;
import com.patrol.simulator.device.DeviceSim;
import com.patrol.simulator.messaging.EventBus;
import com.patrol.simulator.messaging.MsgType;
import com.patrol.simulator.messaging.SimulatorConsumer;
import com.patrol.simulator.util.RandomData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 任务指令派发器。
 * <p>
 * 单一后台线程从 {@link SimulatorConsumer} 的指令队列拉取 TASK_COMMAND,
 * 找到目标设备并异步提交执行,执行完成后上报 TASK_RESULT。
 * <p>
 * 为什么异步 + 单独线程:
 * <ol>
 *   <li>执行过程要持续数秒到数十秒,不能阻塞 Kafka 消费线程(否则分区会被卡死)</li>
 *   <li>不同设备的执行互不干扰,各自在自己的调度线程里跑</li>
 * </ol>
 * <p>
 * 异常路径: 设备执行抛异常 → 仍上报一条 FAILED TASK_RESULT,避免业务侧看不到失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatcher {

    private final SimulatorConsumer consumer;
    private final EventBus eventBus;
    private final List<DeviceSim> devices;

    /** 派发线程(只一条;任务真正执行在每台设备自己的线程里) */
    private ExecutorService dispatcher;

    /** 当前正在执行的任务(供健康检查/调试) */
    private final Map<String, Future<?>> runningTasks = new HashMap<>();

    @PostConstruct
    public void start() {
        // daemon=true: 仿真器退出时不阻塞 JVM
        dispatcher = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sim-task-dispatcher");
            t.setDaemon(true);
            return t;
        });
        dispatcher.submit(this::dispatchLoop);
        log.info("[TASK-DISPATCHER] started, devices={}", devices.size());
    }

    @PreDestroy
    public void stop() {
        if (dispatcher != null) {
            dispatcher.shutdown();
            try {
                if (!dispatcher.awaitTermination(5, TimeUnit.SECONDS)) {
                    dispatcher.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                dispatcher.shutdownNow();
            }
        }
        log.info("[TASK-DISPATCHER] stopped");
    }

    private void dispatchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Map<String, Object> command;
            try {
                command = consumer.takeCommand();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Dispatcher takeCommand failed", e);
                continue;
            }

            try {
                dispatchOne(command);
            } catch (Throwable t) {
                log.error("Dispatch failed: {}", command, t);
            }
        }
        log.warn("[TASK-DISPATCHER] loop exited");
    }

    @SuppressWarnings("unchecked")
    private void dispatchOne(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        String taskId = (String) data.get("taskId");
        String taskType = (String) data.get("taskType");
        String area = (String) data.get("area");
        Object deviceIdsObj = data.get("deviceIds");
        Map<String, Object> params = (Map<String, Object>) data.getOrDefault("params", new HashMap<>());

        List<String> deviceIds = parseDeviceIds(deviceIdsObj);
        if (deviceIds.isEmpty()) {
            log.warn("[TASK] TASK_COMMAND without deviceIds: {}", data);
            return;
        }

        log.info("[TASK] dispatching task={} type={} to devices={}", taskId, taskType, deviceIds);

        for (String deviceId : deviceIds) {
            DeviceSim target = findDevice(deviceId);
            if (target == null) {
                log.warn("[TASK] target device not found in this simulator: {}", deviceId);
                continue;
            }
            if (DeviceContext.MODE_TASK.equals(target.getCtx().getMode())) {
                log.warn("[TASK] device {} already busy, drop task {}", deviceId, taskId);
                continue;
            }
            submitExecution(target, taskId, taskType, area, params);
        }
    }

    private void submitExecution(DeviceSim target, String taskId, String taskType,
                                 String area, Map<String, Object> params) {
        // 在设备自己的调度线程里执行——通过 schedule 一个立即任务
        // DeviceSim 没有暴露 executor,这里用一个简单封装:
        // 把执行丢到每台设备的调度器里,需要把 executeTask 用 schedule 调起。
        // 但 DeviceSim.executor 是 private,所以这里采用通用 ExecutorService 异步执行:
        // 因为 executeTask 内部对自身状态做了 volatile,且设备调度线程空闲上报会检查 mode = TASK 自动跳过
        ExecutorService exec = deviceExecutors.computeIfAbsent(target.getCtx().getDeviceId(),
                id -> Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "sim-exec-" + id);
                    t.setDaemon(true);
                    return t;
                }));
        Future<?> f = exec.submit(() -> {
            long start = System.currentTimeMillis();
            boolean failed = false;
            try {
                target.executeTask(taskId, taskType, area, params);
            } catch (Throwable t) {
                failed = true;
                log.error("[TASK] device {} execute task {} failed", target.getCtx().getDeviceId(), taskId, t);
            } finally {
                long cost = System.currentTimeMillis() - start;
                sendResult(taskId, target.getCtx().getDeviceId(), failed, cost);
                runningTasks.remove(taskId + ":" + target.getCtx().getDeviceId());
            }
        });
        runningTasks.put(taskId + ":" + target.getCtx().getDeviceId(), f);
    }

    /** 各设备的执行线程池(懒创建) */
    private final Map<String, ExecutorService> deviceExecutors = new HashMap<>();

    private void sendResult(String taskId, String deviceId, boolean failed, long costMs) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", failed ? MsgType.TASK_STATUS_FAILED : MsgType.TASK_STATUS_FINISHED);
        // 摘要文本: 后端据此更新任务 result.detail
        String detail = String.format("设备 %s 执行%s, 耗时 %dms%s",
                deviceId,
                failed ? "失败" : "完成",
                costMs,
                RandomData.chance(0.05) ? "(偶发延迟)" : "");
        data.put("detail", detail);
        data.put("finishTime", OffsetDateTime.now().toString());
        eventBus.sendTaskResult(taskId, data);
    }

    private static List<String> parseDeviceIds(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        if (obj instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    private DeviceSim findDevice(String deviceId) {
        for (DeviceSim d : devices) {
            if (d.getCtx().getDeviceId().equals(deviceId)) {
                return d;
            }
        }
        return null;
    }

    /** 当前在跑任务数(健康检查/调试) */
    public int runningTaskCount() {
        return runningTasks.size();
    }
}
