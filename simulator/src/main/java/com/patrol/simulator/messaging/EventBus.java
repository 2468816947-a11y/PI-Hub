package com.patrol.simulator.messaging;

import com.patrol.simulator.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 仿真器侧消息门面,聚合所有上报消息的构造 + 发送。
 * <p>
 * 设计动机: 设备仿真器/任务执行器只关心业务,不必重复拼装信封、UUID、ISO 时间;
 * 由本门面统一负责"构造信封 → 选 Topic → 调生产者"。
 * <p>
 * Topic 命名全部来自配置(架构 §5 + 接口文档 §A.2 一一对应)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBus {

    private final SimulatorProducer producer;
    private final SimulatorProperties properties;

    /* ============================================================
     * 设备注册(REGISTER, §A.2)
     * 载荷: {name, model, area, lng, lat}
     * ============================================================ */
    public void sendRegister(String deviceId, String deviceType, Map<String, Object> data) {
        send(properties.getSimulator().getTopics().getRegister(), deviceId,
                MsgType.REGISTER, deviceId, deviceType, data);
    }

    /* ============================================================
     * 心跳(HEARTBEAT, §A.2)
     * 载荷: {battery}
     * ============================================================ */
    public void sendHeartbeat(String deviceId, String deviceType, int battery) {
        send(properties.getSimulator().getTopics().getHeartbeat(), deviceId,
                MsgType.HEARTBEAT, deviceId, deviceType,
                Map.of("battery", battery));
    }

    /* ============================================================
     * 状态上报(STATUS, §A.2)
     * 载荷: {online, battery, faultCode, mode}
     * ============================================================ */
    public void sendStatus(String deviceId, String deviceType,
                           boolean online, int battery, String faultCode, String mode) {
        send(properties.getSimulator().getTopics().getStatus(), deviceId,
                MsgType.STATUS, deviceId, deviceType,
                Map.of(
                        "online", online,
                        "battery", battery,
                        "faultCode", faultCode == null ? "" : faultCode,
                        "mode", mode
                ));
    }

    /* ============================================================
     * 无人机航拍图像(IMAGE, §A.2)
     * 载荷: {imageId, cameraType, lng, lat, altitude, taskId, fileName, fileSize, imageBase64?}
     * ============================================================ */
    public void sendImage(String deviceId, Map<String, Object> data) {
        send(properties.getSimulator().getTopics().getDroneImage(), deviceId,
                MsgType.IMAGE, deviceId, MsgType.DEVICE_TYPE_UAV, data);
    }

    /* ============================================================
     * 机器狗红外测温(THERMAL, §A.2)
     * 载荷: {pointId, targetDevice, temperature, ambientTemp, lng, lat, taskId?}
     * ============================================================ */
    public void sendThermal(String deviceId, Map<String, Object> data) {
        send(properties.getSimulator().getTopics().getDogThermal(), deviceId,
                MsgType.THERMAL, deviceId, MsgType.DEVICE_TYPE_ROBOT_DOG, data);
    }

    /* ============================================================
     * 机器狗环境传感器(SENSOR, §A.2)
     * 载荷: {temperature, humidity, gas, lng, lat}
     * ============================================================ */
    public void sendSensor(String deviceId, Map<String, Object> data) {
        send(properties.getSimulator().getTopics().getDogSensor(), deviceId,
                MsgType.SENSOR, deviceId, MsgType.DEVICE_TYPE_ROBOT_DOG, data);
    }

    /* ============================================================
     * 任务执行结果(TASK_RESULT, §A.2)
     * 载荷: {taskId, status, detail, finishTime}
     * 注意: 分区键用 taskId(同任务多设备回执落到同分区便于业务聚合)
     * ============================================================ */
    public void sendTaskResult(String taskId, Map<String, Object> data) {
        // 分区键:taskId(若 null 则用 deviceId,理论不会出现)
        String key = taskId != null ? taskId : SimulatorProducer.DEFAULT_PARTITION_KEY;
        // 信封 deviceId 字段也写 taskId(后端消费时会按消息内容更新任务状态; 历史上有后端取 envelope.deviceId 作为日志维度)
        send(properties.getSimulator().getTopics().getTaskResult(), key,
                MsgType.TASK_RESULT, key, "", data);
    }

    /* ============================================================
     * 通用发送:构造信封 + 投递
     * ============================================================ */
    private void send(String topic, String partitionKey, String msgType,
                      String envelopeDeviceId, String deviceType, Map<String, Object> data) {
        MessageEnvelope envelope = MessageEnvelope.builder()
                .msgId(UUID.randomUUID().toString())
                .msgType(msgType)
                .deviceId(envelopeDeviceId)
                .deviceType(deviceType == null ? "" : deviceType)
                .timestamp(OffsetDateTime.now().toString())
                .data(data)
                .build();

        producer.send(topic, partitionKey, envelope);

        // INFO 级别便于演示时直观看到消息流(产线可调成 DEBUG)
        log.info("[MSG] topic={} key={} type={} device={} dataKeys={}",
                topic, partitionKey, msgType, envelopeDeviceId,
                data == null ? "null" : data.keySet());
    }
}
