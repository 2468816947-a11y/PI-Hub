package com.patrol.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 无人机/机器狗仿真器 启动入口。
 * <p>
 * 运行模式: 宿主机 Java 进程,直连 docker compose 暴露的 Kafka/MongoDB/... 端口。
 * 对应部署手册 deploy/README.md §0 目录结构 与 架构文档 §9 部署拓扑。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableKafka
@EnableScheduling
public class PatrolSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatrolSimulatorApplication.class, args);
    }
}
