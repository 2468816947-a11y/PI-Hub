package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import com.patrol.platform.dto.PageResponse;
import com.patrol.platform.dto.TaskDto.TaskCreateRequest;
import com.patrol.platform.dto.TaskDto.TaskDetailResponse;
import com.patrol.platform.dto.TaskDto.TaskVO;
import com.patrol.platform.entity.PatrolTask;
import com.patrol.platform.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理(接口文档 §2.3 共 3 个接口)。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 创建并下发(POST /api/tasks) */
    @PostMapping
    public ApiResponse<TaskVO> create(@Valid @RequestBody TaskCreateRequest req) {
        PatrolTask task = taskService.createAndDispatchTask(
                req.name(), req.taskType(), req.area(), req.deviceIds(), req.params());
        return ApiResponse.ok(TaskVO.from(task));
    }

    /** 分页查询(GET /api/tasks) */
    @GetMapping
    public ApiResponse<PageResponse<TaskVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(size, 1), 100));
        Page<PatrolTask> result = taskService.listTasks(status, type, pageable);
        return ApiResponse.ok(PageResponse.from(result.map(TaskVO::from)));
    }

    /** 任务详情(GET /api/tasks/{id}) 含 deviceProgress */
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailResponse> get(@PathVariable("id") String taskId) {
        PatrolTask task = taskService.getTaskOrThrow(taskId);
        return ApiResponse.ok(new TaskDetailResponse(
                TaskVO.from(task),
                taskService.getDeviceProgress(task)));
    }
}
