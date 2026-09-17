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
 * HDFS 文件元数据实体。
 * <p>
 * 文档对照(架构文档 §6.2): MongoDB hdfs_file 集合字段完全一致。
 * 记录巡检图片在 HDFS 的存储位置, 与 HDFS 文件一一对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hdfs_file")
public class HdfsFile {

    /**
     * 文件编号(PK), 后端生成如 img-3f2a9c1b。
     * 文档字段: fileId
     */
    @Id
    private String fileId;

    /**
     * 原始文件名。
     * 文档字段: fileName
     */
    private String fileName;

    /**
     * HDFS 完整路径, 如 /patrol/1号变电站/2026-09-11/UAV-001/img-xxx.jpg。
     * 文档字段: hdfsPath
     */
    @Indexed
    private String hdfsPath;

    /**
     * 来源设备编号(可为 null)。
     * 文档字段: deviceId
     */
    @Indexed
    private String deviceId;

    /**
     * 关联任务编号(可为 null)。
     * 文档字段: taskId
     */
    @Indexed
    private String taskId;

    /**
     * 文件大小(字节)。
     * 文档字段: fileSize
     */
    private Long fileSize;

    /**
     * 上传时间。
     * 文档字段: uploadTime
     */
    private OffsetDateTime uploadTime;
}
