package com.invoice.dto;

import com.invoice.entity.User;
import com.invoice.entity.UserQuota;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record AdminUserResponse(
        Long id,
        String username,
        String role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean self,
        QuotaSummary quota
) {
    /**
     * Inline quota snapshot embedded in the user-list response.
     * Only populated for USER-role accounts; null for ADMIN / INVOICE_CLERK.
     */
    public record QuotaSummary(
            BigDecimal balance,
            BigDecimal totalRecharged,
            BigDecimal totalDeducted
    ) {
        public static final QuotaSummary ZERO = new QuotaSummary(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        public static QuotaSummary from(UserQuota q) {
            if (q == null) {
                return ZERO;
            }
            return new QuotaSummary(
                    q.getBalance() != null ? q.getBalance() : BigDecimal.ZERO,
                    q.getTotalRecharged() != null ? q.getTotalRecharged() : BigDecimal.ZERO,
                    q.getTotalDeducted() != null ? q.getTotalDeducted() : BigDecimal.ZERO
            );
        }
    }

    /** Convenience factory without quota (admin / clerk rows). */
    public static AdminUserResponse from(User user, Long currentUserId) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                Objects.equals(user.getId(), currentUserId),
                null
        );
    }

    /** Factory with quota (USER-role rows). */
    public static AdminUserResponse from(User user, Long currentUserId, UserQuota quota) {
        QuotaSummary summary = null;
        if ("USER".equals(user.getRole())) {
            summary = QuotaSummary.from(quota);
        }
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                Objects.equals(user.getId(), currentUserId),
                summary
        );
    }
}
