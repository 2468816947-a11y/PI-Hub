package com.patrol.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电力场站设备巡检数据集成平台 —— 业务服务启动类
 */
@SpringBootApplication
@EnableScheduling   // 启用 @Scheduled 离线检测定时任务(阶段 3)
public class PatrolApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatrolApplication.class, args);
    }
}
