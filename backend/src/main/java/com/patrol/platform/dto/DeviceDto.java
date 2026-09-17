package com.patrol.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.patrol.platform.entity.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

/**
 * 设备相关 DTO 集合。
 * - DeviceCreateRequest: POST /api/devices 请求体
 * - DeviceUpdateRequest: PUT /api/devices/{id} 请求体(部分更新)
 * - DeviceVO: 接口返回对象
 */
public final class DeviceDto {

    private DeviceDto() {}

    /**
     * 创建设备请求(接口文档 §2.2.2)。
     */
    public record DeviceCreateRequest(
            /** 设备编号, 可选; 不传则后端生成 */
            String deviceId,
            @NotBlank(message = "deviceType 不能为空")
            @Pattern(regexp = "^(UAV|ROBOT_DOG)$", message = "deviceType 必须是 UAV 或 ROBOT_DOG")
            String deviceType,
            @NotBlank(message = "name 不能为空")
            String name,
            String model,
            String area,
            /** {lng, lat} 缺省 {0, 0} */
            PositionDto position
    ) {}

    /**
     * 更新设备请求(接口文档 §2.2.3), 仅部分字段可改。
     */
    public record DeviceUpdateRequest(
            String name,
            String model,
            String area,
            PositionDto position,
            String faultCode
    ) {}

    /**
     * 设备 VO(接口文档 §2.2 设备对象)。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeviceVO(
            String deviceId,
            String deviceType,
            String name,
            String model,
            String area,
            String status,
            Integer battery,
            String faultCode,
            PositionDto position,
            OffsetDateTime lastHeartbeat,
            OffsetDateTime registerTime
    ) {
        public static DeviceVO from(Device d) {
            PositionDto pos = d.getPosition() != null
                    ? new PositionDto(d.getPosition().getLng(), d.getPosition().getLat())
                    : null;
            return new DeviceVO(
                    d.getDeviceId(),
                    d.getDeviceType(),
                    d.getName(),
                    d.getModel(),
                    d.getArea(),
                    d.getStatus(),
                    d.getBattery(),
                    d.getFaultCode(),
                    pos,
                    d.getLastHeartbeat(),
                    d.getRegisterTime()
            );
        }
    }

    /**
     * 坐标(对外统一 {lng, lat}, 接口文档 §1.5)。
     */
    public record PositionDto(Double lng, Double lat) {}
}
