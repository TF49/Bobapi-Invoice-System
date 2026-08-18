package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.invoice.dto.AdminUserPageResponse;
import com.invoice.dto.AdminUserResponse;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder);
    }

    @Test
    void paginatesFiltersAndReturnsSafeResponsesWithStats() {
        User user = user(2L, "alice", "USER", true, 0L);
        Page<User> page = Page.of(2, 10);
        page.setRecords(List.of(user));
        page.setTotal(15);
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(userMapper.selectCount(any())).thenReturn(15L, 12L, 3L, 2L);

        AdminUserPageResponse response = userService.getAdminUsers(
                2, 10, " ali ", "USER", true, 99L);

        assertEquals(15, response.total());
        assertEquals(2, response.page());
        assertEquals(2, response.totalPages());
        assertEquals(12, response.stats().enabledUsers());
        assertEquals("alice", response.users().get(0).username());
        assertTrue(!response.users().get(0).self());
        verify(userMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void createsEnabledUserWithEncryptedPasswordAndInitialVersion() {
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");

        User created = userService.createUser("alice", "pass123", "USER");

        assertEquals("encoded", created.getPassword());
        assertEquals(Boolean.TRUE, created.getEnabled());
        assertEquals(0L, created.getAuthVersion());
        verify(userMapper).insert(created);
    }

    @Test
    void mapsConcurrentUsernameConflictToBusinessConflict() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        doThrow(new DuplicateKeyException("duplicate")).when(userMapper).insert(any(User.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.createUser("alice", "pass123", "USER"));

        assertEquals(409, exception.getStatus().value());
        assertEquals(40901, exception.getCode());
    }

    @Test
    void changesRoleAndIncrementsAuthenticationVersion() {
        User target = user(2L, "alice", "USER", true, 4L);
        when(userMapper.selectEnabledAdminIdsForUpdate()).thenReturn(List.of(1L));
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(target);

        AdminUserResponse response = userService.updateRole(2L, "ADMIN", 1L);

        assertEquals("ADMIN", response.role());
        assertEquals(5L, target.getAuthVersion());
        verify(userMapper).updateById(target);
    }

    @Test
    void treatsRoleAndStatusTargetStateAsNaturallyIdempotent() {
        User target = user(2L, "alice", "USER", true, 3L);
        when(userMapper.selectEnabledAdminIdsForUpdate()).thenReturn(List.of(1L));
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(target);

        userService.updateRole(2L, "USER", 1L);
        userService.updateStatus(2L, true, 1L);

        verify(userMapper, never()).updateById(any(User.class));
        assertEquals(3L, target.getAuthVersion());
    }

    @Test
    void rejectsSelfRoleAndStatusChanges() {
        User self = user(1L, "admin", "ADMIN", true, 0L);
        when(userMapper.selectEnabledAdminIdsForUpdate()).thenReturn(List.of(1L));
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(self);

        BusinessException roleError = assertThrows(BusinessException.class,
                () -> userService.updateRole(1L, "USER", 1L));
        BusinessException statusError = assertThrows(BusinessException.class,
                () -> userService.updateStatus(1L, false, 1L));

        assertEquals(40302, roleError.getCode());
        assertEquals(40302, statusError.getCode());
    }

    @Test
    void protectsTheLastEnabledAdministrator() {
        User target = user(2L, "admin2", "ADMIN", true, 0L);
        when(userMapper.selectEnabledAdminIdsForUpdate()).thenReturn(List.of(2L));
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(target);

        BusinessException demoteError = assertThrows(BusinessException.class,
                () -> userService.updateRole(2L, "USER", 1L));
        BusinessException disableError = assertThrows(BusinessException.class,
                () -> userService.updateStatus(2L, false, 1L));

        assertEquals(40903, demoteError.getCode());
        assertEquals(40903, disableError.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void allowsResettingOwnPasswordAndInvalidatesExistingCredentials() {
        User self = user(1L, "admin", "ADMIN", true, 6L);
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(self);
        when(passwordEncoder.encode("newpass9")).thenReturn("new-hash");

        AdminUserResponse response = userService.resetPassword(1L, "newpass9", 1L);

        assertTrue(response.self());
        assertEquals("new-hash", self.getPassword());
        assertEquals(7L, self.getAuthVersion());
        verify(userMapper).updateById(self);
    }

    @Test
    void returnsNotFoundForMissingTarget() {
        when(userMapper.selectEnabledAdminIdsForUpdate()).thenReturn(List.of(1L));
        when(userMapper.selectByIdForUpdate(404L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.updateRole(404L, "USER", 1L));

        assertEquals(40403, exception.getCode());
    }

    private User user(Long id, String username, String role, boolean enabled, long authVersion) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("hash");
        user.setRole(role);
        user.setEnabled(enabled);
        user.setAuthVersion(authVersion);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        return user;
    }
}
