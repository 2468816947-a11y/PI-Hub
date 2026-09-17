package com.patrol.platform.repository;

import com.patrol.platform.entity.PatrolTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 巡检任务 Repository。
 * 唯一索引 taskId 由 @Id 注解自动创建。
 */
@Repository
public interface PatrolTaskRepository extends MongoRepository<PatrolTask, String> {

    /**
     * 按状态查询任务列表(分页), 按创建时间倒序。
     */
    Page<PatrolTask> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);

    /**
     * 按任务类型查询(分页)。
     */
    Page<PatrolTask> findByTaskTypeOrderByCreateTimeDesc(String taskType, Pageable pageable);

    /**
     * 按状态+类型组合查询(分页)。
     */
    Page<PatrolTask> findByStatusAndTaskTypeOrderByCreateTimeDesc(
            String status, String taskType, Pageable pageable);

    /**
     * 按任务编号精确查询。
     */
    PatrolTask findByTaskId(String taskId);

    /**
     * 查询指定设备关联的任务(用于设备删除前校验是否存在进行中任务)。
     * 只要任务状态为 CREATED/DISPATCHED/RUNNING 任一即视为进行中。
     */
    List<PatrolTask> findByDeviceIdsContainingAndStatusIn(
            String deviceId, List<String> activeStatuses);

    /**
     * 统计指定状态的任务数量。
     */
    long countByStatus(String status);

    /**
     * 统计指定时间区间内的任务数量(报告聚合用)。
     */
    long countByCreateTimeBetween(OffsetDateTime from, OffsetDateTime to);
}
