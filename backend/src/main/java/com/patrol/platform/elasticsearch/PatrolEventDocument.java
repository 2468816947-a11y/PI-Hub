package com.patrol.platform.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

/**
 * 巡检事件 ES 文档(patrol-event 索引)。
 * <p>
 * 文档对照(架构文档 §6.3):
 * - 检索维度字段全部 keyword 精确匹配;
 * - description 走 ik_max_word 中文分词(目前 Spring Data ES 不直接支持 IK,
 *   description 存 text 类型, 实际分词器由 ES 插件/索引设置决定);
 * - position 为 geo_point 类型, 支撑地理半径/矩形检索;
 * - eventTime 按 ISO8601 格式存储。
 * <p>
 * 注意: 经纬度对外 {lng, lat}, ES 内部 geo_point 需 {lat, lon}, 由后端写入时转换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "patrol-event")
public class PatrolEventDocument {

    /**
     * 事件编号, 如 E-20260911-001。
     * 文档字段: eventId
     */
    @Id
    private String eventId;

    /**
     * 设备编号。
     * 文档字段: deviceId (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String deviceId;

    /**
     * 设备类型: UAV / ROBOT_DOG。
     * 文档字段: deviceType (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String deviceType;

    /**
     * 设备名称。
     * 文档字段: deviceName (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String deviceName;

    /**
     * 事件类型: IMAGE / THERMAL / SENSOR / ALARM。
     * 文档字段: eventType (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String eventType;

    /**
     * 告警类型: TEMP_OVER / BATTERY_LOW / OFFLINE / FAULT(仅 ALARM 事件有值)。
     * 文档字段: alarmType (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String alarmType;

    /**
     * 告警等级: CRITICAL / MAJOR / MINOR(仅 ALARM 事件有值)。
     * 文档字段: alarmLevel (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String alarmLevel;

    /**
     * 关联任务编号。
     * 文档字段: taskId (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String taskId;

    /**
     * 巡检区域。
     * 文档字段: area (keyword)
     */
    @Field(type = FieldType.Keyword)
    private String area;

    /**
     * 实测温度(THERMAL 事件有值)。
     * 文档字段: temperature (double)
     */
    @Field(type = FieldType.Double)
    private Double temperature;

    /**
     * 告警阈值(TEMP_OVER 事件有值)。
     * 文档字段: threshold (double)
     */
    @Field(type = FieldType.Double)
    private Double threshold;

    /**
     * 地理位置(geo_point), 由后端将 {lng, lat} 转换为 {lat, lon} 写入。
     * 文档字段: position (geo_point)
     */
    @GeoPointField
    private GeoPoint position;

    /**
     * 事件发生时间(ISO8601)。
     * 文档字段: eventTime (date)
     * <p>
     * pattern 使用 strict_date_optional_time, 与 ES 默认 date 解析兼容,
     * 支持毫秒精度("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")。
     */
    @Field(type = FieldType.Date, pattern = "uuuu-MM-dd'T'HH:mm:ss[.SSS]XXX")
    private String eventTime;

    /**
     * 事件描述, 走 ik_max_word 中文分词检索(架构 §6.3)。
     * <p>
     * 已验证 ES 镜像预装 analysis-ik 插件:
     * <pre>docker exec elasticsearch bin/elasticsearch-plugin list</pre>
     * 输出含 analysis-ik, 可使用 ik_max_word。索引初始化时通过自定义 mapping JSON 应用,
     * 启动阶段 PatrolEventIndexInitializer 负责创建。
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_max_word")
    private String description;
}
