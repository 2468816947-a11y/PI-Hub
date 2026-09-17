package com.patrol.platform.repository;

import com.patrol.platform.entity.HdfsFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * HDFS 文件元数据 Repository。
 * 唯一索引 fileId 由 @Id 注解自动创建。
 * 额外索引: deviceId、taskId、hdfsPath(见实体 @Indexed)。
 */
@Repository
public interface HdfsFileRepository extends MongoRepository<HdfsFile, String> {

    /**
     * 按设备编号查询文件列表(分页), 按上传时间倒序。
     */
    Page<HdfsFile> findByDeviceIdOrderByUploadTimeDesc(String deviceId, Pageable pageable);

    /**
     * 按任务编号查询文件列表(分页)。
     */
    Page<HdfsFile> findByTaskIdOrderByUploadTimeDesc(String taskId, Pageable pageable);

    /**
     * 按文件编号精确查询。
     */
    HdfsFile findByFileId(String fileId);

    /**
     * 查询指定 HDFS 路径的文件(去重判断)。
     */
    HdfsFile findByHdfsPath(String hdfsPath);

    /**
     * 判断指定文件是否存在。
     */
    boolean existsByFileId(String fileId);

    /**
     * 统计指定时间区间内上传的文件数量(报告聚合用)。
     */
    long countByUploadTimeBetween(OffsetDateTime from, OffsetDateTime to);
}
