package com.patrol.platform.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * HDFS WebHDFS 客户端(架构 §3.3)。
 * <p>
 * 使用 java.net.http.HttpClient(Java 11+) 实现, 避免 Spring RestTemplate 的额外 header 行为
 * 导致的 WebHDFS 405 问题。
 * <p>
 * 流程: MKDIRS(确保父目录) → CREATE(PUT 获取重定向) → PUT(向 DataNode 发文件)。
 * 注意: WebHDFS 各 op 的 HTTP 方法必须严格对应(CREATE/MKDIRS=PUT, APPEND=POST,
 * OPEN=GET, DELETE=DELETE), 用错会被以 400/500 拒绝。
 */
@Slf4j
@Component
public class HdfsClient {

    private final String baseUrl;
    private final String user;
    private final HttpClient httpClient;
    /** 已确认存在的目录缓存，避免每次都调 mkdirs */
    private final Set<String> ensuredDirs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public HdfsClient(@Value("${patrol.hdfs.webhdfs-base}") String baseUrl,
                      @Value("${patrol.hdfs.user}") String user) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.user = user;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 上传字节数据到 HDFS 指定路径。
     *
     * @param hdfsPath HDFS 完整路径, 如 /patrol/1号变电站/2026-09-17/UAV-001/img-xxx.jpg
     * @param data    文件二进制
     * @return true 表示成功
     */
    public boolean upload(String hdfsPath, byte[] data) {
        try {
            // Step 0: 确保所有父目录存在
            if (!ensureParentDirs(hdfsPath)) {
                log.error("HDFS mkdirs failed for path: {}", hdfsPath);
                return false;
            }

            // Step 1: PUT CREATE 获取 DataNode 重定向地址(WebHDFS 约定 CREATE 用 PUT)
            String encodedPath = urlEncode(hdfsPath);
            String createUrl = String.format("%s/webhdfs/v1%s?op=CREATE&user.name=%s&overwrite=true",
                    baseUrl, encodedPath, URLEncoder.encode(user, StandardCharsets.UTF_8));
            HttpRequest createReq = HttpRequest.newBuilder()
                    .uri(URI.create(createUrl))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
            if (createResp.statusCode() != 307) {
                log.error("HDFS CREATE failed: expected 307, got {}, body={}", createResp.statusCode(), createResp.body());
                return false;
            }
            URI redirectUri = createResp.headers().firstValue("Location").map(URI::create).orElse(null);
            if (redirectUri == null) {
                log.error("HDFS CREATE failed: no Location header");
                return false;
            }

            // Step 2: PUT 向 DataNode 上传文件内容
            HttpRequest putReq = HttpRequest.newBuilder()
                    .uri(redirectUri)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();
            HttpResponse<String> putResp = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
            if (putResp.statusCode() / 100 != 2) {
                log.error("HDFS PUT failed: status={}, body={}", putResp.statusCode(), putResp.body());
                return false;
            }
            log.info("HDFS upload success: path={}, size={}", hdfsPath, data.length);
            return true;
        } catch (Exception e) {
            log.error("HDFS upload failed: path={}, size={}", hdfsPath, data.length, e);
            return false;
        }
    }

    /**
     * 递归确保所有父目录存在。
     */
    private boolean ensureParentDirs(String hdfsPath) {
        String parent = hdfsPath;
        while (!parent.isEmpty() && !parent.equals("/")) {
            int lastSlash = parent.lastIndexOf('/');
            if (lastSlash <= 0) break;
            parent = parent.substring(0, lastSlash);
            if (ensuredDirs.contains(parent)) continue;
            if (!mkdirs(parent)) return false;
            ensuredDirs.add(parent);
        }
        return true;
    }

    /**
     * 创建单个 HDFS 目录(MKDIRS 必须用 PUT，其他方法会被 WebHDFS 以 500 拒绝)。
     */
    private boolean mkdirs(String hdfsPath) {
        try {
            String encodedPath = urlEncode(hdfsPath);
            String url = String.format("%s/webhdfs/v1%s?op=MKDIRS&user.name=%s",
                    baseUrl, encodedPath, URLEncoder.encode(user, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.debug("HDFS mkdirs ok: {}", hdfsPath);
                return true;
            }
            log.warn("HDFS mkdirs failed: path={}, status={}, body={}", hdfsPath, resp.statusCode(), resp.body());
            return false;
        } catch (Exception e) {
            log.error("HDFS mkdirs error: path={}", hdfsPath, e);
            return false;
        }
    }

    /**
     * 检查 HDFS 路径是否存在。
     */
    public boolean exists(String hdfsPath) {
        try {
            String encodedPath = urlEncode(hdfsPath);
            String url = String.format("%s/webhdfs/v1%s?op=LISTSTATUS&user.name=%s",
                    baseUrl, encodedPath, URLEncoder.encode(user, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 下载 HDFS 文件。
     * <p>
     * OPEN 返回 307 + Location(指向 DataNode), JDK HttpClient 默认不跟随重定向,
     * 需手动拿 Location 再 GET 一次(与 upload 的 CREATE→PUT 两段式同理)。
     */
    public byte[] download(String hdfsPath) {
        try {
            String encodedPath = urlEncode(hdfsPath);
            String url = String.format("%s/webhdfs/v1%s?op=OPEN&user.name=%s",
                    baseUrl, encodedPath, URLEncoder.encode(user, StandardCharsets.UTF_8));
            HttpRequest openReq = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<byte[]> openResp = httpClient.send(openReq, HttpResponse.BodyHandlers.ofByteArray());
            if (openResp.statusCode() == 200) {
                return openResp.body();
            }
            URI redirectUri = openResp.headers().firstValue("Location").map(URI::create).orElse(null);
            if (openResp.statusCode() == 307 && redirectUri != null) {
                HttpRequest dataReq = HttpRequest.newBuilder().uri(redirectUri).GET().build();
                HttpResponse<byte[]> dataResp = httpClient.send(dataReq, HttpResponse.BodyHandlers.ofByteArray());
                if (dataResp.statusCode() == 200) {
                    return dataResp.body();
                }
                log.warn("HDFS download redirect failed: status={}", dataResp.statusCode());
            }
            log.warn("HDFS download failed: path={}, status={}", hdfsPath, openResp.statusCode());
            return null;
        } catch (Exception e) {
            log.error("HDFS download failed: path={}", hdfsPath, e);
            return null;
        }
    }

    /**
     * URL 编码 HDFS 路径: 逐段编码、保留 '/' 分隔符(与 Hadoop 官方客户端一致)。
     * <p>
     * 不能用 URLEncoder 全量编码: '/' 变 %2F 后, NameNode 返回的 CREATE 重定向
     * Location 会损坏(实测变成 ".../webhdfs/v1/2Fpatrol/..."), 上传必失败。
     */
    private String urlEncode(String path) {
        String[] segs = path.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(segs[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }
}
