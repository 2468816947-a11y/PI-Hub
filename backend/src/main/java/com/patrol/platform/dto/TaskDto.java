package com.patrol.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.patrol.platform.entity.PatrolTask;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务相关 DTO。
 */
public final class TaskDto {

    private TaskDto() {}

    /**
     * 创建任务请求(接口文档 §2.3.1)。
     */
    public record TaskCreateRequest(
            @NotBlank(message = "name 不能为空") String name,
            @NotBlank(message = "taskType 不能为空")
            @Pattern(regexp = "^(THERMAL|IMAGE|SENSOR|COMPREHENSIVE)$",
                    message = "taskType 必须是 THERMAL/IMAGE/SENSOR/COMPREHENSIVE")
            String taskType,
            String area,
            @NotEmpty(message = "deviceIds 不能为空") List<String> deviceIds,
            /** 指令参数; 缺省按 taskType 取默认值(架构 §A.3) */
            Map<String, Object> params
    ) {}

    /**
     * 任务 VO。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskVO(
            String taskId,
            String name,
            String taskType,
            String area,
            List<String> deviceIds,
            String status,
            OffsetDateTime createTime,
            OffsetDateTime dispatchTime,
            OffsetDateTime finishTime,
            Map<String, Object> result
    ) {
        public static TaskVO from(PatrolTask t) {
            return new TaskVO(
                    t.getTaskId(),
                    t.getName(),
                    t.getTaskType(),
                    t.getArea(),
                    t.getDeviceIds(),
                    t.getStatus(),
                    t.getCreateTime(),
                    t.getDispatchTime(),
                    t.getFinishTime(),
                    t.getResult() == null ? null
                            : Map.of(
                                "pointCount", t.getResult().getPointCount() != null ? t.getResult().getPointCount() : 0,
                                "alarmCount", t.getResult().getAlarmCount() != null ? t.getResult().getAlarmCount() : 0,
                                "detail", t.getResult().getDetail() != null ? t.getResult().getDetail() : "")
            );
        }
    }

    /**
     * 任务详情响应(接口文档 §2.3.3): 含 task + deviceProgress。
     */
    public record TaskDetailResponse(TaskVO task, List<DeviceProgress> deviceProgress) {}

    /**
     * 单设备执行进度(0-100 整数)。
     */
    public record DeviceProgress(
            String deviceId,
            String status,
            int progress,
            OffsetDateTime lastUpdateTime
    ) {}
}
