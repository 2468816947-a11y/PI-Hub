package com.patrol.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

/**
 * 设备台账实体。
 * <p>
 * 文档对照(架构文档 §6.2): MongoDB device 集合字段完全一致。
 * 唯一索引: deviceId(文档要求), 确保设备注册消息幂等去重。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "device")
public class Device {

    /**
     * 设备编号(PK), 如 UAV-001 / ROBOTDOG-001。
     * 文档字段: deviceId (PK)
     */
    @Id
    private String deviceId;

    /**
     * 设备类型: UAV(无人机) / ROBOT_DOG(机器狗)。
     * 文档字段: deviceType
     */
    private String deviceType;

    /**
     * 设备名称, 如 "无人机1号"。
     * 文档字段: name
     */
    private String name;

    /**
     * 型号, 如 "巡检无人机-M300"。
     * 文档字段: model
     */
    private String model;

    /**
     * 所属区域, 如 "1号变电站·东区"。
     * 文档字段: area
     */
    private String area;

    /**
     * 在线状态: ONLINE / OFFLINE。
     * 权威规则: 收到该设备 HEARTBEAT/STATUS 消息即置 ONLINE;
     * 定时扫描 lastHeartbeat 超 15s 置 OFFLINE。
     * 文档字段: status
     */
    private String status;

    /**
     * 电量百分比 0-100。
     * 文档字段: battery
     */
    private Integer battery;

    /**
     * 故障码, 无故障为空串。
     * 文档字段: faultCode
     */
    private String faultCode;

    /**
     * 设备位置坐标(经纬度)。
     * 文档字段: position { lng, lat }
     */
    private Position position;

    /**
     * 最近心跳时间, 未心跳过为 null。
     * 文档字段: lastHeartbeat
     */
    private OffsetDateTime lastHeartbeat;

    /**
     * 注册时间。
     * 文档字段: registerTime
     */
    private OffsetDateTime registerTime;

    /**
     * 地理位置坐标(嵌套文档)。
     * 注意: 对外 {lng, lat}, ES 内部 geo_point 需 {lat, lon}, 由后端转换。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        /** 经度 */
        private Double lng;
        /** 纬度 */
        private Double lat;
    }
}
