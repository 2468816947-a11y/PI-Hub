package com.patrol.platform.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrol.platform.entity.ProcessedMessage;
import com.patrol.platform.kafka.KafkaMessage;
import com.patrol.platform.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 消息幂等去重服务(架构 §5: msgId 幂等去重)。
 * <p>
 * 实现方式: 基于 MongoDB processed_message 集合的 msgId 唯一索引兜底。
 * 先用 existsByMsgId 快速判断, 处理完成后插入处理记录;
 * 重复消息在插入阶段抛 DuplicateKeyException, 视为已处理并跳过。
 * <p>
 * 注意: 严格保证"先查后插"——若先插再查会出现竞态, 失去幂等性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    /**
     * 检查消息是否已被处理(快速路径)。
     *
     * @param msgId Kafka 消息 UUID
     * @return true 表示已处理, false 表示需要处理
     */
    public boolean isAlreadyProcessed(String msgId) {
        if (msgId == null || msgId.isEmpty()) {
            // 缺失 msgId 视为非法消息, 由上层拒绝(避免误处理造成数据污染)
            log.warn("msgId is null or empty, treating as already processed");
            return true;
        }
        return processedMessageRepository.existsByMsgId(msgId);
    }

    /**
     * 标记消息已处理(写入 Mongo 去重记录)。
     * <p>
     * 通过 Mongo 唯一索引保证并发幂等: 若同时被多实例消费, 第二个插入会抛 DuplicateKeyException。
     *
     * @param msg    已处理的消息
     * @return true 表示本次标记成功, false 表示已被其他实例标记(视为幂等跳过)
     */
    public boolean markProcessed(KafkaMessage msg) {
        try {
            ProcessedMessage record = ProcessedMessage.builder()
                    .msgId(msg.getMsgId())
                    .msgType(msg.getMsgType())
                    .deviceId(msg.getDeviceId())
                    .processedAt(OffsetDateTime.now())
                    .build();
            processedMessageRepository.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突: 并发场景下其他实例已标记, 视为幂等成功
            log.debug("msgId={} already marked by another consumer", msg.getMsgId());
            return false;
        }
    }

    /**
     * 将 JSON 字符串反序列化为 KafkaMessage。
     * <p>
     * 失败时抛 IllegalArgumentException, 由 Listener 层捕获并跳过(Bad Message 隔离)。
     */
    public KafkaMessage parse(String json) {
        try {
            return objectMapper.readValue(json, KafkaMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid Kafka message JSON: " + e.getOriginalMessage(), e);
        }
    }
}
