package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.invoice.dto.AdminUserPageResponse;
import com.invoice.dto.AdminUserResponse;
import com.invoice.dto.AdminUserStats;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Value("${app.default-clerk.username:clerk}")
    private String defaultClerkUsername;

    @Value("${app.default-clerk.password:}")
    private String defaultClerkPassword;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserQuotaService userQuotaService;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, UserQuotaService userQuotaService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userQuotaService = userQuotaService;
    }
    
    /**
     * 根据用户名查找用户
     */
    public User findByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    public User findById(Long id) {
        return userMapper.selectById(id);
    }
    
    /**
     * 创建用户
     */
    @Transactional
    public User createUser(String username, String password, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        user.setAuthVersion(0L);
        try {
            userMapper.insert(user);
            // 为新用户创建初始额度记录
            userQuotaService.createInitialQuota(user.getId());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "用户名已存在");
        }
        return user;
    }

    public AdminUserPageResponse getAdminUsers(int page, int pageSize, String keyword,
                                               String role, Boolean enabled, Long currentUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword.trim());
        }
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        if (enabled != null) {
            wrapper.eq(User::getEnabled, enabled);
        }
        wrapper.orderByDesc(User::getCreatedAt).orderByDesc(User::getId);

        Page<User> result = userMapper.selectPage(Page.of(page, pageSize), wrapper);
        List<AdminUserResponse> users = result.getRecords().stream()
                .map(user -> AdminUserResponse.from(user, currentUserId))
                .toList();
        return new AdminUserPageResponse(
                users,
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages(),
                getUserStats()
        );
    }

    public AdminUserResponse createAdminUser(String username, String password, String role, Long currentUserId) {
        return AdminUserResponse.from(createUser(username, password, role), currentUserId);
    }

    @Transactional
    public AdminUserResponse updateRole(Long targetUserId, String role, Long currentUserId) {
        List<Long> enabledAdminIds = userMapper.selectEnabledAdminIdsForUpdate();
        User target = requireUserForUpdate(targetUserId);
        rejectSelfRoleOrStatus(targetUserId, currentUserId);
        if (role.equals(target.getRole())) {
            return AdminUserResponse.from(target, currentUserId);
        }
        if ("ADMIN".equals(target.getRole()) && Boolean.TRUE.equals(target.getEnabled())
                && enabledAdminIds.size() <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, 40903, "系统必须保留至少一个启用的管理员");
        }

        target.setRole(role);
        target.setAuthVersion(nextAuthVersion(target));
        userMapper.updateById(target);
        return AdminUserResponse.from(target, currentUserId);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long targetUserId, boolean enabled, Long currentUserId) {
        List<Long> enabledAdminIds = userMapper.selectEnabledAdminIdsForUpdate();
        User target = requireUserForUpdate(targetUserId);
        rejectSelfRoleOrStatus(targetUserId, currentUserId);
        if (Boolean.valueOf(enabled).equals(target.getEnabled())) {
            return AdminUserResponse.from(target, currentUserId);
        }
        if (!enabled && "ADMIN".equals(target.getRole()) && Boolean.TRUE.equals(target.getEnabled())
                && enabledAdminIds.size() <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, 40903, "系统必须保留至少一个启用的管理员");
        }

        target.setEnabled(enabled);
        target.setAuthVersion(nextAuthVersion(target));
        userMapper.updateById(target);
        return AdminUserResponse.from(target, currentUserId);
    }

    @Transactional
    public AdminUserResponse resetPassword(Long targetUserId, String password, Long currentUserId) {
        User target = requireUserForUpdate(targetUserId);
        target.setPassword(passwordEncoder.encode(password));
        target.setAuthVersion(nextAuthVersion(target));
        userMapper.updateById(target);
        return AdminUserResponse.from(target, currentUserId);
    }
    
    /**
     * 验证密码
     */
    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    private AdminUserStats getUserStats() {
        long total = userMapper.selectCount(null);
        long enabled = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEnabled, true));
        long disabled = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEnabled, false));
        long admins = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
        long clerks = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "INVOICE_CLERK"));
        return new AdminUserStats(total, enabled, disabled, admins, clerks);
    }

    private User requireUserForUpdate(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "用户不存在");
        }
        return user;
    }

    private void rejectSelfRoleOrStatus(Long targetUserId, Long currentUserId) {
        if (targetUserId.equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40302, "不能修改当前账号的角色或状态");
        }
    }

    private long nextAuthVersion(User user) {
        return user.getAuthVersion() == null ? 1L : user.getAuthVersion() + 1L;
    }

    /**
     * 初始化默认开票员账号。
     * 通过环境变量 app.default-clerk.username / app.default-clerk.password 配置。
     * 若密码未配置则跳过初始化；创建成功后输出安全警告，提示尽快修改密码。
     */
    @PostConstruct
    public void initDefaultClerk() {
        if (defaultClerkPassword == null || defaultClerkPassword.isBlank()) {
            logger.warn("[安全] 未配置默认开票员密码（app.default-clerk.password），跳过自动初始化。"
                    + " 请手动在管理后台创建开票员账号。");
            return;
        }
        if (findByUsername(defaultClerkUsername) == null) {
            try {
                User clerk = new User();
                clerk.setUsername(defaultClerkUsername);
                clerk.setPassword(passwordEncoder.encode(defaultClerkPassword));
                clerk.setRole("INVOICE_CLERK");
                clerk.setEnabled(true);
                clerk.setAuthVersion(0L);
                userMapper.insert(clerk);
                logger.warn("[安全] 默认开票员账号 '{}' 已自动创建，请登录后台立即修改密码！",
                        defaultClerkUsername);
            } catch (DuplicateKeyException exception) {
                // 并发启动时忽略重复键异常
            }
        }
    }
}
