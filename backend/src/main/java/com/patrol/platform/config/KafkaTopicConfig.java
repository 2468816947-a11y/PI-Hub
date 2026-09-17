package com.patrol.platform.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka Topic 自动声明(架构 §5)。
 * <p>
 * 启动时若 Topic 不存在则自动创建(分区数 3, 副本因子 1, 单节点 Kafka 适用);
 * 已存在则不重建, 由运维按需调整分区/副本。
 * <p>
 * Topic 名从 KafkaTopicProperties 注入, 便于多环境差异化部署。
 */
@Configuration
@RequiredArgsConstructor
public class KafkaTopicConfig {

    /** 单 broker 场景分区数: 3 个分区足以支撑并发消费与按 deviceId 保序 */
    private static final int PARTITIONS = 3;
    /** 单 broker 场景副本因子: 1(架构 §9 单容器部署) */
    private static final short REPLICATION_FACTOR = 1;

    private final KafkaTopicProperties topics;

    @Bean
    public NewTopic topicRegister() {
        return new NewTopic(topics.getRegister(), PARTITIONS, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic topicHeartbeat() {
        return new NewTopic(topics.getHeartbeat(), PARTITIONS, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic topicStatus() {
        return new NewTopic(topics.getStatus(), PARTITIONS, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic topicImage() {
        return new NewTopic(topics.getImage(), PARTITIONS, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic topicThermal() {
        return new NewTopic(topics.getThermal(), PARTITIONS, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic topicSensor() {
        return new NewTopic(topics.getSensor(), PARTITIONS, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic topicTaskResult() {
        // task.result 按 taskId 分区(架构 §5); 分区数与设备消息一致
        return new NewTopic(topics.getTaskResult(), PARTITIONS, REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic topicTaskCommand() {
        // task.command 按 deviceId 分区(架构 §5)
        return new NewTopic(topics.getTaskCommand(), PARTITIONS, REPLICATION_FACTOR);
    }
}
