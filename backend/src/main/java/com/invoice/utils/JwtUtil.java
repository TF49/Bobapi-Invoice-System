package com.invoice.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    @Value("${jwt.remember-me-expiration}")
    private Long rememberMeExpiration;
    
    /**
     * 生成 token
     */
    public String generateToken(Long userId, String username, String role, Long authVersion) {
        return generateToken(userId, username, role, authVersion, false);
    }
    
    /**
     * 生成 token（支持记住我）
     */
    public String generateToken(Long userId, String username, String role,
                                Long authVersion, Boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("authVersion", authVersion);
        return createToken(claims, username, rememberMe);
    }
    
    /**
     * 创建 token
     */
    private String createToken(Map<String, Object> claims, String subject, Boolean rememberMe) {
        Date now = new Date();
        // 如果记住我，使用更长的有效期
        long expirationTime = (rememberMe != null && rememberMe) ? rememberMeExpiration : expiration;
        Date expiryDate = new Date(now.getTime() + expirationTime);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * 从 token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }
    
    /**
     * 从 token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userId", Long.class);
    }
    
    /**
     * 从 token 中获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("role", String.class);
    }
    
    /**
     * 验证 token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getAuthVersionFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("authVersion", Long.class);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * 从 token 中获取 Claims
     */
    private Claims getClaimsFromToken(String token) {
        return parseClaims(token);
    }
    
    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
