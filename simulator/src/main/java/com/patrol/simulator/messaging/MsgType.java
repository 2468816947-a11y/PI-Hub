package com.patrol.simulator.messaging;

/**
 * 消息类型常量(与后端 KafkaMessage.MsgType / 接口文档附录 A.2 对齐)。
 * <p>
 * 集中维护,避免字面量散落；新增类型时,务必同步更新：
 * <ol>
 *   <li>后端 KafkaMessage.MsgType</li>
 *   <li>接口文档 §A.2</li>
 * </ol>
 */
public final class MsgType {

    /** 设备注册(上报方向) */
    public static final String REGISTER = "REGISTER";
    /** 心跳(上报,5s 周期) */
    public static final String HEARTBEAT = "HEARTBEAT";
    /** 状态/电量/故障变化 */
    public static final String STATUS = "STATUS";
    /** 无人机航拍图像元数据 */
    public static final String IMAGE = "IMAGE";
    /** 机器狗红外测温 */
    public static final String THERMAL = "THERMAL";
    /** 机器狗环境传感器 */
    public static final String SENSOR = "SENSOR";
    /** 任务执行结果(上报) */
    public static final String TASK_RESULT = "TASK_RESULT";
    /** 任务指令(下行,业务服务 → 仿真器) */
    public static final String TASK_COMMAND = "TASK_COMMAND";

    /** 设备类型常量(与接口文档 §1.5 deviceType 一致) */
    public static final String DEVICE_TYPE_UAV = "UAV";
    public static final String DEVICE_TYPE_ROBOT_DOG = "ROBOT_DOG";

    /** 任务类型 */
    public static final String TASK_TYPE_THERMAL = "THERMAL";
    public static final String TASK_TYPE_IMAGE = "IMAGE";
    public static final String TASK_TYPE_SENSOR = "SENSOR";
    public static final String TASK_TYPE_COMPREHENSIVE = "COMPREHENSIVE";

    /** 任务结果状态 */
    public static final String TASK_STATUS_FINISHED = "FINISHED";
    public static final String TASK_STATUS_FAILED = "FAILED";

    /** 相机类型 */
    public static final String CAMERA_VISIBLE = "VISIBLE";
    public static final String CAMERA_INFRARED = "INFRARED";

    private MsgType() {}
}
