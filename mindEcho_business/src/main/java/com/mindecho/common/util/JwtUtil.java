package com.mindecho.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private Long expire;

    /**
     * 生成 JWT Token（userId 为 UUID）
     */
    public String generateToken(UUID userId) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getKey())
                .compact();
    }

    /**
     * 从 Token 解析用户 ID（返回 UUID）
     */
    public UUID getUserId(String token) {
        Claims claims = getClaims(token);
        if (claims == null) {
            return null;
        }
        String subject = claims.getSubject();
        if (subject == null) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in JWT subject: {}", subject);
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        return getClaims(token) != null;
    }

    /**
     * 获取 Claims
     */
    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT token invalid: {}", e.getMessage());
        }
        return null;
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

