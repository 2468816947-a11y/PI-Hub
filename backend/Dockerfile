# =====================================================================
# 后端 Spring Boot 3.2 + Java 17 镜像构建模板
# 放置位置: 后端源码根目录(project/backend), 与 pom.xml、src 同级
# 注意: 后端需实现 GET /api/health 接口返回 200(容器 healthcheck 依赖)
# 构建: docker compose build backend-app-1 backend-app-2
# =====================================================================

# ---- 阶段一: Maven 构建 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# ---- 阶段二: 运行(轻量 JRE; curl 供 healthcheck 使用) ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
