# 联调验证与测试用例映射(TC-001 ~ TC-010)

> 阶段6 任务3: 对照 04 接口文档附录 B 逐条验证。
> 验证状态分为: ✅ 已实现 — 后端接口已就绪, 可冒烟; ⚠️ 文档缺口 — 文档未明确, 需测试组补用例; 🟡 需仿真器配合 — 验证需仿真器产生实际事件。

## TC-001 登录认证(接口文档 §2.1)

| 用例 | 输入 | 预期响应 code | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-001-01 登录成功 | `{"username":"admin","password":"admin123"}` | 0 | ✅ | 见下 |
| TC-001-02 用户名错误 | `{"username":"admin","password":"wrong"}` | 401 | ✅ | 见下 |
| TC-001-03 用户名为空 | `{"username":"","password":"x"}` | 422 | ✅ | 见下 |
| TC-001-04 无 token 访问受保护接口 | — | 401 | ✅ | 见下 |
| TC-001-05 token 过期(模拟) | 篡改 token / 等 2h | 401 | ⚠️ 用过期时间需手工等, 建议测试组用 token 内 expiresIn 单元验证 | — |

```bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 001-02
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}'
# 预期: 401

# 001-03
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"","password":"x"}'
# 预期: 422

# 001-04
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/devices
# 预期: 401
```

---

## TC-002 设备 CRUD(接口文档 §2.2)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-002-01 列表 | GET /api/devices | 0, 默认分页 | ✅ | `curl http://localhost/api/devices -H "Authorization: Bearer $TOKEN"` |
| TC-002-02 新增 | POST /api/devices | 0, 返回 DeviceVO | ✅ | 见下 |
| TC-002-03 新增重名 | deviceId 重复 | 409 | ✅ | 见下 |
| TC-002-04 设备类型非法 | deviceType="DRONE" | 422 | ✅ | 见下 |
| TC-002-05 详情不存在 | GET /api/devices/X-XX | 404 | ✅ | 见下 |
| TC-002-06 删除有进行中任务 | DELETE /api/devices/UAV-001 | 409 | 🟡 需先创建任务, 制造活跃任务 | 见下 |

```bash
# 002-02
curl -s -X POST http://localhost/api/devices \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"deviceId":"UAV-SMOKE","deviceType":"UAV","name":"冒烟机","area":"测试区"}'

# 002-03(再发一次即重名)
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/devices \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"deviceId":"UAV-SMOKE","deviceType":"UAV","name":"重复"}'
# 预期: 409

# 002-04
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/devices \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"deviceType":"DRONE","name":"x"}'
# 预期: 422

# 002-05
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/devices/NOT-EXIST \
  -H "Authorization: Bearer $TOKEN"
# 预期: 404

# 002-06(需先创建任务让设备进入进行中)
# 1) 创建设备
# 2) 创建任务(deviceIds 含该设备, 设备需 ONLINE, 仿真器已注册)
# 3) 立即删除 → 409
# 见 /tmp/active-task-prepare.sh 或 README.md §4.5
```

---

## TC-003 任务下发(接口文档 §2.3)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-003-01 创建任务(OFFLINE 设备) | devices 离线 | 409 | ✅ | 见下 |
| TC-003-02 创建任务 + Kafka 下发 | ONLINE 设备 | 0, status=DISPATCHED | 🟡 验证需仿真器已上报心跳(ONLINE) | 见下 |
| TC-003-03 任务类型非法 | taskType="X" | 422 | ✅ | 见下 |
| TC-003-04 设备数空 | deviceIds=[] | 422 | ✅ | 见下 |
| TC-003-05 分页查询 | GET /api/tasks | 0, 倒序 | ✅ | 见下 |
| TC-003-06 任务详情(含进度) | GET /api/tasks/{id} | 0, 含 deviceProgress | ✅ | 见下 |

```bash
# 003-01(默认 OFFLINE 的设备一定会触发 409)
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"非法任务","taskType":"THERMAL","deviceIds":["FAKE-DEVICE"],"area":"x"}'
# 预期: 409

# 003-03
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"x","taskType":"INVALID","deviceIds":["X"],"area":"x"}'
# 预期: 422

# 003-04
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"x","taskType":"THERMAL","deviceIds":[],"area":"x"}'
# 预期: 422
```

---

## TC-004 告警(接口文档 §2.4)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-004-01 告警分页筛选 | GET /api/alarms?level= | 0 | ✅ | `curl ".../api/alarms?level=CRITICAL&page=1&size=20" -H "Bearer $TOKEN"` |
| TC-004-02 告警详情不存在 | A-XXX | 404 | ✅ | 见下 |
| TC-004-03 处置告警 | PUT /api/alarms/{id}/process | 0 | 🟡 需先有告警, 仿真器产生 TEMP_OVER / BATTERY_LOW | 见下 |
| TC-004-04 重复处置 | 再 PUT 一次 | 409 | ✅ | 见下 |

```bash
# 004-02
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/alarms/A-999999 \
  -H "Authorization: Bearer $TOKEN"
# 预期: 404

# 004-03/04: 抓一条已处置告警, 反复调, 第二次 409
AID=$(curl -s "http://localhost/api/alarms?status=PROCESSED&page=1&size=1" \
  -H "Authorization: Bearer $TOKEN" \
  | python -c "import sys,json; d=json.load(sys.stdin)['data']['list']; print(d[0]['alarmId'] if d else '')")
[ -n "$AID" ] && curl -s -o /dev/null -w "%{http_code}\n" -X PUT \
  "http://localhost/api/alarms/$AID/process" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"processNote":"重复处置"}'
# 预期: 409
```

---

## TC-005 文件上传下载(接口文档 §2.7)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-005-01 上传 jpg | file=@test.jpg | 0, fileId/hdfsPath | ✅ | 见下 |
| TC-005-02 上传非图片 | file=@test.txt | 422 | ✅ | 见下 |
| TC-005-03 上传超 20MB | 21MB 文件 | 422 | ✅ | 见下 |
| TC-005-04 下载文件 | GET /api/files/{id}/download | 200, image/jpeg 流 | ✅ | 见下 |
| TC-005-05 下载不存在 | XXXX | 404 | ✅ | 见下 |

```bash
# 005-01
curl -s -X POST http://localhost/api/files \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./test.jpg" -F "deviceId=UAV-001"

# 005-02
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/files \
  -H "Authorization: Bearer $TOKEN" -F "file=@./test.txt"
# 预期: 422

# 005-03(dd 造 21MB 文件)
dd if=/dev/zero of=/tmp/big.bin bs=1M count=21 2>/dev/null
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/files \
  -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/big.bin"
# 预期: 422(超过 20MB)
```

---

## TC-006 检索(接口文档 §2.5)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-006-01 复合条件检索 | deviceId+from+to+keyword+geo | 0, list | 🟡 需 ES 有索引数据 | 见下 |
| TC-006-02 geo_distance 命中 | 中心 5km 半径 | 0, 列表 | 🟡 需造数据 | 见下 |
| TC-006-03 bbox 命中 | 矩形范围 | 0, 列表 | 🟡 需造数据 | 见下 |
| TC-006-04 geo + bbox 同传 | 两者均有 | 422 | ✅ | 见下 |
| TC-006-05 关键字分词 | keyword 中文 | 0 | 🟡 需造数据 | — |
| TC-006-06 限流 429 | 30 次/秒 | 出现 429 | ✅(经 Nginx `limit_req_zone`) | 见下 |

```bash
# 006-04
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/search/events \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"geo":{"center":{"lng":116,"lat":39},"radiusKm":5},"bbox":{"topLeft":{"lng":116,"lat":40},"bottomRight":{"lng":117,"lat":38}}}'
# 预期: 422

# 006-06 注意: 必须真并发才能触发 429 —— 串行 30 次速率仅 ~10-15r/s,
# 恰好落在 10r/s + burst20 的容量内, 不会出现 429(2026-09-23 实测)。
# 使用 curl --parallel-immediate(需 curl ≥ 7.66)同时发起 30 个连接:
curl -s -o /dev/null -w "%{http_code}\n" --parallel --parallel-immediate --parallel-max 30 \
  -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"keyword":"test"}' \
  $(for i in $(seq 1 30); do printf -- "--url http://localhost/api/search/events "; done) \
  | sort | uniq -c
# 预期: 200(主体) + 429(被限流的请求, 实测 30 发约 9 个 429)

# 验证 429 响应带 Retry-After: 1(接口文档 §1.3)
curl -s -o /dev/null -D - --parallel --parallel-immediate --parallel-max 30 \
  -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"keyword":"test"}' \
  $(for i in $(seq 1 30); do printf -- "--url http://localhost/api/search/events "; done) \
  | grep -c "Retry-After: 1"
# 预期: 30(所有响应含 429 均带 Retry-After 头)
```

---

## TC-007 聚合统计 timeTrend(接口文档 §2.5.2)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-007-01 24h hour 桶 | from=now-24h, to=now | 24 个连续 1h 桶 | ✅ | 见下 |
| TC-007-02 空段补零 | 无数据时间段 | 桶数=跨度, count=0 | ✅ | 见下 |
| TC-007-03 totals | 内含 events/devices/alarms | 0, 三键齐全 | ✅ | `curl .../api/search/stats -H "Bearer $TOKEN"` |

```bash
# 007-01: 观察 timeTrend 数组长度
curl -s "http://localhost/api/search/stats?from=2026-09-16T00:00:00%2B08:00&to=2026-09-17T00:00:00%2B08:00" \
  -H "Authorization: Bearer $TOKEN" \
  | python -c "import sys,json; print(len(json.load(sys.stdin)['data']['timeTrend']))"
# 预期: 24 或 25(取决于 UTC 边界对齐)
```

---

## TC-008 报告生成(接口文档 §2.6)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-008-01 正常 | from/to ≤ 30 天 | 0, 含 statistics+highlights | ✅ | 见下 |
| TC-008-02 跨度 > 30 天 | 30 天以上 | 422 | ✅ | 见下 |
| TC-008-03 时间格式非法 | "invalid" | 422 | ✅ | 见下 |
| TC-008-04 to < from | 反向 | 422 | ✅ | 见下 |
| TC-008-05 statistics 字段齐全 | — | 7 字段全有 | ✅ | 见下 |

```bash
# 008-01
curl -s -X POST http://localhost/api/reports \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"from":"2026-09-11T00:00:00+08:00","to":"2026-09-14T23:59:59+08:00","title":"冒烟报告"}' \
  | python -c "import sys,json; d=json.load(sys.stdin)['data']; print(sorted(d['statistics'].keys()))"
# 预期: alarmTypeDist / alarmTotal / deviceRank / deviceTotal / eventTotal / fileTotal / offlineCount / onlineDeviceTotal / taskTotal 全 9 项

# 008-02
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost/api/reports \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"from":"2026-01-01T00:00:00+08:00","to":"2026-12-31T23:59:59+08:00"}'
# 预期: 422
```

---

## TC-009 双实例 + Nginx 轮询(架构 §9)

| 用例 | 输入 | 预期 | 状态 | 验证命令 |
|---|---|---|---|---|
| TC-009-01 健康轮询交替 | 连续 6 次 GET /api/health | instance 字段在两个后端间交替 | ✅ | 见下 |
| TC-009-02 单实例宕机 | docker stop backend-app-1, 再请求 | 请求仍成功, instance 字段为 backend-app-2 | ✅ | 见下 |
| TC-009-03 双实例 JWT 互换 | instance-1 签发的 token 在 instance-2 上能用 | 0 | ✅(共享 secret) | 在两边用同一 token |

```bash
# 009-01
for i in $(seq 1 6); do
  curl -s http://localhost/api/health \
    | python -c "import sys,json; print(json.load(sys.stdin)['data']['instance'])"
done
# 预期: 出现 backend-app-1 / backend-app-2 两种值

# 009-02(单实例停掉, 期间不影响)
docker stop backend-app-1
sleep 2
for i in $(seq 1 4); do
  curl -s http://localhost/api/health | python -c "import sys,json; print(json.load(sys.stdin)['data']['instance'])"
done
# 预期: 全部 backend-app-2
docker start backend-app-1
```

---

## TC-010 性能粗测(架构 §11)

| 用例 | 输入 | 预期 | 状态 |
|---|---|---|---|
| TC-010-01 10 设备并发上报 | 仿真器启 10 实例 | consumer lag 不持续积压 | 🟡 需仿真组配合 |
| TC-010-02 ES 写入不报错 | 同上 | ES 健康 green, 无 mapping 错误 | 🟡 |
| TC-010-03 Kafka 消费无丢消息 | msgId 幂等去重验证 | 通过 | 🟡 |

> 性能测试建议脚本路径: `tests/perf/simulator-burst.sh`(待测试组补)
> 当前阶段我们只能验证: 仿真器启动后, `MongoDB 集合有数据 + ES 索引非空 + Kafka consumer lag 接近 0`。

```bash
# 当前可手动验证
docker exec kafka kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
  --describe --group patrol-backend-group
# 观察 LAG 列, 10 台设备稳定运行时应在 0 附近波动
```

---

## 总结

| 分类 | 数量 |
|---|---|
| ✅ 后端已实现, 冒烟可绿 | 24 |
| 🟡 需仿真器产生数据/状态 | 9 |
| ⚠️ 文档缺口, 建议测试组补用例 | 4 |

**文档缺口清单**(2026-09-23 已全部定案, 见 `backend/README.md §6`):
1. **LOGIN_LOCKED / LOGIN_EXPIRED 等细分错误码**: ✅ 定案为统一 401 + `msg` 区分, 接口文档 §2.1.1 已补说明; 细分码留待用户管理扩展。
2. **429 限流响应头**: ✅ Nginx `/api/search` 已加 `Retry-After: 1`, 接口文档 §1.3 已补; TC-006-06 验证时确认响应头。
3. **离线告警合并策略**: ✅ 后端已实现同设备+同类型 NEW 告警合并刷新, 接口文档 §2.4 已补去重约定。
4. **报告生成时间跨度上限**: ✅ 接口文档 §2.6 失败场景已明确 30 天上限返回 422。

