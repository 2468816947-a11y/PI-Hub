#!/usr/bin/env bash
# =====================================================================
# TC-010 性能粗测脚本: 10 台设备并发上报模拟
# 阶段6 任务4 —— 等仿真器就绪后由测试组启用
# =====================================================================
#
# 触发方式:
#   1. 拉起 6 无人机 + 4 机器狗仿真器(每个独立 JVM 进程, 不同 deviceId)
#   2. 仿真器连 localhost:9092(宿主 Kafka), 持续发 REGISTER + HEARTBEAT
#   3. 任务触发 THERMAL / IMAGE / SENSOR 数据上报
#
# 此处仅占位, 仿真组按 simulator/ 模块的启动参数 --count=10 --type=DRONE
# 即可以批量上线 10 个设备上报源。
#
# 监听指标:
# =====================================================================

set -e

# 1. Kafka consumer lag(应接近 0, 不持续积压)
echo "=== Kafka consumer lag ==="
docker exec kafka kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
    --describe --group patrol-backend-group 2>/dev/null || \
    docker exec patrol-kafka-1 kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
        --describe --group patrol-backend-group

# 2. ES 文档数(应持续增长)
echo
echo "=== ES 巡检事件数(应与上报一致) ==="
curl -s 'http://localhost:9200/patrol-event/_count?pretty'

# 3. MongoDB 集合计数(应反映告警 / 设备 / 任务)
echo
echo "=== MongoDB 集合计数 ==="
docker exec mongodb mongosh -u patrol -p patrol123 --authenticationDatabase patrol patrol --quiet --eval '
[
    { name: "device",       n: db.device.countDocuments() },
    { name: "patrol_task",  n: db.patrol_task.countDocuments() },
    { name: "alarm",        n: db.alarm.countDocuments() },
    { name: "hdfs_file",    n: db.hdfs_file.countDocuments() }
]
'

# 4. ES 健康
echo
echo "=== ES 健康 ==="
curl -s 'http://localhost:9200/_cluster/health?pretty' \
  | python -c "import sys,json; d=json.load(sys.stdin); print('status:', d['status'], '| nodes:', d['number_of_nodes'])"

echo
echo "=== 后端实例日志最近 50 行(观察 ERROR) ==="
docker logs backend-app-1 --tail 50 2>&1 | grep -iE "error|exception" || echo "(无 ERROR)"
