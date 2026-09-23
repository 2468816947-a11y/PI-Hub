# Patrol Backend —— 业务服务

> 电力场站设备巡检数据集成平台 · 后端服务
> 技术栈: Java 17 + Spring Boot 3.2 + Kafka + MongoDB + Elasticsearch + HDFS(WebHDFS)
> 对接契约: `04-接口文档 v1.2`(前后端与仿真器的最终契约)

---

## 1. 启动步骤

### 1.1 本地 IDE 启动(快速联调)
```bash
# 1) 启动中间件(Kafka/Mongo/ES/HDFS), 见 deploy/README.md
cd deploy && docker compose up -d kafka kafka-init mongodb elasticsearch es-init hdfs-namenode hdfs-datanode

# 2) 启动本机后端(SPRING_PROFILES_ACTIVE=local 走 localhost)
mvn -q clean spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=local"
# 或 IDE 中把 PatrolApplication 启动环境变量设为 SPRING_PROFILES_ACTIVE=local
```

### 1.2 容器内启动(完整联调 + 双实例)
```bash
cd deploy && docker compose up -d --build
# 双实例由 docker-compose.yml 控制, 分别监听 8080 / 8081
```

### 1.3 健康检查
```bash
curl -s http://localhost/api/health
# 预期: {"code":0, "data":{"instance":"backend-app-1", "time":"..."}}
```

---

## 2. 关键配置项

| 配置键 | 环境变量 | 缺省值 | 说明 |
|---|---|---|---|
| `spring.data.mongodb.uri` | `SPRING_DATA_MONGODB_URI` | `mongodb://patrol:patrol123@mongodb:27017/patrol?authSource=patrol` | MongoDB 业务库连接串 |
| `spring.elasticsearch.uris` | `PATROL_ES_URIS` | `http://elasticsearch:9200` | ES 入口 |
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker(容器内用服务名, 宿主机用 `localhost:9092`) |
| `spring.kafka.consumer.group-id` | `PATROL_KAFKA_GROUP_ID` | `patrol-backend-group` | 消费者组(双实例共用) |
| `patrol.hdfs.webhdfs-base` | `PATROL_HDFS_WEBHDFS_BASE` | `http://namenode:9870` | WebHDFS 入口 |
| `patrol.hdfs.user` | `PATROL_HDFS_USER` | `dr.who` | WebHDFS 用户代理 |
| `patrol.jwt.secret` | `PATROL_JWT_SECRET` | dev key(必须改) | JWT 签名密钥(生产 ≥ 256bit) |
| `patrol.jwt.expire-seconds` | `PATROL_JWT_EXPIRE` | `7200` | JWT 有效期(秒) |
| `patrol.instance.name` | `INSTANCE_NAME` | `${HOSTNAME}` | 实例名, `/api/health` 返回 |
| `server.port` | `SERVER_PORT` | `8080` | 后端 HTTP 端口(双实例分别 8080/8081) |
| 8 个 Topic 名 | `PATROL_TOPIC_*` | 见 `application.yml` | 全部可在运维层重命名 |
| `spring.servlet.multipart.max-file-size` | — | `20MB` | 文件上传上限(接口文档 §2.7) |

### Profile 说明
| Profile | 用途 | 中间件地址 |
|---|---|---|
| `local` | 本机 IDE 联调 | `localhost:*` |
| `container` | Docker 容器部署 | 服务名(如 `kafka`/`mongodb`) |

---

## 3. 目录结构(模块分层)

```
com.patrol.platform
├── PatrolApplication              # 启动类, @EnableScheduling(阶段3离线检测)
├── common/
│   ├── ApiResponse                # 统一响应信封 {code, msg, data}
│   ├── BusinessException          # 业务异常, 携带 ErrorCode
│   ├── ErrorCode                  # 错误码枚举(401/403/404/409/422/500)
│   ├── GlobalExceptionHandler     # @RestControllerAdvice 全局拦截
│   ├── BusinessConstants          # 业务状态/类型枚举常量
│   └── IdGenerator                # 业务编号生成(T-001/A-001/E-001/img-xxx)
├── config/
│   ├── KafkaTopicProperties       # 8 个 Topic 名注入
│   ├── KafkaTopicConfig           # Topic 自动注册(@Bean)
│   ├── SecurityConfig             # Spring Security + JWT 白名单
│   └── MultipartConfig            # 20MB 上传限制
├── security/
│   ├── JwtUtil                    # JWT 签发/解析
│   ├── JwtAuthFilter              # 鉴权过滤器(无状态, 禁 HttpSession)
│   ├── RestAuthenticationEntryPoint # 401 JSON 响应
│   └── RestAccessDeniedHandler    # 403 JSON 响应
├── controller/                    # 19 个 REST 接口
│   ├── AuthController             # POST /api/auth/login
│   ├── HealthController           # GET  /api/health
│   ├── DeviceController           # GET/POST/PUT/DELETE/GET /api/devices(5)
│   ├── TaskController             # POST/GET/GET /api/tasks(3)
│   ├── AlarmController            # GET/GET/PUT /api/alarms(3)
│   ├── FileController             # POST/GET/GET /api/files(3)
│   ├── SearchController           # POST/GET /api/search(2)
│   └── ReportController           # POST /api/reports(1)
├── dto/                           # 入参/出参(record, jakarta validation)
├── entity/                        # MongoDB + ES 实体
├── repository/                    # MongoRepository + Elasticsearch Repository
├── service/                       # 业务 Service(Device/Task/Alarm/Hdfs/Event/EventSearch/Report)
├── scheduler/
│   └── DeviceOfflineScheduler     # @Scheduled 5s/次 离线检测
├── messaging/
│   ├── KafkaMessageListener       # 8 个 @KafkaListener
│   ├── MessageDeduplicationService # msgId 幂等去重
│   ├── MessageDispatcher          # 按 msgType 路由到 Service
│   └── TaskCommandProducer        # Kafka Producer(TASK_COMMAND 指令下发)
├── storage/
│   └── HdfsClient                 # WebHDFS REST 客户端
└── elasticsearch/
    ├── PatrolEventDocument        # ES 文档(@Document, ik_max_word)
    ├── PatrolEventRepository      # ES Repository
    └── PatrolEventIndexInitializer # 启动期显式创建索引 + mapping
```

---

## 4. 冒烟脚本(接口文档附录 C 全绿)

### 4.1 登录拿 token
```bash
TOKEN=$(curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
echo "$TOKEN"
```

### 4.2 健康检查(双实例轮询验证)
```bash
for i in $(seq 1 6); do
  curl -s http://localhost/api/health \
    | python -c "import sys,json; print(json.load(sys.stdin)['data']['instance'])"
done
# 预期: backend-app-1 与 backend-app-2 交替出现
```

### 4.3 设备列表
```bash
curl -s "http://localhost/api/devices?page=1&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### 4.4 创建设备
```bash
curl -s -X POST http://localhost/api/devices \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"deviceId":"UAV-099","deviceType":"UAV","name":"冒烟测试机","area":"测试区"}'
```

### 4.5 创建任务(下发)
```bash
curl -s -X POST http://localhost/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name":"2号变压器测温",
    "taskType":"THERMAL",
    "area":"东区",
    "deviceIds":["ROBOTDOG-002"],
    "params":{"points":12,"tempThreshold":80}
  }'
# 预期: data.taskId="T-...", status="DISPATCHED"
```

### 4.6 上传图片(multipart)
```bash
curl -s -X POST http://localhost/api/files \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./test.jpg" -F "deviceId=UAV-001"
```

### 4.7 检索事件
```bash
curl -s -X POST http://localhost/api/search/events \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "deviceId":"ROBOTDOG-002",
    "eventTypes":["THERMAL","ALARM"],
    "from":"2026-09-11T00:00:00+08:00",
    "to":"2026-09-14T23:59:59+08:00",
    "keyword":"变压器",
    "geo":{"center":{"lng":116.397,"lat":39.908},"radiusKm":5},
    "page":1,"size":20
  }'
```

### 4.8 聚合统计
```bash
curl -s "http://localhost/api/search/stats?from=2026-09-11T00:00:00%2B08:00&to=2026-09-14T23:59:59%2B08:00" \
  -H "Authorization: Bearer $TOKEN"
# 预期: totals / alarmTypeDist / deviceRank / timeTrend / areaDist 五项齐全
```

### 4.9 限流验证(检索 10r/s)
```bash
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{http_code} " -X POST \
    http://localhost/api/search/events \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"keyword":"test"}'
done; echo
# 预期: 大部分 200, 部分请求 429
```

---

## 5. 异常码对照表

| code | HTTP | 场景 | 抛出位置 |
|---|---|---|---|
| 0 | 200 | 成功 | Controller |
| 401 | 401 | 未登录 / Token 过期 / 凭证错误 | AuthController / JwtAuthFilter |
| 403 | 403 | 已登录但权限不足(预留) | SecurityConfig |
| 404 | 404 | 资源不存在(device/alarm/task/file) | 各 Service |
| 409 | 409 | deviceId 重复 / 删除有进行中任务的设备 / 告警重复处置 / 任务含离线设备 | DeviceService / AlarmService / TaskService |
| 422 | 422 | @Valid 校验失败 / 必填缺失 / geo 与 bbox 同传 / 时间跨度 > 30 天 | GlobalExceptionHandler / SearchController / ReportService |
| 429 | 429 | 检索/搜索限流触发 | Nginx `limit_req` |
| 500 | 500 | 系统兜底异常 | GlobalExceptionHandler |

---

## 6. 与文档契约的偏离与缺口

| 缺口 | 现状 | 建议 |
|---|---|---|
| 登录失败场景未单独定义错误码 | ✅ 已定案(2026-09-23): 统一 `401 + msg` 区分, 接口文档 §2.1.1 已明确; 细分错误码留待用户管理扩展 | — |
| 限流触发 429 的阈值与重试策略 | ✅ 已定案(2026-09-23): Nginx `/api/search` 限流响应带 `Retry-After: 1`, 接口文档 §1.3 已明确 | 测试组验证 TC-006-06 时确认响应头 |
| 双实例 JWT 一致性 | 共享 secret, 已一致 | 测试组应补 TC-008(实例切换前后 token 复用) |
| `description` 分词器 | 已用 `ik_max_word`(ES 镜像已装 IK 插件); 若 IK 镜像缺失, 启动期索引创建会失败 | 文档应补"索引未创建时的降级策略" |
| 报告生成时间跨度上限 | ✅ 已定案(2026-09-23): 后端硬编码 30 天, 接口文档 §2.6 失败场景已明确 | 测试组应补 TC-009(30 天临界) |
| 离线告警与 STATUS 告警合并策略 | ✅ 已定案(2026-09-23): 后端已实现"同设备同类型 NEW 告警合并刷新", 接口文档 §2.4 已明确 | 测试组验证: 设备反复掉线不重复建告警 |

---

## 7. 单元/集成测试覆盖建议(待测试组补)

- `AuthControllerTest`: 登录成功 / 失败 / 401 路径
- `DeviceServiceTest`: 409 路径(重名/进行中任务) / 404 路径
- `TaskServiceTest`: 409(离线设备) / 422(枚举非法) / Kafka 指令发送验证
- `AlarmServiceTest`: BATTERY_LOW / TEMP_OVER / OFFLINE 三类规则触发
- `PatrolEventSearchServiceTest`: geo_distance / bbox / 互斥 422 / timeTrend 补零
- `ReportServiceTest`: 时间跨度 > 30 天 422 / highlights 排序

