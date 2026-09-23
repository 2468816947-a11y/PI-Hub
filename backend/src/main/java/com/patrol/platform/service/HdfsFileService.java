package com.patrol.platform.service;

import com.patrol.platform.common.BusinessConstants;
import com.patrol.platform.common.BusinessException;
import com.patrol.platform.common.ErrorCode;
import com.patrol.platform.common.IdGenerator;
import com.patrol.platform.entity.Device;
import com.patrol.platform.entity.HdfsFile;
import com.patrol.platform.kafka.KafkaMessage;
import com.patrol.platform.repository.HdfsFileRepository;
import com.patrol.platform.storage.HdfsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * HDFS 文件归档 Service。
 * <p>
 * 处理两类写入:
 * <ul>
 *   <li>Kafka IMAGE 消息: imageBase64 有值时解码并归档(接口文档附录 A.3.1)</li>
 *   <li>POST /api/files(阶段 4 接入): 前端手工上传, multipart 二进制</li>
 * </ul>
 * 目录组织: /patrol/{区域}/{日期}/{设备}/{fileId}.{ext}(架构 §3.3)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HdfsFileService {

    private final HdfsClient hdfsClient;
    private final HdfsFileRepository hdfsFileRepository;
    private final DeviceService deviceService;
    private final IdGenerator idGenerator;

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 处理 IMAGE 消息中的 imageBase64(可选字段)。
     * <p>
     * 若消息不含 imageBase64, 仅登记元数据占位(不写 HDFS); 真实场景由前端手工上传补齐。
     *
     * @return 写入的文件元数据, 若未上传二进制则返回 null
     */
    public Optional<HdfsFile> saveImageFromMessage(KafkaMessage msg) {
        if (msg.getData() == null) return Optional.empty();
        Object b64 = msg.getData().get("imageBase64");
        if (b64 == null) {
            log.debug("IMAGE message without imageBase64, skip HDFS write: msgId={}", msg.getMsgId());
            return Optional.empty();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(String.valueOf(b64));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid imageBase64 in IMAGE message: msgId={}", msg.getMsgId());
            return Optional.empty();
        }
        // 单图 ≤ 200KB(架构 §11 关键风险)
        if (bytes.length > 200 * 1024) {
            log.warn("IMAGE exceeds 200KB, skip HDFS write: size={}", bytes.length);
            return Optional.empty();
        }

        Device device = null;
        try {
            device = deviceService.registerOrUpdate(msg); // 确保设备存在(罕见: 直接发 IMAGE 之前没注册)
        } catch (Exception ignore) {}

        // 构建路径
        String area = device != null && device.getArea() != null ? device.getArea() : "unknown";
        String date = OffsetDateTime.now().format(DATE_DIR);
        String ext = inferExt(String.valueOf(msg.getData().getOrDefault("fileName", "")));
        String fileId = idGenerator.nextFileId();
        String fileName = String.valueOf(msg.getData().getOrDefault("fileName", fileId + ext));
        String hdfsPath = "/patrol/" + area + "/" + date + "/" + msg.getDeviceId() + "/" + fileId + ext;

        boolean ok = hdfsClient.upload(hdfsPath, bytes);
        if (!ok) {
            log.error("HDFS upload failed for IMAGE msg: msgId={}, path={}", msg.getMsgId(), hdfsPath);
            return Optional.empty();
        }

        HdfsFile meta = HdfsFile.builder()
                .fileId(fileId)
                .fileName(fileName)
                .hdfsPath(hdfsPath)
                .deviceId(msg.getDeviceId())
                .taskId(asString(msg.getData().get("taskId")).orElse(null))
                .fileSize((long) bytes.length)
                .uploadTime(OffsetDateTime.now())
                .build();
        hdfsFileRepository.save(meta);
        log.info("IMAGE archived to HDFS: fileId={}, path={}, deviceId={}",
                fileId, hdfsPath, msg.getDeviceId());
        return Optional.of(meta);
    }

    private String inferExt(String fileName) {
        if (fileName == null) return ".jpg";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : ".jpg";
    }

    private Optional<String> asString(Object v) {
        if (v == null) return Optional.empty();
        return Optional.of(String.valueOf(v));
    }

    // ==================== REST API 业务方法 ====================

    /** 允许的图片扩展名(接口文档 §2.7.1) */
    private static final List<String> ALLOWED_EXTS = Arrays.asList(".jpg", ".jpeg", ".png", ".webp");
    /** 单文件最大 20MB */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /**
     * 手工上传巡检图片(POST /api/files, multipart)。
     * 校验: 仅 jpg/jpeg/png/webp, ≤ 20MB。
     */
    public HdfsFile uploadFile(MultipartFile file, String deviceId, String taskId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件为空");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
        String ext = inferExt(originalName).toLowerCase();
        if (!ALLOWED_EXTS.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "仅支持图片格式: jpg/jpeg/png/webp");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "文件超过 20MB 上限");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }

        // 构建路径
        Device device = deviceId != null ? deviceService.getDeviceOrNull(deviceId) : null;
        String area = device != null && device.getArea() != null ? device.getArea() : "unknown";
        String date = OffsetDateTime.now().format(DATE_DIR);
        String fileId = idGenerator.nextFileId();
        String hdfsPath = "/patrol/" + area + "/" + date + "/" +
                (deviceId != null ? deviceId : "manual") + "/" + fileId + ext;

        boolean ok = hdfsClient.upload(hdfsPath, bytes);
        if (!ok) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "HDFS 写入失败");
        }
        HdfsFile meta = HdfsFile.builder()
                .fileId(fileId)
                .fileName(originalName)
                .hdfsPath(hdfsPath)
                .deviceId(deviceId)
                .taskId(taskId)
                .fileSize((long) bytes.length)
                .uploadTime(OffsetDateTime.now())
                .build();
        return hdfsFileRepository.save(meta);
    }

    /**
     * 文件元数据分页查询。
     */
    public Page<HdfsFile> listFiles(String deviceId, String taskId, Pageable pageable) {
        boolean hasDevice = deviceId != null && !deviceId.isEmpty();
        boolean hasTask = taskId != null && !taskId.isEmpty();
        if (hasDevice) {
            return hdfsFileRepository.findByDeviceIdOrderByUploadTimeDesc(deviceId, pageable);
        } else if (hasTask) {
            return hdfsFileRepository.findByTaskIdOrderByUploadTimeDesc(taskId, pageable);
        }
        return hdfsFileRepository.findAll(pageable);
    }

    /**
     * 下载文件(GET /api/files/{fileId}/download)。
     * 返回 [byte[], fileName], 不存在抛 404。
     */
    public HdfsFile getFileOrThrow(String fileId) {
        HdfsFile meta = hdfsFileRepository.findByFileId(fileId);
        if (meta == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在: " + fileId);
        }
        return meta;
    }

    public byte[] downloadFile(String fileId) {
        HdfsFile meta = getFileOrThrow(fileId);
        byte[] data = hdfsClient.download(meta.getHdfsPath());
        if (data == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "HDFS 文件不存在: " + meta.getHdfsPath());
        }
        return data;
    }
}
