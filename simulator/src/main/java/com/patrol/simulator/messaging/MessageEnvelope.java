package com.patrol.simulator.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 仿真器侧统一消息信封(对应接口文档 §A.1,与后端 KafkaMessage 字段完全一致)。
 * <p>
 * 仿真器生产 7 类(REGISTER/HEARTBEAT/STATUS/IMAGE/THERMAL/SENSOR/TASK_RESULT),
 * 消费 1 类(TASK_COMMAND)；与后端契约保持一致即可被现有消费逻辑直接处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageEnvelope {

    /** 消息全局唯一 ID(UUID)—— 消费端幂等去重依据(架构 §5) */
    private String msgId;

    /** 消息类型 */
    private String msgType;

    /** 设备编号(Kafka 分区键,架构 §5: 同设备消息路由至同一分区,保证单设备有序) */
    private String deviceId;

    /** 设备类型: UAV / ROBOT_DOG */
    private String deviceType;

    /** 设备侧产生时间(ISO 8601 带时区) */
    private String timestamp;

    /** 业务载荷(各 msgType 字段定义见接口文档附录 A.2) */
    private Map<String, Object> data;
}
