package com.patrol.simulator.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 仿真器侧 Kafka 消费者: 只订阅 patrol.task.command。
 * <p>
 * 解耦方式: 消费者只负责"解析 + 入队 + ack",实际派发到设备执行由 {@link com.patrol.simulator.task.TaskDispatcher}
 * 异步消费本类暴露的命令队列。
 * <p>
 * 这样设计的好处:
 * <ol>
 *   <li>Kafka 消费线程不阻塞在设备执行(测温点位逐个上报,可能持续数十秒)</li>
 *   <li>设备执行器可以独立调度,与消息消费节流解耦</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatorConsumer {

    private final ObjectMapper objectMapper;

    /** 任务指令队列(有界,容量 256,防突发挤压内存) */
    private final BlockingQueue<Map<String, Object>> commandQueue = new LinkedBlockingQueue<>(256);

    /**
     * 订阅 patrol.task.command。
     * <p>
     * 与后端 TaskCommandProducer 对应: 信封格式一致,载荷字段见接口文档 §A.2 TASK_COMMAND。
     * deviceId 即目标设备。
     */
    @KafkaListener(topics = "${simulator.topics.task-command}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onTaskCommand(String payload, Acknowledgment ack) {
        try {
            MessageEnvelope envelope = objectMapper.readValue(payload, MessageEnvelope.class);

            if (!MsgType.TASK_COMMAND.equals(envelope.getMsgType())) {
                log.warn("Unexpected msgType on task-command topic: {}, skip", envelope.getMsgType());
                ack.acknowledge();
                return;
            }

            // 入队: 若队列已满短暂阻塞(限流保护)
            boolean offered = false;
            long deadline = System.currentTimeMillis() + 5000;
            while (!offered && System.currentTimeMillis() < deadline) {
                offered = commandQueue.offer(envelope.getData(), 100, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            if (!offered) {
                log.error("Task command queue full, drop: deviceId={}, taskId={}",
                        envelope.getDeviceId(),
                        envelope.getData() != null ? envelope.getData().get("taskId") : null);
            } else {
                log.info("[CMD] received TASK_COMMAND device={} taskId={}",
                        envelope.getDeviceId(),
                        envelope.getData() != null ? envelope.getData().get("taskId") : null);
            }

            // 手动 ack(配置: ack-mode: manual)
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            // 数据污染: 跳过 + ack(避免卡住分区)
            log.error("Bad task-command payload, skip and ack: {}", e.getMessage());
            ack.acknowledge();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while enqueueing task command");
        } catch (Exception e) {
            // 兜底: 异常也 ack,不让单条坏消息阻塞分区
            log.error("Unexpected error consuming task-command", e);
            ack.acknowledge();
        }
    }

    /** 供 TaskDispatcher 拉取下一条指令(阻塞) */
    public Map<String, Object> takeCommand() throws InterruptedException {
        return commandQueue.take();
    }

    /** 当前队列大小(用于监控/健康检查) */
    public int queueSize() {
        return commandQueue.size();
    }
}
