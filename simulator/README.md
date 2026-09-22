# 无人机 / 机器狗设备仿真器

> 电力场站设备巡检数据集成平台 —— 设备仿真模块
> 依据《04-接口文档》v1.2 / 《03-架构设计文档》§4 §5 §9 实现

仿真器作为**独立的 Java 进程**运行（默认在宿主机；可选 Docker 容器化），模拟
**6 台无人机 + 4 台机器狗**（共 10 台，可配置）持续向 Kafka 8 个 Topic 上报
**注册 / 心跳 / 状态 / 航拍图像 / 红外测温 / 环境传感器 / 任务结果** 7 类消息，
并订阅 **`patrol.task.command`** 指令 Topic，按指令类型模拟执行后回传任务结果。

---

## 1. 目录结构

```
simulator/
├── pom.xml                          # Maven 构建(Spring Boot 3.2.5 + Java 17)
├── Dockerfile                       # 可选容器化镜像
├── README.md                        # 本文件
├── src/main/java/com/patrol/simulator/
│   ├── PatrolSimulatorApplication.java   # Spring Boot 启动入口
│   ├── config/
│   │   ├── SimulatorProperties.java      # 配置项(注入 application.yml)
│   │   ├── DeviceFactory.java            # 创建 N 台无人机 + M 台机器狗并启动
│   │   ├── StartupBanner.java            # 启动时打印 Banner
│   │   ├── HealthReporter.java           # 每 30s 打印设备/消息状态摘要
│   │   └── DemoFaultInjector.java        # 每 90s 演示性注入故障/低电量
│   ├── messaging/
│   │   ├── MessageEnvelope.java          # 消息信封(对齐接口文档 §A.1)
│   │   ├── MsgType.java                  # 消息类型 / 设备类型 / 任务类型常量
│   │   ├── SimulatorProducer.java        # Kafka 生产者封装
│   │   ├── SimulatorConsumer.java        # 仅订阅 patrol.task.command
│   │   └── EventBus.java                 # 7 类上报消息的统一门面
│   ├── device/
│   │   ├── DeviceContext.java            # 单设备运行时状态(电量/故障/模式)
│   │   ├── DeviceSim.java                # 设备抽象基类(单线程调度)
│   │   ├── DroneSimulator.java           # 无人机(航拍图像)
│   │   └── RobotDogSimulator.java        # 机器狗(红外测温 + 环境传感器)
│   ├── task/
│   │   └── TaskDispatcher.java           # 派发 TASK_COMMAND 到目标设备,完成后回传 TASK_RESULT
│   └── util/
│       ├── Ids.java                      # UUID / imageId / deviceId / pointId 生成
│       ├── ImageGenerator.java           # 仿真图片(JPEG 800x600,含热点/场景,可选 base64)
│       └── RandomData.java               # 高斯分布/范围随机/概率触发
└── src/main/resources/
    ├── application.yml                   # 默认配置
    └── logback-spring.xml                # 控制台日志格式
```

---

## 2. 与平台其它模块的关系

| 对端 | 关系 | 通信方式 |
| --- | --- | --- |
| 后端业务服务 | 上下游 | Kafka 8 Topic(JSON,见接口文档 §A.2) |
| MongoDB | 不直连 | 由后端业务服务消费消息后落库 |
| HDFS | 不直连 | 由后端业务服务消费 IMAGE 后写 HDFS |
| Elasticsearch | 不直连 | 由后端业务服务消费消息后写 ES |
| Kibana / Web / Nginx | 无 | 仿真器不暴露 HTTP,纯后台进程 |

> 仿真器**只依赖 Kafka**,不依赖 Mongo/HDFS/ES,降低部署复杂度。

---

## 3. 消息契约（与接口文档 §A 一致）

### 3.1 信封

```json
{
  "msgId":     "UUID",
  "msgType":   "REGISTER|HEARTBEAT|STATUS|IMAGE|THERMAL|SENSOR|TASK_RESULT|TASK_COMMAND",
  "deviceId":  "UAV-001",
  "deviceType":"UAV|ROBOT_DOG",
  "timestamp": "2026-09-22T09:30:00+08:00",
  "data":      { ...载荷... }
}
```

### 3.2 各消息载荷字段（与 §A.2 一致）

| msgType | Topic | 关键字段 |
| --- | --- | --- |
| REGISTER | `patrol.device.register` | `{name, model, area, lng, lat}` |
| HEARTBEAT | `patrol.device.heartbeat` | `{battery}` |
| STATUS | `patrol.device.status` | `{online, battery, faultCode, mode}` |
| IMAGE | `patrol.drone.image` | `{imageId, cameraType, lng, lat, altitude, taskId, fileName, fileSize, imageBase64?}` |
| THERMAL | `patrol.dog.thermal` | `{pointId, targetDevice, temperature, ambientTemp, lng, lat, taskId}` |
| SENSOR | `patrol.dog.sensor` | `{temperature, humidity, gas, lng, lat}` |
| TASK_RESULT | `patrol.task.result` | `{taskId, status, detail, finishTime}` |
| TASK_COMMAND | `patrol.task.command` | `{taskId, taskType, area, deviceIds, params}` |

---

## 4. 快速运行

### 4.1 前置条件

- **JDK 17**（仿真器用 Spring Boot 3.2.5）
- **Maven 3.6+**（构建）
- Kafka 已运行且 8 个 Topic 已创建（参见 `deploy/README.md` 与 `deploy/docker-compose.yml` 中 `kafka-init` 服务）

### 4.2 构建

```bash
cd simulator
mvn -DskipTests package
# 产物: target/device-simulator.jar
```

### 4.3 启动（宿主机进程，最常见）

```bash
# 默认配置: 6 无人机 + 4 机器狗,Kafka = localhost:9092
java -jar target/device-simulator.jar

# 自定义规模与 Kafka 地址
java -jar target/device-simulator.jar \
    --kafka.bootstrap-servers=192.168.1.10:9092 \
    --simulator.drone-count=8 \
    --simulator.robot-dog-count=4
```

或通过环境变量：

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export DRONE_COUNT=8
export ROBOT_DOG_COUNT=4
java -jar target/device-simulator.jar
```

### 4.4 启动（Docker，可选）

```bash
# 构建
docker build -t patrol-device-simulator:1.0.0 .

# 假设 docker compose 项目名是 deploy(对应 patrol-net 网络)
docker run --rm \
    --network deploy_patrol-net \
    -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
    -e DRONE_COUNT=6 \
    -e ROBOT_DOG_COUNT=4 \
    patrol-device-simulator:1.0.0
```

> 宿主机进程 vs Docker：仿真器**只连 Kafka**，没有端口暴露，宿主机进程更简单；
> Docker 方式适合统一用 compose 起停/不想宿主机装 JDK 的场景。

---

## 5. 配置项速查（`application.yml`）

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka 地址 |
| `spring.kafka.consumer.group-id` | `simulator-group` | 仿真器消费组（仅用于 `patrol.task.command`） |
| `simulator.drone-count` | `6` | 无人机台数 |
| `simulator.robot-dog-count` | `4` | 机器狗台数 |
| `simulator.heartbeat-interval-ms` | `5000` | 心跳周期（接口文档 §2.2.5：离线阈值 15s = 3×心跳） |
| `simulator.status-interval-ms` | `30000` | 周期状态上报间隔 |
| `simulator.drone-image-interval-ms` | `8000` | 无人机空闲巡航图像上报间隔 |
| `simulator.dog-sensor-interval-ms` | `10000` | 机器狗空闲环境传感器上报间隔 |
| `simulator.thermal-point-interval-ms` | `1500` | THERMAL 任务相邻测点间隔 |
| `simulator.image-interval-ms` | `2000` | IMAGE 任务相邻图像间隔 |
| `simulator.fault-injection-rate` | `0.02` | 每心跳注入故障概率（2%） |
| `simulator.temp-over-rate` | `0.10` | THERMAL 测点超阈值概率（10%） |
| `simulator.battery-drain-per-hour` | `2.0` | 电量消耗速率（%/小时） |
| `simulator.image-base64-enabled` | `true` | IMAGE 消息是否附带 `imageBase64`（≤200KB） |

### Topic 名（与 `deploy/docker-compose.yml` 中 `kafka-init` 对齐）

```yaml
simulator.topics.register      = patrol.device.register
simulator.topics.heartbeat     = patrol.device.heartbeat
simulator.topics.status        = patrol.device.status
simulator.topics.drone-image   = patrol.drone.image
simulator.topics.dog-thermal   = patrol.dog.thermal
simulator.topics.dog-sensor    = patrol.dog.sensor
simulator.topics.task-result   = patrol.task.result
simulator.topics.task-command  = patrol.task.command
```

---

## 6. 行为说明

### 6.1 启动序列

1. Spring Boot 启动 → 自动配置 Kafka Producer/Consumer
2. `StartupBanner` 打印 Banner
3. `DeviceFactory.@PostConstruct` 创建并 `start()` 每台设备
   - 立即发送 **REGISTER** 到 `patrol.device.register`
   - 启动心跳（5s 周期）
   - 启动空闲巡航（无人机 8s 上报 1 张图、机器狗 10s 上报 1 次传感器）
4. `TaskDispatcher` 启动后台线程，从 `SimulatorConsumer` 队列拉取 `TASK_COMMAND`
5. `HealthReporter` 每 30s 打印设备/消息摘要
6. `DemoFaultInjector` 每 90s 演示性注入故障/低电量（可禁用）

### 6.2 任务执行流程（与接口文档 §5.1 一致）

```
Web POST /api/tasks
  → 业务服务写 Mongo + 发 Kafka patrol.task.command
  → 仿真器消费指令
  → TaskDispatcher 派发到目标设备
  → 设备切 MODE_TASK,执行期间暂停空闲上报
  → 执行结束(成功/异常) → 上报 patrol.task.result
  → 业务服务消费结果 → 更新任务状态
```

**任务类型支持**：

| taskType | 无人机 | 机器狗 |
| --- | --- | --- |
| `IMAGE` | ✅ 连续拍 N 张图 | — |
| `THERMAL` | — | ✅ 逐点上报 N 个测温点 |
| `SENSOR` | — | ✅ 连续上报 N 次环境传感器 |
| `COMPREHENSIVE` | ✅ 拍图 | ✅ 同步测温+传感器 |

> 任务级 `params.points` 与 `params.tempThreshold` 与接口文档 §A.2 完全对齐。

### 6.3 故障/告警演示

- **`fault-injection-rate`**：每个心跳周期以 2% 概率随机注入故障码 `E001~E004`
- **`temp-over-rate`**：10% 的测温点超过 `params.tempThreshold`，触发后端 `TEMP_OVER` 告警
- **`DemoFaultInjector`**：每 90s 演示性注入一次故障/低电量/恢复，便于现场展示

关闭演示注入：在 `DemoFaultInjector` 注解中把 `fixedDelay` 调大，或直接 `@Component` 注解掉即可。

### 6.4 离线检测配合

仿真器**不直接产生 OFFLINE 告警**。平台后端的 `DeviceOfflineScheduler`
（参见后端 `scheduler/DeviceOfflineScheduler.java`）周期性扫描
`device.lastHeartbeat`，超 15s 未更新即置 `OFFLINE` 并写离线告警。
要演示离线，只需**杀掉仿真器进程**或停掉一台设备。

---

## 7. 验证清单

仿真器启动后,建议按顺序验证:

```bash
# 1) 看 Topic 已建立(Kafka 容器内执行)
docker exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --list

# 2) 看消息流入(任选一个 Topic)
docker exec kafka kafka-console-consumer.sh \
    --bootstrap-server kafka:9092 \
    --topic patrol.device.heartbeat \
    --from-beginning --max-messages 3

# 3) Mongo 端设备台账(注册消息落地)
docker exec mongodb mongosh -u patrol -p patrol123 --authenticationDatabase patrol \
    patrol --eval "db.device.find().limit(3).pretty()"

# 4) Web 端验证
#    GET /api/devices           → 应能看到 10 台设备,首条心跳后置 ONLINE
#    GET /api/health            → 双实例轮询
#    POST /api/tasks            → 任务下发,稍后设备执行完成

# 5) Kibana 验证检索
#    打开 http://localhost:5601,索引 patrol-event
#    按告警类型 / 设备 / 时间 / geo 检索
```

---

## 8. 常见问题

**Q1. 启动报 `Connection to node -1 could not be established`**

按 `deploy/README.md §4.2`：仿真器在**宿主机**运行,必须用
`KAFKA_BOOTSTRAP_SERVERS=localhost:9092`,不能用容器内 `kafka:9092`。
容器内服务才用服务名。

**Q2. 收不到任务指令**

- 确认 `spring.kafka.consumer.group-id` 与业务服务侧 group 不冲突；
  默认 `simulator-group` 不会与 `patrol-backend-group` 冲突。
- `auto-offset-reset=latest`：仿真器只消费启动后的新指令（避免历史指令冲击）。
- 通过 `kafka-console-consumer` 监听 `patrol.task.command` 验证后端确实发出了指令。

**Q3. 报 `ImageIO` 找不到 JPEG writer**

仿真器使用 JDK 自带的 `javax.imageio`,alpine 基础镜像自带。如换 minimal JRE 镜像需补 `libjpeg-turbo`。

**Q4. 单机资源吃紧**

仿真器默认每台设备独立线程,共 10 个调度线程 + 1 个派发线程 + Kafka 生产/消费线程 ≈ 15 线程,内存 ≤ 256MB。压测可调大 `drone-count`/`robot-dog-count` 到 50+,注意增加 Kafka 分区与后端消费能力。

---

## 9. 与接口文档的对齐表

| 仿真器模块 | 接口文档对应章节 |
| --- | --- |
| `MessageEnvelope` | §A.1 消息信封 |
| `EventBus` 7 个 send* 方法 | §A.2 各消息类型载荷 |
| `MsgType` | §A.2 + §1.5 枚举值 |
| `drone-image-interval-ms=5000` 心跳 | §2.2.5 心跳 5s、离线阈值 15s |
| `TASK_COMMAND` 消费与 `executeTask` | §2.3 任务管理 + §A.2 TASK_COMMAND |
| `params.points`/`tempThreshold` | §A.3 需求空白定案 #3 |

仿真器严格遵守接口文档 §1.5（时间格式 / 枚举值 / 经纬度外层结构）与 §A.3
（IMAGE `imageBase64`、TASK_COMMAND `params`），与后端消费逻辑零差异对接。
