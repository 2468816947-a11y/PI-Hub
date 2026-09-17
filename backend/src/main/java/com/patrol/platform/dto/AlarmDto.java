package com.patrol.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.patrol.platform.entity.Alarm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 告警相关 DTO。
 */
public final class AlarmDto {

    private AlarmDto() {}

    /**
     * 告警处置请求(接口文档 §2.4.3)。
     */
    public record AlarmProcessRequest(
            @NotBlank(message = "processNote 不能为空")
            @Size(max = 500, message = "processNote 不能超过 500 字")
            String processNote
    ) {}

    /**
     * 告警 VO。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AlarmVO(
            String alarmId,
            String deviceId,
            String taskId,
            String alarmType,
            String level,
            Double value,
            Double threshold,
            PositionDto position,
            String description,
            String status,
            OffsetDateTime createTime,
            OffsetDateTime processTime,
            String processNote
    ) {
        public static AlarmVO from(Alarm a) {
            PositionDto pos = a.getPosition() != null
                    ? new PositionDto(a.getPosition().getLng(), a.getPosition().getLat())
                    : null;
            return new AlarmVO(
                    a.getAlarmId(),
                    a.getDeviceId(),
                    a.getTaskId(),
                    a.getAlarmType(),
                    a.getLevel(),
                    a.getValue(),
                    a.getThreshold(),
                    pos,
                    a.getDescription(),
                    a.getStatus(),
                    a.getCreateTime(),
                    a.getProcessTime(),
                    a.getProcessNote()
            );
        }
    }
}
