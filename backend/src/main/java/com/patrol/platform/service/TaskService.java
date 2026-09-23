package com.patrol.platform.service;

import com.patrol.platform.common.BusinessConstants;
import com.patrol.platform.common.BusinessException;
import com.patrol.platform.common.ErrorCode;
import com.patrol.platform.common.IdGenerator;
import com.patrol.platform.entity.Device;
import com.patrol.platform.entity.PatrolTask;
import com.patrol.platform.kafka.KafkaMessage;
import com.patrol.platform.messaging.TaskCommandProducer;
import com.patrol.platform.repository.DeviceRepository;
import com.patrol.platform.repository.PatrolTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务业务 Service。
 * <p>
 * 职责:
 * <ul>
 *   <li>接收 TASK_RESULT 更新任务 status / finishTime / result</li>
 *   <li>接收 THERMAL/IMAGE/SENSOR 数据上报时维护 deviceProgress 进度</li>
 *   <li>提供 taskId → tempThreshold 缓存(消息消费时快速查表)</li>
 * </ul>
 * 进度定义(接口文档 §2.3.3): THERMAL 任务 = 已上报测点数 / params.points;
 * 其他类型简化估算(已上报消息条数 / 计划消息数)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final PatrolTaskRepository patrolTaskRepository;
    private final DeviceRepository deviceRepository;
    private final TaskCommandProducer taskCommandProducer;
    private final IdGenerator idGenerator;

    /** 任务运行时上下文缓存: taskId → 任务信息(含 tempThreshold 等参数) */
    private final Map<String, TaskContext> taskContextCache = new ConcurrentHashMap<>();

    /**
     * 注册任务上下文(任务下发成功后调用)。
     */
    public void registerTask(PatrolTask task) {
        Double tempThreshold = null;
        Integer planPoints = null;
        if (task.getResult() == null && task.getStatus() != null) {
            // 上线后参数从任务 params 读取; 阶段4 在 TaskServiceImpl 中接入
        }
        TaskContext ctx = new TaskContext(task.getTaskId(), task.getTaskType(),
                planPoints, tempThreshold);
        taskContextCache.put(task.getTaskId(), ctx);
        log.info("Task context registered: taskId={}, type={}", task.getTaskId(), task.getTaskType());
    }

    /**
     * 提供给 Thermal 消息消费时查询任务阈值(快速路径)。
     */
    public Optional<TaskContext> getContext(String taskId) {
        if (taskId == null) return Optional.empty();
        return Optional.ofNullable(taskContextCache.get(taskId));
    }

    // ==================== REST API 业务方法 ====================

    /**
     * 分页查询任务列表(按 createTime 倒序)。
     */
    public Page<PatrolTask> listTasks(String status, String taskType, Pageable pageable) {
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasType = taskType != null && !taskType.isEmpty();
        if (hasStatus && hasType) {
            return patrolTaskRepository.findByStatusAndTaskTypeOrderByCreateTimeDesc(status, taskType, pageable);
        } else if (hasStatus) {
            return patrolTaskRepository.findByStatusOrderByCreateTimeDesc(status, pageable);
        } else if (hasType) {
            return patrolTaskRepository.findByTaskTypeOrderByCreateTimeDesc(taskType, pageable);
        }
        return patrolTaskRepository.findAll(pageable);
    }

    /**
     * 任务详情(含设备进度)。
     * 进度估算(接口文档 §2.3.3): THERMAL=已上报测点数/params.points; 其他类型简化估算。
     */
    public PatrolTask getTaskOrThrow(String taskId) {
        PatrolTask t = patrolTaskRepository.findByTaskId(taskId);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        return t;
    }

    /**
     * 计算指定任务各设备的执行进度。
     */
    public List<com.patrol.platform.dto.TaskDto.DeviceProgress> getDeviceProgress(PatrolTask task) {
        List<com.patrol.platform.dto.TaskDto.DeviceProgress> result = new ArrayList<>();
        if (task.getDeviceIds() == null) return result;

        TaskContext ctx = taskContextCache.get(task.getTaskId());
        int reportedPoints = ctx != null ? ctx.reportedPointCount.get() : 0;
        Integer planPoints = ctx != null ? ctx.planPoints : null;

        for (String deviceId : task.getDeviceIds()) {
            int progress;
            String status;
            if (BusinessConstants.TASK_STATUS_FINISHED.equals(task.getStatus())
                    || BusinessConstants.TASK_STATUS_FAILED.equals(task.getStatus())) {
                status = task.getStatus();
                progress = 100;
            } else if (planPoints != null && planPoints > 0
                    && BusinessConstants.TASK_TYPE_THERMAL.equals(task.getTaskType())) {
                // THERMAL: 平均分配到每设备
                int perDevice = Math.max(1, planPoints / task.getDeviceIds().size());
                int deviceReported = Math.min(reportedPoints, perDevice);
                progress = Math.min(100, deviceReported * 100 / perDevice);
                status = progress >= 100 ? BusinessConstants.TASK_STATUS_FINISHED
                        : progress > 0 ? BusinessConstants.TASK_STATUS_RUNNING
                                : BusinessConstants.TASK_STATUS_DISPATCHED;
            } else {
                // 其他类型: 简化估算(若有 ctx 则给出估算进度)
                progress = ctx != null ? Math.min(100, reportedPoints * 10) : 0;
                status = progress > 0 ? BusinessConstants.TASK_STATUS_RUNNING
                        : BusinessConstants.TASK_STATUS_DISPATCHED;
            }
            OffsetDateTime lastUpdate = ctx != null && ctx.lastUpdateTime != null
                    ? ctx.lastUpdateTime
                    : task.getDispatchTime();
            result.add(new com.patrol.platform.dto.TaskDto.DeviceProgress(
                    deviceId, status, progress, lastUpdate));
        }
        return result;
    }

    /**
     * 创建并下发任务(POST /api/tasks)。
     * <p>
     * 行为(接口文档 §2.3.1):
     * <ol>
     *   <li>校验 deviceIds 中所有设备存在且 ONLINE, 否则 409</li>
     *   <li>写 Mongo(status=DISPATCHED), recordTime = now</li>
     *   <li>为每个设备发 Kafka TASK_COMMAND 指令</li>
     *   <li>注册 TaskContext(供后续消费端按 taskId 查 tempThreshold 等)</li>
     * </ol>
     */
    public PatrolTask createAndDispatchTask(String name, String taskType, String area,
                                             List<String> deviceIds, Map<String, Object> params) {
        // 1. 校验设备
        List<String> invalid = new ArrayList<>();
        for (String deviceId : deviceIds) {
            Device device = deviceRepository.findById(deviceId).orElse(null);
            if (device == null || !BusinessConstants.DEVICE_STATUS_ONLINE.equals(device.getStatus())) {
                invalid.add(deviceId + (device == null ? "(不存在)" : "(" + device.getStatus() + ")"));
            }
        }
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "存在无效设备(不存在或离线): " + String.join(", ", invalid));
        }

        // 2. 缺省参数(THERMAL: points=12, tempThreshold=80)
        Map<String, Object> finalParams = new HashMap<>();
        if (params != null) finalParams.putAll(params);
        Integer planPoints = null;
        Double tempThreshold = BusinessConstants.DEFAULT_TEMP_THRESHOLD;
        if (BusinessConstants.TASK_TYPE_THERMAL.equals(taskType)) {
            planPoints = finalParams.containsKey("points")
                    ? asInt(finalParams.get("points"))
                    : BusinessConstants.DEFAULT_THERMAL_POINTS;
            if (finalParams.containsKey("tempThreshold")) {
                tempThreshold = asDouble(finalParams.get("tempThreshold"));
            }
            finalParams.putIfAbsent("points", planPoints);
            finalParams.putIfAbsent("tempThreshold", tempThreshold);
        }

        // 3. 写 Mongo(DISPATCHED 状态, 同步动作)
        String taskId = idGenerator.nextTaskId();
        OffsetDateTime now = OffsetDateTime.now();
        PatrolTask task = PatrolTask.builder()
                .taskId(taskId)
                .name(name)
                .taskType(taskType)
                .area(area)
                .deviceIds(deviceIds)
                .status(BusinessConstants.TASK_STATUS_DISPATCHED)
                .createTime(now)
                .dispatchTime(now)
                .build();
        patrolTaskRepository.save(task);

        // 4. 注册 TaskContext(供消费端按 taskId 查阈值)
        taskContextCache.put(taskId,
                new TaskContext(taskId, taskType, planPoints, tempThreshold));

        // 5. 逐设备发 Kafka TASK_COMMAND
        for (String deviceId : deviceIds) {
            Device device = deviceRepository.findById(deviceId).orElse(null);
            String deviceType = device != null ? device.getDeviceType() : "UAV";
            Map<String, Object> cmdData = new HashMap<>();
            cmdData.put("taskId", taskId);
            cmdData.put("taskType", taskType);
            cmdData.put("area", area);
            cmdData.put("deviceIds", deviceIds);
            cmdData.put("params", finalParams);
            taskCommandProducer.send(deviceId, deviceType, KafkaMessage.MsgType.TASK_COMMAND, cmdData);
        }
        log.info("Task dispatched: taskId={}, taskType={}, deviceIds={}",
                taskId, taskType, deviceIds);
        return task;
    }

    private Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 处理 TASK_RESULT 消息(接口文档附录 A.2: TASK_RESULT data = {taskId, status, detail, finishTime})。
     */
    public void completeTask(KafkaMessage msg) {
        if (msg.getData() == null) return;
        String taskId = asString(msg.getData().get("taskId")).orElse(null);
        if (taskId == null) {
            log.warn("TASK_RESULT missing taskId, msgId={}", msg.getMsgId());
            return;
        }
        PatrolTask task = patrolTaskRepository.findByTaskId(taskId);
        if (task == null) {
            log.warn("TASK_RESULT for unknown taskId={}", taskId);
            return;
        }
        String status = asString(msg.getData().get("status")).orElse(BusinessConstants.TASK_STATUS_FINISHED);
        String detail = asString(msg.getData().get("detail")).orElse("");
        OffsetDateTime finishTime = parseIso(msg.getData().get("finishTime"));

        task.setStatus(BusinessConstants.TASK_STATUS_FINISHED.equals(status)
                ? BusinessConstants.TASK_STATUS_FINISHED
                : BusinessConstants.TASK_STATUS_FAILED);
        task.setFinishTime(finishTime != null ? finishTime : OffsetDateTime.now());

        // 汇总进度: 从缓存中取出已上报测点/数据数
        TaskContext ctx = taskContextCache.get(taskId);
        PatrolTask.TaskResult result = task.getResult();
        if (result == null) {
            result = new PatrolTask.TaskResult();
        }
        if (ctx != null) {
            result.setPointCount(ctx.reportedPointCount.get());
        } else {
            result.setPointCount(0);
        }
        result.setAlarmCount((int) alarmCountOf(taskId));
        result.setDetail(detail);
        task.setResult(result);
        patrolTaskRepository.save(task);

        log.info("Task completed: taskId={}, status={}, pointCount={}, alarmCount={}",
                taskId, task.getStatus(), result.getPointCount(), result.getAlarmCount());
    }

    /**
     * 记录 THERMAL 测点上报(用于设备进度估算)。
     */
    public void recordThermalPoint(KafkaMessage msg) {
        if (msg.getData() == null) return;
        String taskId = asString(msg.getData().get("taskId")).orElse(null);
        if (taskId == null) return;
        TaskContext ctx = taskContextCache.computeIfAbsent(taskId, k -> new TaskContext(k, BusinessConstants.TASK_TYPE_THERMAL, null, null));
        ctx.reportedPointCount.incrementAndGet();
        ctx.lastUpdateTime = OffsetDateTime.now();
    }

    /**
     * 记录 IMAGE/SENSOR 数据上报(用于进度估算)。
     */
    public void recordDataPoint(KafkaMessage msg) {
        if (msg.getData() == null) return;
        String taskId = asString(msg.getData().get("taskId")).orElse(null);
        if (taskId == null) return;
        TaskContext ctx = taskContextCache.computeIfAbsent(taskId, k -> new TaskContext(k, "GENERIC", null, null));
        ctx.reportedPointCount.incrementAndGet();
        ctx.lastUpdateTime = OffsetDateTime.now();
    }

    /**
     * 任务下发时记录参数阈值(阶段 4 TaskService 接线时调用)。
     */
    public void registerTaskParams(String taskId, String taskType, Integer planPoints, Double tempThreshold) {
        TaskContext ctx = new TaskContext(taskId, taskType, planPoints, tempThreshold);
        taskContextCache.put(taskId, ctx);
    }

    private long alarmCountOf(String taskId) {
        // 阶段 3 简化: 通过 AlarmRepository 统计(阶段 4 接线时实现)
        return 0L;
    }

    private Optional<String> asString(Object v) {
        if (v == null) return Optional.empty();
        return Optional.of(String.valueOf(v));
    }

    private OffsetDateTime parseIso(Object v) {
        if (v == null) return null;
        try {
            return OffsetDateTime.parse(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 任务运行时上下文(在 JVM 内存中缓存, 启动时也可从 Mongo 预热)。
     */
    public static class TaskContext {
        public final String taskId;
        public final String taskType;
        public final Integer planPoints;
        public final Double tempThreshold;
        public final java.util.concurrent.atomic.AtomicInteger reportedPointCount = new java.util.concurrent.atomic.AtomicInteger(0);
        public final java.util.concurrent.atomic.AtomicInteger reportedAlarmCount = new java.util.concurrent.atomic.AtomicInteger(0);
        public volatile OffsetDateTime lastUpdateTime;

        public TaskContext(String taskId, String taskType, Integer planPoints, Double tempThreshold) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.planPoints = planPoints;
            this.tempThreshold = tempThreshold;
        }
    }
}
