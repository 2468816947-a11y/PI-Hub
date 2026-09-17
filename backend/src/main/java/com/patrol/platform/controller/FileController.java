package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import com.patrol.platform.dto.PageResponse;
import com.patrol.platform.entity.HdfsFile;
import com.patrol.platform.service.HdfsFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传下载(接口文档 §2.7 共 3 个接口)。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final HdfsFileService hdfsFileService;

    /** 上传图片(POST /api/files, multipart) */
    @PostMapping
    public ApiResponse<FileVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) String deviceId,
                                       @RequestParam(required = false) String taskId) {
        HdfsFile meta = hdfsFileService.uploadFile(file, deviceId, taskId);
        return ApiResponse.ok(new FileVO(
                meta.getFileId(), meta.getFileName(), meta.getHdfsPath(),
                meta.getFileSize(), meta.getUploadTime() == null ? null : meta.getUploadTime().toString()));
    }

    /** 文件列表(GET /api/files) */
    @GetMapping
    public ApiResponse<PageResponse<HdfsFileVO>> list(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(size, 1), 100));
        Page<HdfsFile> result = hdfsFileService.listFiles(deviceId, taskId, pageable);
        return ApiResponse.ok(PageResponse.from(result.map(f ->
                new HdfsFileVO(f.getFileId(), f.getFileName(), f.getHdfsPath(),
                        f.getDeviceId(), f.getTaskId(), f.getFileSize(),
                        f.getUploadTime() == null ? null : f.getUploadTime().toString()))));
    }

    /** 下载文件(GET /api/files/{fileId}/download) */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable("fileId") String fileId) {
        HdfsFile meta = hdfsFileService.getFileOrThrow(fileId);
        byte[] data = hdfsFileService.downloadFile(fileId);
        String contentType = inferContentType(meta.getFileName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.getFileName() + "\"")
                .body(data);
    }

    private String inferContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /** 上传响应 VO(接口文档 §2.7.1) */
    public record FileVO(String fileId, String fileName, String hdfsPath,
                         Long fileSize, String uploadTime) {}

    /** 文件列表 VO */
    public record HdfsFileVO(String fileId, String fileName, String hdfsPath,
                              String deviceId, String taskId, Long fileSize, String uploadTime) {}
}
