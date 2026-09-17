package com.patrol.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka Topic 配置(架构 §5: 8 个 Topic 统一管理)。
 * 配置项前缀 patrol.kafka.topics.*, 各 Topic 名可通过环境变量覆盖(便于多环境部署)。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "patrol.kafka.topics")
public class KafkaTopicProperties {

    /** 设备注册 */
    private String register;
    /** 心跳(5s 周期) */
    private String heartbeat;
    /** 状态/电量/故障变化 */
    private String status;
    /** 航拍图像元数据 */
    private String image;
    /** 红外测温 */
    private String thermal;
    /** 环境传感器 */
    private String sensor;
    /** 任务执行结果 */
    private String taskResult;
    /** 任务指令下发 */
    private String taskCommand;
}
