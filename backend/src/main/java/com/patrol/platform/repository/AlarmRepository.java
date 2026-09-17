package com.patrol.platform.repository;

import com.patrol.platform.entity.Alarm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 告警记录 Repository。
 * 唯一索引 alarmId 由 @Id 注解自动创建。
 * 额外索引: deviceId、taskId(见实体 @Indexed)。
 */
@Repository
public interface AlarmRepository extends MongoRepository<Alarm, String> {

    /**
     * 按告警等级分页查询。
     */
    Page<Alarm> findByLevelOrderByCreateTimeDesc(String level, Pageable pageable);

    /**
     * 按告警类型分页查询。
     */
    Page<Alarm> findByAlarmTypeOrderByCreateTimeDesc(String alarmType, Pageable pageable);

    /**
     * 按处置状态分页查询。
     */
    Page<Alarm> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);

    /**
     * 按等级+类型+状态组合查询(分页), 用于告警列表多条件筛选。
     */
    Page<Alarm> findByLevelAndAlarmTypeAndStatusOrderByCreateTimeDesc(
            String level, String alarmType, String status, Pageable pageable);

    /**
     * 按告警编号精确查询。
     */
    Alarm findByAlarmId(String alarmId);

    /**
     * 查询指定设备的所有告警(用于设备详情页)。
     */
    List<Alarm> findByDeviceIdOrderByCreateTimeDesc(String deviceId);

    /**
     * 查询指定任务关联的所有告警。
     */
    List<Alarm> findByTaskIdOrderByCreateTimeDesc(String taskId);

    /**
     * 统计指定时间范围内的告警数量。
     */
    long countByCreateTimeBetween(OffsetDateTime from, OffsetDateTime to);

    /**
     * 统计指定告警类型的数量。
     */
    long countByAlarmType(String alarmType);

    /**
     * 查询未处置告警(用于告警数量 badge)。
     */
    long countByStatus(String status);

    /**
     * 按时间范围查询告警(报告聚合用, 按 createTime 倒序)。
     */
    List<Alarm> findByCreateTimeBetween(OffsetDateTime from, OffsetDateTime to);
}
