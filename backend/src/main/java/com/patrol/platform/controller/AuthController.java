package com.patrol.platform.controller;

import com.patrol.platform.common.ApiResponse;
import com.patrol.platform.common.BusinessException;
import com.patrol.platform.common.ErrorCode;
import com.patrol.platform.dto.LoginRequest;
import com.patrol.platform.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录认证(接口文档 2.1.1)
 * TODO: 接入 Mongo 用户表后, 将内置账号替换为数据库校验 + BCrypt 密码
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;

    /** 开发期内置账号 */
    private static final Map<String, DevUser> DEV_USERS = Map.of(
            "admin", new DevUser("admin", "admin123", "ADMIN", "巡检管理员"));

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        DevUser user = DEV_USERS.get(req.username());
        if (user == null || !user.password().equals(req.password())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.username(), user.role());
        return ApiResponse.ok(new LoginResponse(
                token, jwtUtil.getExpireSeconds(),
                new LoginResponse.User(user.username(), user.realName(), user.role())));
    }

    private record DevUser(String username, String password, String role, String realName) {
    }

    public record LoginResponse(String token, long expiresIn, User user) {
        public record User(String username, String realName, String role) {
        }
    }
}
