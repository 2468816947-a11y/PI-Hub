package com.patrol.platform.messaging;

import com.patrol.platform.kafka.KafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka 消息监听器(架构 §5: 监听 8 个 Topic)。
 * <p>
 * 每个 Topic 一个独立 @KafkaListener 方法, 串行处理(同 Topic 内保序);
 * 不同 Topic 间可并行(由 spring.kafka.listener.concurrency 控制)。
 * <p>
 * 流程: 接收 JSON → 解析为 KafkaMessage → 幂等去重 → 分发到业务 → 手动 ack。
 * <p>
 * 手动 ack 与 enable.auto.commit=false 配合, 避免消息丢失或重复消费;
 * 异常捕获后仍 ack, 保证消费不被单条坏消息阻塞(业务层面需另做死信/重试机制)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

    private final MessageDeduplicationService deduplicationService;
    private final MessageDispatcher dispatcher;

    @KafkaListener(topics = "${patrol.kafka.topics.register}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onRegister(String payload, Acknowledgment ack) {
        consume(payload, ack, "REGISTER");
    }

    @KafkaListener(topics = "${patrol.kafka.topics.heartbeat}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onHeartbeat(String payload, Acknowledgment ack) {
        consume(payload, ack, "HEARTBEAT");
    }

    @KafkaListener(topics = "${patrol.kafka.topics.status}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onStatus(String payload, Acknowledgment ack) {
        consume(payload, ack, "STATUS");
    }

    @KafkaListener(topics = "${patrol.kafka.topics.image}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onImage(String payload, Acknowledgment ack) {
        consume(payload, ack, "IMAGE");
    }

    @KafkaListener(topics = "${patrol.kafka.topics.thermal}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onThermal(String payload, Acknowledgment ack) {
        consume(payload, ack, "THERMAL");
    }

    @KafkaListener(topics = "${patrol.kafka.topics.sensor}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onSensor(String payload, Acknowledgment ack) {
        consume(payload, ack, "SENSOR");
    }

    @KafkaListener(topics = "${patrol.kafka.topics.task-result}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onTaskResult(String payload, Acknowledgment ack) {
        consume(payload, ack, "TASK_RESULT");
    }

    // task.command 主要是平台→设备的下行消息, 业务服务通常不消费,
    // 但保留 Listener 占位, 防止因 group 订阅而被其他仿真器消费时遗漏(同组订阅会消费所有 Topic)
    @KafkaListener(topics = "${patrol.kafka.topics.task-command}",
            groupId = "${spring.kafka.consumer.group-id}", autoStartup = "false")
    public void onTaskCommand(String payload, Acknowledgment ack) {
        consume(payload, ack, "TASK_COMMAND");
    }

    /**
     * 通用消费流程: 解析 → 幂等去重 → 分发 → ack。
     * <p>
     * 异常分层处理:
     * <ul>
     *   <li>JSON 解析失败: 跳过并 ack(数据污染, 重试无意义)</li>
     *   <li>幂等命中: 跳过业务, 直接 ack</li>
     *   <li>业务异常: 已在 Dispatcher 内捕获, 此处仍 ack(由死信/重试机制承接)</li>
     * </ul>
     */
    private void consume(String payload, Acknowledgment ack, String expectedType) {
        try {
            KafkaMessage msg = deduplicationService.parse(payload);

            // 类型校验: 防止 Topic 误投递(如 register Topic 收到 HEARTBEAT 消息)
            if (!expectedType.equals(msg.getMsgType())) {
                log.warn("Type mismatch on topic expected={}, actual={}, msgId={}, deviceId={}",
                        expectedType, msg.getMsgType(), msg.getMsgId(), msg.getDeviceId());
                ack.acknowledge();
                return;
            }

            // 幂等去重: 已处理则直接跳过
            if (deduplicationService.isAlreadyProcessed(msg.getMsgId())) {
                log.debug("Duplicate msg skipped: msgId={}, msgType={}", msg.getMsgId(), msg.getMsgType());
                ack.acknowledge();
                return;
            }

            // 分发到业务
            dispatcher.dispatch(msg, ack);

            // 标记已处理(并发场景下由唯一索引兜底)
            deduplicationService.markProcessed(msg);

            // 手动 ack: 只有 ack 才会真正提交 offset
            ack.acknowledge();
        } catch (IllegalArgumentException e) {
            // JSON 解析失败: 跳过该消息(否则会卡住整个分区)
            log.error("Bad message, skip and ack: topic={}, error={}", expectedType, e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            // 兜底异常: 记录后 ack(避免单条坏消息阻塞分区)
            log.error("Unexpected error consuming topic={}", expectedType, e);
            ack.acknowledge();
        }
    }
}
