package com.invoice.dto;

import com.invoice.entity.User;
import com.invoice.entity.UserQuota;
import com.invoice.entity.UserSealQuota;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

public record AdminUserResponse(
        Long id,
        String username,
        String role,
        String apiKey,
        Boolean apiKeyEnabled,
        boolean enabled,
        String remark,
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
            BigDecimal totalDeducted,
            BigDecimal sealBalance,
            BigDecimal sealTotalRecharged,
            BigDecimal sealTotalDeducted,
            Integer sealAvailableCount
    ) {
        public static final QuotaSummary ZERO = new QuotaSummary(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0
        );

        public QuotaSummary(BigDecimal balance, BigDecimal totalRecharged, BigDecimal totalDeducted) {
            this(balance, totalRecharged, totalDeducted, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }

        public static QuotaSummary from(UserQuota q) {
            return from(q, null);
        }

        public static QuotaSummary from(UserQuota q, UserSealQuota sq) {
            if (q == null && sq == null) {
                return ZERO;
            }
            BigDecimal bal = q != null && q.getBalance() != null ? q.getBalance() : BigDecimal.ZERO;
            BigDecimal rec = q != null && q.getTotalRecharged() != null ? q.getTotalRecharged() : BigDecimal.ZERO;
            BigDecimal ded = q != null && q.getTotalDeducted() != null ? q.getTotalDeducted() : BigDecimal.ZERO;

            BigDecimal sBal = sq != null && sq.getBalance() != null ? sq.getBalance() : BigDecimal.ZERO;
            BigDecimal sRec = sq != null && sq.getTotalRecharged() != null ? sq.getTotalRecharged() : BigDecimal.ZERO;
            BigDecimal sDed = sq != null && sq.getTotalDeducted() != null ? sq.getTotalDeducted() : BigDecimal.ZERO;
            int count = sBal.divide(new BigDecimal("10.00"), 0, RoundingMode.DOWN).intValue();

            return new QuotaSummary(bal, rec, ded, sBal, sRec, sDed, Math.max(0, count));
        }
    }

    /** Convenience factory without quota (admin / clerk rows). */
    public static AdminUserResponse from(User user, Long currentUserId) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getApiKey(),
                user.getApiKeyEnabled(),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getRemark(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                Objects.equals(user.getId(), currentUserId),
                null
        );
    }

    /** Factory with quota (USER-role rows). */
    public static AdminUserResponse from(User user, Long currentUserId, UserQuota quota) {
        return from(user, currentUserId, quota, null);
    }

    public static AdminUserResponse from(User user, Long currentUserId, UserQuota quota, UserSealQuota sealQuota) {
        QuotaSummary summary = null;
        if ("USER".equals(user.getRole())) {
            summary = QuotaSummary.from(quota, sealQuota);
        }
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getApiKey(),
                user.getApiKeyEnabled(),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getRemark(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                Objects.equals(user.getId(), currentUserId),
                summary
        );
    }
}
