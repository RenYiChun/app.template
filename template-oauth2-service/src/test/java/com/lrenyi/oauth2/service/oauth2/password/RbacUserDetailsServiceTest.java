package com.lrenyi.oauth2.service.oauth2.password;

import java.util.Collections;
import java.util.List;
import com.lrenyi.oauth2.service.config.IdentifierType;
import com.lrenyi.template.api.rbac.model.AppUser;
import com.lrenyi.template.api.rbac.model.Permission;
import com.lrenyi.template.api.rbac.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RbacUserDetailsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RbacUserDetailsServiceTest {

    @Mock
    private IRbacService rbacService;

    @Mock
    private ObjectProvider<IRbacService> rbacServiceObjectProvider;

    private RbacUserDetailsService service;

    @BeforeEach
    void setUp() {
        when(rbacServiceObjectProvider.getIfAvailable()).thenReturn(rbacService);
        service = new RbacUserDetailsService(rbacServiceObjectProvider);
    }

    @Test
    void loadUserByUsername_whenValidUser_returnsUserDetails() {
        AppUser<String> user = new AppUser<>();
        user.setId("uid-1");
        user.setUsername("testuser");
        user.setPassword("encoded");

        Role role = createMockRole("role-1");
        Permission perm1 = createMockPermission("read");
        Permission perm2 = createMockPermission("write");

        when(rbacService.findUserByIdentifier(eq("testuser"), eq(IdentifierType.USERNAME)))
                .thenAnswer(invocation -> user);
        when(rbacService.getRolesByUserId("uid-1"))
                .thenReturn(List.of(role));
        when(rbacService.getPermissionsByRoleId("role-1"))
                .thenReturn(List.of(perm1, perm2));

        UserDetails details = service.loadUserByUsername("USERNAME:testuser");

        assertNotNull(details);
        assertEquals("testuser", details.getUsername());
        assertEquals("encoded", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> "read".equals(a.getAuthority())));
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> "write".equals(a.getAuthority())));

        verify(rbacService).findUserByIdentifier("testuser", IdentifierType.USERNAME);
        verify(rbacService).getRolesByUserId("uid-1");
        verify(rbacService).getPermissionsByRoleId("role-1");
    }

    @Test
    void loadUserByUsername_whenRolesNull_usesEmptyList() {
        AppUser<String> user = new AppUser<>();
        user.setId("uid-1");
        user.setUsername("noroles");
        user.setPassword("encoded");

        when(rbacService.findUserByIdentifier(eq("noroles"), eq(IdentifierType.USERNAME)))
                .thenAnswer(invocation -> user);
        when(rbacService.getRolesByUserId("uid-1")).thenReturn(null);

        UserDetails details = service.loadUserByUsername("USERNAME:noroles");

        assertNotNull(details);
        assertEquals("noroles", details.getUsername());
        assertTrue(details.getAuthorities().isEmpty());
    }

    @Test
    void loadUserByUsername_whenInvalidFormat_throws() {
        assertThrows(UsernameNotFoundException.class, () ->
                service.loadUserByUsername("invalid"));
    }

    @Test
    void loadUserByUsername_whenInvalidIdentifierType_throws() {
        assertThrows(UsernameNotFoundException.class, () ->
                service.loadUserByUsername("INVALID:user"));
    }

    @Test
    void loadUserByUsername_whenUserNotFound_throws() {
        when(rbacService.findUserByIdentifier(eq("unknown"), eq(IdentifierType.USERNAME)))
                .thenReturn(null);

        assertThrows(UsernameNotFoundException.class, () ->
                service.loadUserByUsername("USERNAME:unknown"));
    }

    @Test
    void loadUserByUsername_whenEmployeeId_usesCorrectType() {
        AppUser<String> user = new AppUser<>();
        user.setId("uid-2");
        user.setUsername("emp");
        user.setPassword("pwd");

        when(rbacService.findUserByIdentifier(eq("E001"), eq(IdentifierType.EMPLOYEE_ID)))
                .thenAnswer(invocation -> user);
        when(rbacService.getRolesByUserId("uid-2")).thenReturn(Collections.emptyList());

        UserDetails details = service.loadUserByUsername("EMPLOYEE_ID:E001");

        assertNotNull(details);
        assertEquals("emp", details.getUsername());
    }

    private static Role createMockRole(String id) {
        Role role = org.mockito.Mockito.mock(Role.class);
        when(role.getId()).thenReturn(id);
        return role;
    }

    private static Permission createMockPermission(String perm) {
        Permission p = org.mockito.Mockito.mock(Permission.class);
        when(p.getPermission()).thenReturn(perm);
        return p;
    }
}
