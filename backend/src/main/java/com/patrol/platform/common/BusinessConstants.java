package com.patrol.platform.common;

/**
 * 业务状态/类型枚举常量集中管理, 避免硬编码字符串散落各处。
 * 所有枚举值与接口文档 §1.5 一致。
 */
public final class BusinessConstants {

    private BusinessConstants() {}

    // ==================== 设备 ====================
    /** 设备类型: 无人机 */
    public static final String DEVICE_TYPE_UAV = "UAV";
    /** 设备类型: 机器狗 */
    public static final String DEVICE_TYPE_ROBOT_DOG = "ROBOT_DOG";

    /** 设备状态: 在线 */
    public static final String DEVICE_STATUS_ONLINE = "ONLINE";
    /** 设备状态: 离线 */
    public static final String DEVICE_STATUS_OFFLINE = "OFFLINE";

    /** 心跳离线阈值: 15 秒(3 个心跳周期 @5s) */
    public static final long HEARTBEAT_OFFLINE_THRESHOLD_SECONDS = 15L;
    /** 心跳离线扫描周期: 5 秒 */
    public static final long HEARTBEAT_SCAN_INTERVAL_MS = 5000L;

    /** 初始电量: 100% */
    public static final int INITIAL_BATTERY = 100;

    // ==================== 任务 ====================
    /** 任务类型 */
    public static final String TASK_TYPE_THERMAL = "THERMAL";
    public static final String TASK_TYPE_IMAGE = "IMAGE";
    public static final String TASK_TYPE_SENSOR = "SENSOR";
    public static final String TASK_TYPE_COMPREHENSIVE = "COMPREHENSIVE";

    /** 任务状态 */
    public static final String TASK_STATUS_CREATED = "CREATED";
    public static final String TASK_STATUS_DISPATCHED = "DISPATCHED";
    public static final String TASK_STATUS_RUNNING = "RUNNING";
    public static final String TASK_STATUS_FINISHED = "FINISHED";
    public static final String TASK_STATUS_FAILED = "FAILED";

    /** THERMAL 任务缺省计划测点数 */
    public static final int DEFAULT_THERMAL_POINTS = 12;
    /** THERMAL 任务缺省超温告警阈值 */
    public static final double DEFAULT_TEMP_THRESHOLD = 80.0;

    // ==================== 告警 ====================
    /** 告警类型 */
    public static final String ALARM_TYPE_TEMP_OVER = "TEMP_OVER";
    public static final String ALARM_TYPE_BATTERY_LOW = "BATTERY_LOW";
    public static final String ALARM_TYPE_OFFLINE = "OFFLINE";
    public static final String ALARM_TYPE_FAULT = "FAULT";

    /** 告警等级 */
    public static final String ALARM_LEVEL_CRITICAL = "CRITICAL";
    public static final String ALARM_LEVEL_MAJOR = "MAJOR";
    public static final String ALARM_LEVEL_MINOR = "MINOR";

    /** 告警处置状态 */
    public static final String ALARM_STATUS_NEW = "NEW";
    public static final String ALARM_STATUS_PROCESSED = "PROCESSED";

    /** 低电量阈值 */
    public static final int BATTERY_LOW_THRESHOLD = 20;

    // ==================== 事件类型 ====================
    public static final String EVENT_TYPE_IMAGE = "IMAGE";
    public static final String EVENT_TYPE_THERMAL = "THERMAL";
    public static final String EVENT_TYPE_SENSOR = "SENSOR";
    public static final String EVENT_TYPE_ALARM = "ALARM";

    // ==================== 相机类型 ====================
    public static final String CAMERA_TYPE_VISIBLE = "VISIBLE";
    public static final String CAMERA_TYPE_INFRARED = "INFRARED";
}
