package com.patrol.platform.elasticsearch;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * patrol-event 索引初始化器(架构 §6.3)。
 * <p>
 * 启动后显式检查索引是否存在:
 * <ul>
 *   <li>不存在 → 读取 patrol-event-mapping.json 并 PUT 到 ES, 保证 keyword/text 字段类型与架构文档一致</li>
 *   <li>已存在 → 跳过(ES 中通过注解修改字段类型需重建索引, 由运维触发)</li>
 * </ul>
 * 字段类型约定:
 * <ul>
 *   <li>检索维度字段(设备/类型/区域/告警类型/级别)全部 keyword</li>
 *   <li>description 走 ik_max_word(ES 镜像已安装 analysis-ik 插件)</li>
 *   <li>position 为 geo_point, 写入时由后端将 {lng, lat} 转为 {lat, lon}</li>
 *   <li>eventTime 按 ISO 8601 解析</li>
 * </ul>
 */
@Slf4j
@Component
public class PatrolEventIndexInitializer {

    // 直接用 Spring 标准的 spring.elasticsearch.uris(application.yml 已配置, 由 PATROL_ES_URIS 注入),
    // 缺省 localhost:9200 仅用于本地开发; 容器内为 http://elasticsearch:9200
    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String esUris;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @PostConstruct
    public void initIndex() {
        String esUrl = esUris.replaceAll("/$", "");
        try {
            // 检查索引是否存在
            HttpRequest checkReq = HttpRequest.newBuilder()
                    .uri(URI.create(esUrl + "/patrol-event"))
                    .GET()
                    .build();
            HttpResponse<String> checkResp = HTTP_CLIENT.send(checkReq, HttpResponse.BodyHandlers.ofString());
            if (checkResp.statusCode() == 200) {
                log.info("ES index already exists: patrol-event");
                return;
            }
        } catch (Exception e) {
            // 索引不存在会抛异常, 继续创建
            log.debug("ES index check: not exists, will create");
        }

        try {
            String mappingJson = loadMappingJson();
            HttpRequest createReq = HttpRequest.newBuilder()
                    .uri(URI.create(esUrl + "/patrol-event"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mappingJson))
                    .build();
            HttpResponse<String> createResp = HTTP_CLIENT.send(createReq, HttpResponse.BodyHandlers.ofString());
            if (createResp.statusCode() == 200 || createResp.statusCode() == 201) {
                log.info("ES index created with custom mapping from patrol-event-mapping.json");
            } else {
                log.error("Failed to create ES index: {} {}", createResp.statusCode(), createResp.body());
            }
        } catch (Exception e) {
            log.error("Failed to initialize patrol-event index with custom mapping", e);
        }
    }

    /**
     * 从 classpath 加载 patrol-event-mapping.json, 去掉注释行后返回合法 JSON 字符串。
     * <p>
     * 文件副本说明: deploy/es/patrol-event-mapping.json 供 es-init 容器首次建索引(权威副本);
     * 本资源副本供后端在本地开发等场景兜底创建, 两处修改需保持一致。
     */
    private String loadMappingJson() throws IOException {
        try (InputStream in = new ClassPathResource("patrol-event-mapping.json").getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return stripComments(json);
        }
    }

    private String stripComments(String json) {
        StringBuilder sb = new StringBuilder();
        for (String line : json.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("//")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
