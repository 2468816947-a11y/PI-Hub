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
 * 已处理消息记录(幂等去重)。
 * <p>
 * Kafka 消费端按 msgId 幂等去重: 先查此集合, 存在则跳过, 不存在则处理后插入。
 * TTL 索引保证消息记录在 7 天后自动删除(避免集合无限膨胀)。
 * 架构文档 §5: "按 msgId 幂等去重(Redis/内存 Set + Mongo 唯一索引兜底)"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "processed_message")
public class ProcessedMessage {

    /**
     * Kafka 消息 ID(UUID), 全局唯一。
     * 文档字段: msgId
     */
    @Id
    private String msgId;

    /**
     * 消息类型: IMAGE / THERMAL / SENSOR / STATUS / HEARTBEAT / TASK_RESULT / REGISTER。
     * 便于排查与统计。
     */
    private String msgType;

    /**
     * 所属设备编号。
     */
    @Indexed
    private String deviceId;

    /**
     * 消息处理时间(用于 TTL 计算与问题排查)。
     */
    private OffsetDateTime processedAt;
}
