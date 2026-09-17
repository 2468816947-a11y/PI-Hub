package com.patrol.platform.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一消息信封(对应接口文档附录 A.1)。
 * <p>
 * 字段含义:
 * <ul>
 *   <li>msgId     —— 消息全局唯一 ID(UUID), 消费端幂等去重依据</li>
 *   <li>msgType   —— 消息类型: REGISTER / HEARTBEAT / STATUS / IMAGE / THERMAL / SENSOR / TASK_RESULT / TASK_COMMAND</li>
 *   <li>deviceId  —— 设备编号; Kafka 分区键(架构 §5 按 deviceId 分区保序)</li>
 *   <li>deviceType—— UAV / ROBOT_DOG</li>
 *   <li>timestamp —— 设备侧产生时间(ISO 8601 带时区)</li>
 *   <li>data      —— 业务载荷(Map 形态以便承载不同 msgType 的异构字段; 各载荷字段定义见附录 A.2)</li>
 * </ul>
 * 注意: 信封**没有** taskId 字段 —— 与任务相关的信息(taskId)一律放在 data 载荷内。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaMessage {

    /** 消息全局唯一 ID(UUID) */
    private String msgId;

    /** 消息类型 */
    private String msgType;

    /** 设备编号(分区键) */
    private String deviceId;

    /** 设备类型: UAV / ROBOT_DOG */
    private String deviceType;

    /** 设备侧产生时间(ISO 8601 带时区) */
    private String timestamp;

    /** 业务载荷(字段定义见接口文档附录 A.2) */
    private Map<String, Object> data;

    /**
     * 所有 Kafka 消息类型常量。
     * 与 Topic 一一对应(架构 §5), 仿真器与业务服务统一引用。
     */
    public static final class MsgType {
        public static final String REGISTER     = "REGISTER";
        public static final String HEARTBEAT    = "HEARTBEAT";
        public static final String STATUS       = "STATUS";
        public static final String IMAGE        = "IMAGE";
        public static final String THERMAL      = "THERMAL";
        public static final String SENSOR       = "SENSOR";
        public static final String TASK_RESULT  = "TASK_RESULT";
        public static final String TASK_COMMAND = "TASK_COMMAND";

        private MsgType() {}
    }
}
