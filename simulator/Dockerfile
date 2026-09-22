# ============================================================
# 无人机/机器狗仿真器 镜像(可选)
#
# 默认情况下仿真器作为宿主机 Java 进程运行即可,无需容器化;
# 此 Dockerfile 提供"宿主机无 JDK / 想用 docker compose 跑"的场景使用。
#
# 构建:  docker build -t patrol-device-simulator:1.0.0 .
# 运行:  docker run --rm \
#           --network=deploy_patrol-net \
#           -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
#           patrol-device-simulator:1.0.0
# ============================================================

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="patrol-platform"
LABEL description="无人机/机器狗仿真器 - 电力场站巡检数据集成平台"

WORKDIR /opt/simulator

# 把构建好的 fat jar 拷进来(在 simulator/ 目录下执行: mvn -DskipTests package)
COPY target/device-simulator.jar /opt/simulator/device-simulator.jar

# Alpine 时区: 与北京时区对齐,ISO 8601 时间戳人类可读
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms128m -Xmx256m"

EXPOSE 8080

# 健康检查: 通过 actuator/health(若启用) 或消费命令队列长度日志(此处省)
HEALTHCHECK NONE

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/simulator/device-simulator.jar"]
