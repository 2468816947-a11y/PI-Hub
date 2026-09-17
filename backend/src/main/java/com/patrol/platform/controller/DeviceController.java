package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import com.patrol.platform.dto.DeviceDto.DeviceCreateRequest;
import com.patrol.platform.dto.DeviceDto.DeviceUpdateRequest;
import com.patrol.platform.dto.DeviceDto.DeviceVO;
import com.patrol.platform.dto.PageResponse;
import com.patrol.platform.entity.Device;
import com.patrol.platform.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * 设备管理(接口文档 §2.2 共 5 个接口)。
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /** 列表查询(GET /api/devices) */
    @GetMapping
    public ApiResponse<PageResponse<DeviceVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String area,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(size, 1), 100));
        Page<Device> result = deviceService.listDevices(status, type, area, pageable);
        return ApiResponse.ok(PageResponse.from(result.map(DeviceVO::from)));
    }

    /** 设备详情(GET /api/devices/{id}) */
    @GetMapping("/{id}")
    public ApiResponse<DeviceVO> get(@PathVariable("id") String deviceId) {
        return ApiResponse.ok(DeviceVO.from(deviceService.getDeviceOrThrow(deviceId)));
    }

    /** 新增设备(POST /api/devices) */
    @PostMapping
    public ApiResponse<DeviceVO> create(@Valid @RequestBody DeviceCreateRequest req) {
        Device.Position pos = null;
        if (req.position() != null) {
            pos = Device.Position.builder().lng(req.position().lng()).lat(req.position().lat()).build();
        }
        Device d = deviceService.createDevice(req.deviceId(), req.deviceType(), req.name(),
                req.model(), req.area(), pos);
        return ApiResponse.ok(DeviceVO.from(d));
    }

    /** 更新设备(PUT /api/devices/{id}) */
    @PutMapping("/{id}")
    public ApiResponse<DeviceVO> update(@PathVariable("id") String deviceId,
                                         @RequestBody DeviceUpdateRequest req) {
        Device.Position pos = null;
        if (req.position() != null) {
            pos = Device.Position.builder().lng(req.position().lng()).lat(req.position().lat()).build();
        }
        Device d = deviceService.updateDevice(deviceId, req.name(), req.model(),
                req.area(), pos, req.faultCode());
        return ApiResponse.ok(DeviceVO.from(d));
    }

    /** 删除设备(DELETE /api/devices/{id}) */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String deviceId) {
        deviceService.deleteDevice(deviceId);
        return ApiResponse.ok();
    }
}
