package com.patrol.platform.messaging;

import com.patrol.platform.common.BusinessConstants;
import com.patrol.platform.entity.HdfsFile;
import com.patrol.platform.kafka.KafkaMessage;
import com.patrol.platform.service.AlarmService;
import com.patrol.platform.service.DeviceService;
import com.patrol.platform.service.HdfsFileService;
import com.patrol.platform.service.PatrolEventService;
import com.patrol.platform.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 消息分发器(架构 §5: 按 msgType 分发到对应业务处理器)。
 * <p>
 * 阶段 3 完成全部业务接线:
 * <ul>
 *   <li>REGISTER / HEARTBEAT / STATUS → DeviceService</li>
 *   <li>STATUS → AlarmService.checkStatusAlarms(BATTERY_LOW / FAULT)</li>
 *   <li>IMAGE → HdfsFileService(可选写 HDFS) + PatrolEventService(写 ES)</li>
 *   <li>THERMAL → PatrolEventService(写 ES) + TaskService(进度) + AlarmService(超温)</li>
 *   <li>SENSOR → PatrolEventService(写 ES)</li>
 *   <li>TASK_RESULT → TaskService.completeTask()</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private final DeviceService deviceService;
    private final AlarmService alarmService;
    private final TaskService taskService;
    private final HdfsFileService hdfsFileService;
    private final PatrolEventService patrolEventService;

    public void dispatch(KafkaMessage msg, Acknowledgment ack) {
        try {
            switch (String.valueOf(msg.getMsgType())) {
                case KafkaMessage.MsgType.REGISTER:
                    deviceService.registerOrUpdate(msg);
                    break;
                case KafkaMessage.MsgType.HEARTBEAT:
                    deviceService.updateHeartbeat(msg);
                    break;
                case KafkaMessage.MsgType.STATUS:
                    deviceService.updateStatus(msg);
                    alarmService.checkStatusAlarms(msg);
                    break;
                case KafkaMessage.MsgType.IMAGE: {
                    Optional<HdfsFile> saved = hdfsFileService.saveImageFromMessage(msg);
                    boolean hasFile = saved.isPresent();
                    String hdfsPath = saved.map(HdfsFile::getHdfsPath).orElse(null);
                    patrolEventService.indexImage(msg, hasFile, hdfsPath);
                    break;
                }
                case KafkaMessage.MsgType.THERMAL: {
                    String taskId = msg.getData() != null
                            ? asString(msg.getData().get("taskId"))
                            : null;
                    Double threshold = taskId == null ? null
                            : taskService.getContext(taskId)
                                    .map(ctx -> ctx.tempThreshold)
                                    .orElse(BusinessConstants.DEFAULT_TEMP_THRESHOLD);
                    taskService.recordThermalPoint(msg);
                    patrolEventService.indexThermal(msg);
                    alarmService.checkThermalAlarm(msg, threshold);
                    break;
                }
                case KafkaMessage.MsgType.SENSOR:
                    taskService.recordDataPoint(msg);
                    patrolEventService.indexSensor(msg);
                    break;
                case KafkaMessage.MsgType.TASK_RESULT:
                    taskService.completeTask(msg);
                    break;
                case KafkaMessage.MsgType.TASK_COMMAND:
                    log.debug("[TASK_COMMAND] deviceId={}, taskId={}",
                            msg.getDeviceId(),
                            msg.getData() != null ? msg.getData().get("taskId") : null);
                    break;
                default:
                    log.warn("Unknown msgType={}, msgId={}", msg.getMsgType(), msg.getMsgId());
            }
        } catch (Exception e) {
            log.error("Dispatch failed: msgId={}, msgType={}, deviceId={}",
                    msg.getMsgId(), msg.getMsgType(), msg.getDeviceId(), e);
        }
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
