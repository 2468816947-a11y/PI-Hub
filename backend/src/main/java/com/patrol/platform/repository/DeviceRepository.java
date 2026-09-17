package com.patrol.platform.repository;

import com.patrol.platform.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设备台账 Repository。
 * 唯一索引 deviceId 由 @Id 注解自动创建。
 * 业务约束: 删除设备前需确保无进行中任务(Controller/Service 层校验)。
 */
@Repository
public interface DeviceRepository extends MongoRepository<Device, String> {

    /**
     * 按类型查询设备列表(分页)。
     */
    Page<Device> findByDeviceType(String deviceType, Pageable pageable);

    /**
     * 按状态查询设备列表(分页)。
     */
    Page<Device> findByStatus(String status, Pageable pageable);

    /**
     * 按类型和状态组合查询(分页)。
     */
    Page<Device> findByDeviceTypeAndStatus(String deviceType, String status, Pageable pageable);

    /**
     * 按区域模糊查询(分页), MongoDB 正则匹配。
     */
    Page<Device> findByAreaContainingIgnoreCase(String area, Pageable pageable);

    /**
     * 按类型+区域+状态组合查询(分页), 用于设备列表多条件筛选。
     */
    Page<Device> findByDeviceTypeAndStatusAndAreaContainingIgnoreCase(
            String deviceType, String status, String area, Pageable pageable);

    /**
     * 按设备编号精确查询(存在性检查)。
     */
    Optional<Device> findByDeviceId(String deviceId);

    /**
     * 判断设备是否存在。
     */
    boolean existsByDeviceId(String deviceId);

    /**
     * 统计在线设备数量。
     */
    long countByStatus(String status);

    /**
     * 按状态查询设备列表(无分页, 用于离线检测全量扫描)。
     * 仿真规模 ≤ 10 台设备, 全表扫描安全; 大规模场景应改用 Mongo $where 或 Indexed 查询。
     */
    List<Device> findByStatus(String status);

    /**
     * 按状态分页查询(用于设备管理 API)。
     */
    Page<Device> findByStatus(String status, Pageable pageable);
}
