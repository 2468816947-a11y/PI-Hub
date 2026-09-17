package com.patrol.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

/**
 * 告警记录实体。
 * <p>
 * 文档对照(架构文档 §6.2): MongoDB alarm 集合字段完全一致。
 * 告警类型: TEMP_OVER(超温) / BATTERY_LOW(低电量) / OFFLINE(离线) / FAULT(故障)。
 * 告警等级: CRITICAL(严重) / MAJOR(重要) / MINOR(一般)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alarm")
public class Alarm {

    /**
     * 告警编号(PK), 后端生成如 A-001。
     * 文档字段: alarmId
     */
    @Id
    private String alarmId;

    /**
     * 设备编号。
     * 文档字段: deviceId
     */
    @Indexed
    private String deviceId;

    /**
     * 关联任务编号(离线告警可为 null)。
     * 文档字段: taskId
     */
    @Indexed
    private String taskId;

    /**
     * 告警类型: TEMP_OVER / BATTERY_LOW / OFFLINE / FAULT。
     * 文档字段: alarmType
     */
    private String alarmType;

    /**
     * 告警等级: CRITICAL / MAJOR / MINOR。
     * 文档字段: level
     */
    private String level;

    /**
     * 实测值(离线告警为 null)。
     * 文档字段: value
     */
    private Double value;

    /**
     * 触发阈值(TEMP_OVER 来自任务指令 params.tempThreshold; 离线告警为 null)。
     * 文档字段: threshold
     */
    private Double threshold;

    /**
     * 告警位置坐标。
     * 文档字段: position { lng, lat }
     */
    private AlarmPosition position;

    /**
     * 告警描述(写入 ES 时走 ik_max_word 分词)。
     * 文档字段: description
     */
    private String description;

    /**
     * 处置状态: NEW(未处置) / PROCESSED(已处置)。
     * 文档字段: status
     */
    private String status;

    /**
     * 告警产生时间。
     * 文档字段: createTime
     */
    private OffsetDateTime createTime;

    /**
     * 告警处置时间(未处置为 null)。
     * 文档字段: processTime
     */
    private OffsetDateTime processTime;

    /**
     * 处置备注(最长 500 字)。
     * 文档字段: processNote
     */
    private String processNote;

    /**
     * 告警位置(嵌套文档)。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmPosition {
        private Double lng;
        private Double lat;
    }
}
