package com.patrol.platform.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrol.platform.config.KafkaTopicProperties;
import com.patrol.platform.kafka.KafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 任务指令下发生产者(架构 §5: patrol.task.command 由平台业务服务生产)。
 * <p>
 * 行为约束:
 * <ul>
 *   <li>按 deviceId 分区(架构 §5): 同一设备指令有序消费</li>
 *   <li>每条消息分配唯一 msgId(消费端幂等去重依据)</li>
 *   <li>异步发送 + 回调日志; 失败抛业务异常由调用方决策重试/告警</li>
 * </ul>
 * 阶段 4 接入: POST /api/tasks 创建任务时, 由 TaskService 调用此 Producer。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCommandProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties topics;
    private final ObjectMapper objectMapper;

    /**
     * 下发任务指令到 patrol.task.command。
     *
     * @param deviceId 目标设备编号(Kafka 分区键)
     * @param msgType  消息类型(应为 TASK_COMMAND)
     * @param deviceType 设备类型(UAV / ROBOT_DOG)
     * @param data     业务载荷(参见接口文档附录 A.2 TASK_COMMAND data)
     */
    public void send(String deviceId, String deviceType, String msgType, Map<String, Object> data) {
        KafkaMessage message = KafkaMessage.builder()
                .msgId(UUID.randomUUID().toString())
                .msgType(msgType)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .timestamp(OffsetDateTime.now().toString())
                .data(data)
                .build();

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // 序列化失败: 数据问题, 抛业务异常由 GlobalExceptionHandler 转为 500
            throw new IllegalStateException("Failed to serialize task command", e);
        }

        String topic = topics.getTaskCommand();
        // 按 deviceId 分区: 保证同设备指令有序
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, deviceId, json);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Task command send failed: topic={}, deviceId={}, taskId={}, msgId={}",
                        topic, deviceId,
                        data != null ? data.get("taskId") : null,
                        message.getMsgId(), ex);
            } else {
                log.info("Task command sent: topic={}, partition={}, offset={}, deviceId={}, taskId={}, msgId={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        deviceId,
                        data != null ? data.get("taskId") : null,
                        message.getMsgId());
            }
        });
    }
}
