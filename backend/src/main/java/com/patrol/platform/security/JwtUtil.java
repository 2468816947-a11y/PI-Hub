package com.patrol.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析(HS256)。密钥与有效期见 application.yml 的 patrol.jwt.*
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtUtil(@Value("${patrol.jwt.secret}") String secret,
                   @Value("${patrol.jwt.expire-seconds}") long expireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /** 签发令牌, role 写入 claims */
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** 解析并校验令牌, 无效(过期/伪造)抛 JwtException */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
