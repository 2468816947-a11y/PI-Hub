# 电力场站设备巡检数据集成平台 —— Docker 部署手册

> 阶段二联调环境：单机 Docker 多容器模拟分布式集群（Kafka KRaft / MongoDB / HDFS / ES / Kibana / Nginx / 后端双实例）。

## 0. 目录结构

```
deploy/
├── docker-compose.yml          # 编排文件(所有组件一键起停)
├── README.md                   # 本手册
├── nginx/
│   ├── nginx.conf              # 网关配置(负载均衡/限流/静态托管)
│   └── html/                   # 前端构建产物(由 frontend/dist 同步)
├── kafka/server.properties     # KRaft 参考模板(容器由环境变量生成)
├── es/
│   ├── elasticsearch.yml       # ES 单节点配置
│   ├── Dockerfile.ik           # IK 分词镜像构建文件(本地 zip 离线安装, compose 已默认启用)
│   ├── elasticsearch-analysis-ik-8.11.0.zip  # IK 插件本地安装包(已入库, 构建无需联网)
│   └── patrol-event-mapping.json  # 索引映射(架构文档 §6.3, es-init 容器自动导入)
├── hdfs/
│   ├── core-site.xml           # fs.defaultFS -> namenode:8020
│   └── hdfs-site.xml           # WebHDFS 开启/副本因子 1
├── mongo/init-mongo.js         # 建库建账号建集合(首次启动执行)
└── simulator/application-simulator.yml   # 宿主机仿真器配置模板
```

后端源码目录 `project/backend/`（Spring Boot 3.2：19 个 REST 接口全量实现——统一信封/全局异常/JWT 认证、设备/任务/告警/检索/统计/报告/文件，详见《04-接口文档》v1.2）。

## 1. 快速启动

前置条件：Docker Desktop 已安装，可用内存建议 ≥ 8GB。

```bash
cd deploy

# ① 只起中间件(不含后端; IK 镜像由已入库的 zip 离线构建, 无需联网)
docker compose up -d --build kafka kafka-init mongodb elasticsearch es-init kibana hdfs-namenode hdfs-datanode

# ② 全套启动(含后端双实例 + Nginx)
docker compose up -d --build

# 查看状态(全部 healthy 即就绪, 首次启动约需 2-3 分钟)
docker compose ps

# 停止(保留数据) / 彻底清理(删数据, 慎用)
docker compose down
docker compose down -v
```

**WSL2 注意**：Elasticsearch 需要 `vm.max_map_count=262144`（WSL 重启后需重新设置）：

```bash
wsl -d docker-desktop sysctl -w vm.max_map_count=262144
```

## 2. 中间件连接信息表

| 组件 | 容器内地址 | 宿主机端口 | 用户名/密码 | 关键备注 |
| --- | --- | --- | --- | --- |
| Kafka | `kafka:9092`(容器内) | `localhost:9092`(宿主机) | 无(PLAINTEXT) | KRaft 单 broker；8 个 Topic × 3 分区 1 副本；retention 24h；消费者组 `patrol-backend-group` |
| MongoDB | `mongodb:27017` | `localhost:27017` | root: `admin/patrol-admin-2026`；业务: `patrol/patrol123`(authSource=patrol) | 库 `patrol`；集合 device/patrol_task/alarm/hdfs_file |
| HDFS NameNode | `namenode:8020`(RPC)、`namenode:9870`(HTTP) | `localhost:9870` | 无 | WebHDFS 基址 `http://namenode:9870/webhdfs/v1` |
| HDFS DataNode | `datanode:9864` | `localhost:9864` | 无 | 副本因子 1 |
| Elasticsearch | `elasticsearch:9200` | `localhost:9200` | 无(安全已关, 仅开发) | 堆 512m；镜像内置 IK 分词(本地 zip 离线构建)；es-init 容器自动导入 `patrol-event` 索引(geo_point + ik_max_word) |
| Kibana | `kibana:5601` | `localhost:5601` | 无 | 中文界面；自动连 `http://elasticsearch:9200` |
| Nginx | `nginx:80` | `localhost:80` | 无 | `/api` → 双后端轮询；`/api/search` 限流 10r/s；上传上限 20m |
| backend-app-1 | `backend-app-1:8080` | `localhost:8080` | JWT: `admin/admin123` | Spring Boot 3.2 + Java 17；19 个 REST 接口全量实现 |
| backend-app-2 | `backend-app-2:8081` | `localhost:8081` | 同上 | 与 1 同镜像不同端口 |
| 仿真器(宿主机) | —— | 只连 `localhost:9092`(Kafka) | —— | 模拟 10 台设备(6 无人机 + 4 机器狗) |

Topic 清单：`patrol.device.register` / `patrol.device.heartbeat` / `patrol.device.status` / `patrol.drone.image` / `patrol.dog.thermal` / `patrol.dog.sensor` / `patrol.task.result` / `patrol.task.command`。

## 3. 部署验证清单

```bash
# 各组件健康状态
docker compose ps

# Kafka: Topic 已自动创建
docker exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --list

# MongoDB: 业务账号可登录、集合已建
docker exec mongodb mongosh -u patrol -p patrol123 --authenticationDatabase patrol \
  patrol --eval "db.getCollectionNames()"

# HDFS: DataNode 已注册(Live datanodes 必须为 1)
docker exec namenode hdfs dfsadmin -report | grep -A 2 "Live datanodes"
docker exec namenode hdfs dfs -mkdir -p /patrol && docker exec namenode hdfs dfs -ls /

# ES: 集群 green
curl "http://localhost:9200/_cluster/health?pretty"

# ES: IK 插件已安装 + patrol-event 索引已由 es-init 导入
docker run --rm patrol-es-ik:8.11.0 bin/elasticsearch-plugin list
curl -s "http://localhost:9200/patrol-event/_mapping" | grep -o "ik_max_word" | head -1

# 网关与负载均衡: 连续请求, 观察 access.log 中 up=backend-app-1/2 交替
curl http://localhost/healthz
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/health
docker logs nginx | grep 'up='

# 限流验证: 注意必须"真并发"才能触发 429(串行 30 次速率仅 ~10-15r/s, 被 burst=20 吸收,
# 2026-09-23 实测), 用 curl --parallel-immediate(≥7.66)同时发起:
curl -s -o /dev/null -w "%{http_code}\n" --parallel --parallel-immediate --parallel-max 30 \
  http://localhost/api/search/stats \
  $(for i in $(seq 1 30); do printf -- "--url http://localhost/api/search/stats "; done) \
  | sort | uniq -c
# 预期: 200/401 为主 + 出现 429; 429 响应带 Retry-After: 1 头
```

## 4. 常见踩坑与排错指南

### 4.0 后端容器重建后 Nginx 持续 502（必读）

**症状**：`docker compose build backend-app-1 backend-app-2 && docker compose up -d` 后，后端 healthcheck 正常但经网关访问 `/api/*` 持续 502；`docker logs nginx` 出现 `connect() failed (111: Connection refused)`。

**原因**：nginx 的 `upstream patrol_backend` 成员主机名在**配置加载时静态解析一次**（容器重建后 IP 变化，且 Docker 常把 .2/.3 两个 IP 互换，端口对不上必然拒绝）。nginx.conf 里的 `resolver 127.0.0.11` 只对"变量方式 proxy_pass 的主机名"生效，**不会**让 upstream 成员自动重解析（2026-09-23 实测）。

**解决**：重建后端后执行一次：

```bash
docker exec nginx nginx -s reload   # 重新解析 upstream 成员 IP, 秒级生效、不中断服务
```

> 答辩/演示当天全栈重启（`docker compose down && up`）后也建议执行一次，避免演示时踩坑。

### 4.1 Elasticsearch 内存溢出 / 反复重启

**症状**：`docker ps` 显示 ES 反复重启；`docker logs elasticsearch` 出现 `exit code 137`（OOM 被杀）或 `Native controller process has stopped`。

**排查**：

```bash
docker logs elasticsearch --tail 50
docker stats elasticsearch          # 观察内存曲线
docker inspect elasticsearch --format '{{.HostConfig.Memory}}'
```

**原因/解决**：① 容器内存 = 堆 + 非堆（堆 512m 时容器至少留 1g，compose 已设 `mem_limit: 1g`，**不要单独调大堆而不调容器限制**）；② WSL2 默认 `vm.max_map_count=65530` 偏低，执行 `wsl -d docker-desktop sysctl -w vm.max_map_count=262144`（WSL 重启后需重设）。

### 4.2 Kafka 连不上 / 连接建立后立刻断开

**症状**：仿真器或后端报 `Connection to node -1 could not be established`；或连接成功后反复 `Disconnected from node`。

**排查**：

```bash
docker logs kafka | grep -iE "listen|advertise"
docker exec kafka cat /opt/bitnami/kafka/config/server.properties | grep -E "listeners"
# 容器内自测(应正常列出 8 个 Topic)
docker exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --list
```

**原因/解决**：Kafka 客户端拿到的是 `advertised.listeners` 地址，与实际入口不一致时必然失败。三处必须对应：容器内监听 `EXTERNAL://:9094` → 广播地址 `EXTERNAL://localhost:9092` → 端口映射 `"9092:9094"`。规则：**容器内程序用 `kafka:9092`，宿主机程序（仿真器）用 `localhost:9092`**；若仿真器在另一台机器，把 advertised 中的 `localhost` 换成宿主机局域网 IP。

### 4.3 WebHDFS 403 / DataNode 注册失败

**症状**：WebHDFS 写操作 403；namenode 日志报 `Datanode denied communication with namenode because the hostname cannot be resolved`。

**排查**：

```bash
docker logs namenode | grep -i denied
docker exec namenode hdfs dfsadmin -report | grep -A 2 "Live datanodes"   # 必须为 1
curl -i "http://localhost:9870/webhdfs/v1/?op=LISTSTATUS&user.name=root"
```

**原因/解决**：① 403 → 已配 `dfs.permissions.enabled=false`（重启用 `docker compose restart hdfs-namenode hdfs-datanode` 生效）；② 主机名校验报错 → 确认 `ip-hostname-check=false` 生效；③ **宿主机浏览器打不开 WebHDFS** → PUT/GET 会重定向到 DataNode，Windows 需在 `C:\Windows\System32\drivers\etc\hosts` 添加 `127.0.0.1 namenode datanode`（后端在容器内不受影响）；④ curl 快速测试下载可用 `&noredirect=true` 跳过重定向；⑤ 路径含中文（如 `1号变电站`）必须 URL 编码后再拼 URL。

### 4.4 容器间网络不通 / UnknownHostException

**症状**：后端报 `UnknownHostException: mongodb`、`Connection refused`；Nginx 502。

**排查**：

```bash
docker network inspect patrol-net          # 确认所有容器都在 patrol-net
docker exec backend-app-1 getent hosts mongodb    # 应解析出 172.x.x.x
docker exec backend-app-1 curl -s http://elasticsearch:9200
```

**原因/解决**：① 配置里写了 `localhost`（**容器内 localhost 只指向容器自己**，一律改用服务名）；② 服务没加 `networks: patrol-net`；③ 后端容器重建后 Nginx 报 502 → nginx.conf 已配置 Docker 内嵌 DNS 自动重解析，若仍 502 手动 `docker exec nginx nginx -s reload`。

### 4.5 健康检查不过 / 依赖链启动慢

**症状**：`docker compose ps` 长期 `starting/unhealthy`，后端因 `depends_on: service_healthy` 一直不启动。

**排查**：

```bash
docker inspect kafka --format '{{json .State.Health}}'
docker compose logs -f hdfs-namenode        # 观察启动进度
```

**原因/解决**：① NameNode 首次 format + 启动需 30~90s，ES 首次 60s+，已放宽 `start_period`，耐心等待；② 数据卷损坏（非正常关机/反复实验）→ `docker compose down -v` 清卷重来（**会删除全部数据**）；③ 后端 healthcheck 依赖 `GET /api/health` 接口，该接口异常时容器会一直 unhealthy（不影响启动，但 Nginx 轮询可能命中未就绪实例）。

### 4.6 特别提醒：IK 分词插件（已解决）

`patrol-event` 索引的 `description` 字段使用 `ik_max_word` 分词，官方 ES 镜像不含该插件，后端初始化索引会报 `analyzer [ik_max_word] not found`。**现状**：compose 的 elasticsearch 服务已默认使用 `es/Dockerfile.ik` 构建（插件 zip 已入库，`COPY` 后 `file://` 离线安装，构建无需联网），es-init 容器会在首次启动时自动导入 patrol-event mapping。验证：`docker run --rm patrol-es-ik:8.11.0 bin/elasticsearch-plugin list` 应输出 `analysis-ik`。若仍需临时退回官方镜像，把 compose 中 build 段换回 image，并将 mapping 的 analyzer 改为 `standard`。

### 4.7 后端本地构建踩坑（组员必读）

后端骨架已入库，本机开发（IDEA + Maven）时有两个已知坑：

1. **旧阿里云 Maven 镜像已废弃**：本地 `settings.xml` 若配置的是 `http://maven.aliyun.com/nexus/...`（旧地址），会下载失败或缺 artifact。改为 `https://maven.aliyun.com/repository/public`（或直接用 Maven Central）。
2. **不要引入 `spring-boot-starter-kafka`**：该 starter 在 Maven Central 没有 3.x 版本（2.x 后直到 4.0 才重新发布，很多教程是 Boot 2.x 写法）。Boot 3 正确做法是直接依赖 `org.springframework.kafka:spring-kafka`（版本由 BOM 管理），自动配置照常生效 —— `backend/pom.xml` 已是正确写法，勿改回 starter。

## 5. 附录：后端接入配置

后端 `application.yml` 直接消费 compose 注入的环境变量：

```yaml
spring:
  data:
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI:mongodb://patrol:patrol123@mongodb:27017/patrol?authSource=patrol}
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
    consumer:
      group-id: ${PATROL_KAFKA_GROUP_ID:patrol-backend-group}
      enable-auto-commit: false            # 架构文档 §5: 手动提交 + 幂等去重
patrol:
  es:
    uris: ${PATROL_ES_URIS:http://elasticsearch:9200}
  hdfs:
    webhdfs-base: ${PATROL_HDFS_WEBHDFS_BASE:http://namenode:9870}
```

WebHDFS 上传示例（两步 CREATE → PUT，与业务封装对照）：

```bash
# 1) CREATE 拿重定向地址(Location 指向 DataNode)
curl -i -X PUT "http://localhost:9870/webhdfs/v1/patrol/test.txt?op=CREATE&overwrite=true&user.name=root"
# 2) 将文件 PUT 到 Location 返回的地址(宿主机需 hosts 映射; 容器内直接用 datanode:9864)
curl -i -X PUT -T test.txt "http://localhost:9870/webhdfs/v1/patrol/test.txt?op=CREATE&overwrite=true&user.name=root&noredirect=true"
# 3) 下载
curl -i "http://localhost:9870/webhdfs/v1/patrol/test.txt?op=OPEN&user.name=root&noredirect=true"
```
