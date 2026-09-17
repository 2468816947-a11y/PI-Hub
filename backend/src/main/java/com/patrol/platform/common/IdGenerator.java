package com.patrol.platform.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务编号生成器。
 * <p>
 * 设计权衡: 仿真项目无需分布式 ID, 采用时间戳 + 自增序列即可满足唯一性;
 * 真实生产应替换为雪花算法或 Mongo $sequence。
 */
public final class IdGenerator {

    private IdGenerator() {}

    private static final AtomicLong DEVICE_COUNTER = new AtomicLong(0);
    private static final AtomicLong TASK_COUNTER = new AtomicLong(0);
    private static final AtomicLong ALARM_COUNTER = new AtomicLong(0);

    private static final DateTimeFormatter EVENT_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 生成设备编号: 后端未传入 deviceId 时使用, 形如 UAV-1234567890-1。
     * <p>
     * 真实场景应取 max(existingId) + 1, 此处简化处理。
     */
    public static String nextDeviceId(String deviceType) {
        long seq = DEVICE_COUNTER.incrementAndGet();
        String prefix = DEVICE_TYPE_ROBOT_DOG.equals(deviceType) ? "ROBOTDOG" : "UAV";
        return String.format("%s-%013d-%d", prefix, System.currentTimeMillis(), seq);
    }

    /**
     * 生成任务编号: 形如 T-yyyyMMdd-序号。
     */
    public static String nextTaskId() {
        long seq = TASK_COUNTER.incrementAndGet();
        return String.format("T-%s-%05d", LocalDate.now().format(EVENT_DATE), seq);
    }

    /**
     * 生成告警编号: 形如 A-yyyyMMdd-序号。
     */
    public static String nextAlarmId() {
        long seq = ALARM_COUNTER.incrementAndGet();
        return String.format("A-%s-%05d", LocalDate.now().format(EVENT_DATE), seq);
    }

    /**
     * 生成事件编号: 形如 E-yyyyMMdd-序号。
     */
    public static String nextEventId() {
        long seq = DEVICE_COUNTER.incrementAndGet();
        return String.format("E-%s-%07d", LocalDate.now().format(EVENT_DATE), seq);
    }

    /**
     * 生成文件编号: 形如 img-xxxxxxxx(8 位随机字符串)。
     */
    public static String nextFileId() {
        return "img-" + randomHex(8);
    }

    private static String randomHex(int len) {
        StringBuilder sb = new StringBuilder(len);
        String hex = "0123456789abcdef";
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < len; i++) {
            sb.append(hex.charAt(rnd.nextInt(16)));
        }
        return sb.toString();
    }
}
