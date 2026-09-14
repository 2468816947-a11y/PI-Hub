# 电力场站设备巡检数据集成平台 —— Docker 部署手册

> 阶段二联调环境：单机 Docker 多容器模拟分布式集群（Kafka KRaft / MongoDB / HDFS / ES / Kibana / Nginx / 后端双实例）。

## 0. 目录结构

```
deploy/
├── docker-compose.yml          # 编排文件(所有组件一键起停)
├── README.md                   # 本手册
├── nginx/
│   ├── nginx.conf              # 网关配置(负载均衡/限流/静态托管)
│   └── html/index.html         # 前端占位页(替换为 dist 产物)
├── kafka/server.properties     # KRaft 参考模板(容器由环境变量生成)
├── es/
│   ├── elasticsearch.yml       # ES 单节点配置
│   ├── Dockerfile.ik           # 可选: IK 中文分词插件镜像
│   └── patrol-event-mapping.json  # 索引映射(架构文档 §6.3)
├── hdfs/
│   ├── core-site.xml           # fs.defaultFS -> namenode:8020
│   └── hdfs-site.xml           # WebHDFS 开启/副本因子 1
├── mongo/init-mongo.js         # 建库建账号建集合(首次启动执行)
└── simulator/application-simulator.yml   # 宿主机仿真器配置模板
```

后端源码目录 `project/backend/`（Dockerfile 模板已就绪，放入 `pom.xml` + `src` 即可构建）。

## 1. 快速启动

前置条件：Docker Desktop 已安装，可用内存建议 ≥ 8GB。

```bash
cd deploy

# ① 后端源码未就绪时，先只起中间件(互不依赖后端)
docker compose up -d kafka mongodb elasticsearch kibana hdfs-namenode hdfs-datanode

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
| Elasticsearch | `elasticsearch:9200` | `localhost:9200` | 无(安全已关, 仅开发) | 堆 512m；索引 `patrol-event`(geo_point + ik_max_word) |
| Kibana | `kibana:5601` | `localhost:5601` | 无 | 中文界面；自动连 `http://elasticsearch:9200` |
| Nginx | `nginx:80` | `localhost:80` | 无 | `/api` → 双后端轮询；`/api/search` 限流 10r/s；上传上限 20m |
| backend-app-1 | `backend-app-1:8080` | `localhost:8080` | JWT(后端自管) | Spring Boot 3.2 + Java 17 |
| backend-app-2 | `backend-app-2:8081` | `localhost:8081` | 同上 | 与 1 同镜像不同端口 |
| 仿真器(宿主机) | —— | 连 `localhost:9092` / `localhost:27017` | `patrol/patrol123` | 模拟 10 台设备(6 无人机 + 4 机器狗) |

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

# 网关与负载均衡: 连续请求, 观察 access.log 中 up=backend-app-1/2 交替
curl http://localhost/healthz
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/health
docker logs nginx | grep 'up='

# 限流验证: 快速连发 30 次, 应出现 429
for i in $(seq 1 30); do curl -s -o /dev/null -w "%{http_code} " http://localhost/api/search/stats; done; echo
```

## 4. 常见踩坑与排错指南

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

**原因/解决**：① NameNode 首次 format + 启动需 30~90s，ES 首次 60s+，已放宽 `start_period`，耐心等待；② 数据卷损坏（非正常关机/反复实验）→ `docker compose down -v` 清卷重来（**会删除全部数据**）；③ 后端 healthcheck 依赖 `GET /api/health` 接口（架构文档 §8 已设计），未实现前后端会一直 unhealthy（不影响启动，但 Nginx 轮询可能命中未就绪实例）。

### 4.6 特别提醒：IK 分词插件

`patrol-event` 索引的 `description` 字段使用 `ik_max_word` 分词，官方 ES 镜像不含该插件，后端初始化索引会报 `analyzer [ik_max_word] not found`。解决：用 `es/Dockerfile.ik` 构建镜像（compose 中 elasticsearch 服务改 build 段，见文件内注释），或临时把 mapping 的 analyzer 改为 `standard`。

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
