package com.patrol.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 巡检任务实体。
 * <p>
 * 文档对照(架构文档 §6.2): MongoDB patrol_task 集合字段完全一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "patrol_task")
public class PatrolTask {

    /**
     * 任务编号(PK), 后端生成如 T-001。
     * 文档字段: taskId
     */
    @Id
    private String taskId;

    /**
     * 任务名称。
     * 文档字段: name
     */
    private String name;

    /**
     * 任务类型: THERMAL(红外测温) / IMAGE(航拍巡检) / SENSOR(环境监测) / COMPREHENSIVE(综合)。
     * 文档字段: taskType
     */
    private String taskType;

    /**
     * 巡检区域。
     * 文档字段: area
     */
    private String area;

    /**
     * 指派设备编号数组。
     * 文档字段: deviceIds
     */
    private List<String> deviceIds;

    /**
     * 任务状态: CREATED / DISPATCHED / RUNNING / FINISHED / FAILED。
     * 文档字段: status
     */
    private String status;

    /**
     * 任务创建时间。
     * 文档字段: createTime
     */
    private OffsetDateTime createTime;

    /**
     * 任务下发时间(发 Kafka 指令成功即 DISPATCHED)。
     * 文档字段: dispatchTime
     */
    private OffsetDateTime dispatchTime;

    /**
     * 任务完成时间(未完成为 null)。
     * 文档字段: finishTime
     */
    private OffsetDateTime finishTime;

    /**
     * 执行结果摘要。
     * 文档字段: result
     */
    private TaskResult result;

    /**
     * 任务执行结果明细(嵌套文档)。
     * 文档字段: result { pointCount, alarmCount, detail }
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResult {
        /** 测温/采集点数 */
        private Integer pointCount;
        /** 产生告警数 */
        private Integer alarmCount;
        /** 结果描述 */
        private String detail;
    }
}
