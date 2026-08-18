package com.invoice.security;

public record JwtUserPrincipal(Long userId, String username, String role) {
}
