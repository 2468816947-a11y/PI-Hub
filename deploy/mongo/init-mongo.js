// =====================================================================
// MongoDB 初始化脚本(容器首次启动时由 /docker-entrypoint-initdb.d 自动执行)
// 与架构文档 §6.2 对齐: 库 patrol, 集合 device / patrol_task / alarm / hdfs_file
// 注意: 业务账号创建在 patrol 库, 连接串必须带 ?authSource=patrol
// =====================================================================

const patrol = db.getSiblingDB('patrol');

// 业务账号(供后端双实例使用)
patrol.createUser({
  user: 'patrol',
  pwd: 'patrol123',
  roles: [{ role: 'readWrite', db: 'patrol' }]
});

// 预建集合
patrol.createCollection('device');
patrol.createCollection('patrol_task');
patrol.createCollection('alarm');
patrol.createCollection('hdfs_file');

// 常用索引
patrol.device.createIndex({ status: 1 });
patrol.device.createIndex({ lastHeartbeat: 1 });                    // 设备离线检测定时扫描
patrol.patrol_task.createIndex({ createTime: -1 });
patrol.alarm.createIndex({ deviceId: 1, createTime: -1 });
patrol.alarm.createIndex({ level: 1, status: 1 });
patrol.hdfs_file.createIndex({ deviceId: 1, uploadTime: -1 });
