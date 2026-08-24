package com.invoice.security;

import com.invoice.entity.User;
import com.invoice.mapper.UserMapper;
import com.invoice.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        String token = null;
        JwtUserPrincipal principal = null;
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.parseClaims(token);
                Long userId = JwtUtil.getLongClaim(claims, "userId");
                Long tokenAuthVersion = JwtUtil.getLongClaim(claims, "authVersion");
                if (userId != null && tokenAuthVersion != null) {
                    User user = userMapper.selectById(userId);
                    if (user != null && Boolean.TRUE.equals(user.getEnabled())
                            && tokenAuthVersion.equals(user.getAuthVersion())) {
                        principal = new JwtUserPrincipal(
                                user.getId(), user.getUsername(), user.getRole(), user.getAuthVersion());
                    } else {
                        log.warn("JWT user check failed for URI {}: userExists={}, enabled={}, tokenAuthVersion={}, dbAuthVersion={}",
                                request.getRequestURI(), user != null, user != null ? user.getEnabled() : null,
                                tokenAuthVersion, user != null ? user.getAuthVersion() : null);
                    }
                } else {
                    log.warn("JWT token missing userId or authVersion claim for URI {}", request.getRequestURI());
                }
            } catch (Exception e) {
                log.warn("JWT token validation exception for URI {}: {}", request.getRequestURI(), e.getMessage());
            }
        } else if (!request.getRequestURI().startsWith("/api/auth/") && !request.getRequestURI().startsWith("/auth/")) {
            log.warn("Authorization header missing or invalid format ('{}') for request: {} {}",
                    authHeader, request.getMethod(), request.getRequestURI());
        }
        
        if (principal != null && principal.role() != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + principal.role()))
                );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        
        filterChain.doFilter(request, response);
    }
}
