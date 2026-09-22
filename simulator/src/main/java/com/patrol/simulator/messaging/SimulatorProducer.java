package com.patrol.simulator.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrol.simulator.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 仿真器 Kafka 生产者(架构 §5: 仿真端作为生产者上报 7 类数据 + 任务结果)。
 * <p>
 * 设计要点(与后端 TaskCommandProducer 一致):
 * <ul>
 *   <li>按 deviceId 分区(架构 §5): 同设备消息有序</li>
 *   <li>异步发送 + 回调日志</li>
 *   <li>统一序列化器(消息构造在调用方完成,本类只负责发送)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatorProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SimulatorProperties properties;

    /** 默认分区键(用于任务结果等不直接绑定设备的消息) */
    public static final String DEFAULT_PARTITION_KEY = "simulator-default";

    /**
     * 发送一条消息到指定 Topic。
     *
     * @param topic     目标 Topic(从 {@link SimulatorProperties.Simulator.Topics} 取)
     * @param partitionKey Kafka 分区键(同设备消息用 deviceId 保序)
     * @param envelope  已构建好的消息信封(msgId/msgType/timestamp 由本方法自动填充)
     */
    public void send(String topic, String partitionKey, MessageEnvelope envelope) {
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // 仿真器侧数据全部自产,序列化失败属代码 bug,直接抛
            throw new IllegalStateException("Failed to serialize simulator message: " + envelope.getMsgType(), e);
        }

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, partitionKey, json);

        // 发送回调:不抛异常阻塞业务,但记录错误便于排查
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Send failed: topic={}, key={}, msgType={}, msgId={}",
                        topic, partitionKey, envelope.getMsgType(), envelope.getMsgId(), ex);
            } else if (log.isDebugEnabled()) {
                log.debug("Sent: topic={}, partition={}, offset={}, key={}, msgType={}, msgId={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        partitionKey,
                        envelope.getMsgType(),
                        envelope.getMsgId());
            }
        });
    }
}
