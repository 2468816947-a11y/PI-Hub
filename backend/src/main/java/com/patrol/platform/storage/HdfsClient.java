package com.patrol.platform.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HDFS WebHDFS 客户端(架构 §3.3)。
 * <p>
 * 流程: CREATE → PUT(上传) → CLOSE。
 * 通过 Query 参数 user.name 指定 Hadoop 用户代理(默认 dr.who, 与 HDFS 配置一致)。
 * <p>
 * 注意: 由于环境约束(本任务未直接访问 HDFS), 本实现使用 JDK HttpClient 通过 RestClient 调 WebHDFS REST API。
 * 真实部署需保证 Namenode :9870 可达。
 */
@Slf4j
@Component
public class HdfsClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String user;

    public HdfsClient(@Value("${patrol.hdfs.webhdfs-base}") String baseUrl,
                      @Value("${patrol.hdfs.user}") String user) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.user = user;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    /**
     * 上传字节数据到 HDFS 指定路径。
     *
     * @param path   HDFS 完整路径, 如 /patrol/1号变电站/2026-09-17/UAV-001/img-xxx.jpg
     * @param data   文件二进制
     * @return true 表示成功
     */
    public boolean upload(String path, byte[] data) {
        try {
            // 1) CREATE 阶段: 获取重定向后的 DataNode 上传 URL
            String createUrl = String.format("%s/webhdfs/v1%s?op=CREATE&user.name=%s&overwrite=true",
                    baseUrl, urlEncode(path), urlEncode(user));
            String redirect = restClient.post().uri(createUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation()
                    .toString();

            // 2) PUT 阶段: 向重定向地址写入文件内容
            URI uri = URI.create(redirect);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String pathAndQuery = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
            String putUrl = scheme + "://" + host + ":" + port + pathAndQuery;

            RestClient dataNodeClient = RestClient.builder().baseUrl(scheme + "://" + host + ":" + port).build();
            dataNodeClient.put().uri(pathAndQuery)
                    .body(new ByteArrayInputStream(data))
                    .retrieve()
                    .toBodilessEntity();
            log.info("HDFS upload success: path={}, size={}", path, data.length);
            return true;
        } catch (Exception e) {
            log.error("HDFS upload failed: path={}, size={}", path, data.length, e);
            return false;
        }
    }

    /**
     * 检查 HDFS 路径是否存在(通过 LISTSTATUS)。
     *
     * @return true 表示存在
     */
    public boolean exists(String path) {
        try {
            String url = String.format("%s/webhdfs/v1%s?op=LISTSTATUS&user.name=%s",
                    baseUrl, urlEncode(path), urlEncode(user));
            restClient.get().uri(url).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 下载 HDFS 文件(OPEN)。
     *
     * @return 字节内容, 失败返回 null
     */
    public byte[] download(String path) {
        try {
            String url = String.format("%s/webhdfs/v1%s?op=OPEN&user.name=%s",
                    baseUrl, urlEncode(path), urlEncode(user));
            byte[] body = restClient.get().uri(url).retrieve().body(byte[].class);
            return body;
        } catch (Exception e) {
            log.error("HDFS download failed: path={}", path, e);
            return null;
        }
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
