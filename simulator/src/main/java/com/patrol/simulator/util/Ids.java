package com.patrol.simulator.util;

import java.util.UUID;

/**
 * 仿真器侧 ID 生成器。
 * <p>
 * 集中维护所有 ID 格式,避免散落的字面量:
 * <ul>
 *   <li>消息 ID: UUID(对应后端幂等去重)</li>
 *   <li>图像 ID: img- + 8 位 hex(与后端 fileId 一致: img-3f2a9c1b)</li>
 *   <li>测点 ID: TMP- + 3 位序号(同任务内递增)</li>
 *   <li>设备 ID: UAV-001 / ROBOTDOG-001(与架构 §9 一致)</li>
 * </ul>
 */
public final class Ids {

    private Ids() {}

    /** 通用 UUID(消息 msgId) */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** 图像 fileId: img-xxxxxxxx (8 位 hex) */
    public static String imageId() {
        return "img-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** 测点 pointId: TMP-001 */
    public static String thermalPointId(int seq) {
        return String.format("TMP-%03d", seq);
    }

    /** 无人机编号: UAV-001 */
    public static String droneId(int seq) {
        return String.format("UAV-%03d", seq);
    }

    /** 机器狗编号: ROBOTDOG-001 */
    public static String robotDogId(int seq) {
        return String.format("ROBOTDOG-%03d", seq);
    }
}
