package com.lrenyi.oauth2.service.oauth2.password;

import java.util.Collections;
import java.util.List;
import com.lrenyi.oauth2.service.config.IdentifierType;
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
        RbacUserCredentials credentials = new RbacUserCredentials("testuser", "encoded", List.of("read", "write"));
        
        when(rbacService.loadUserCredentials(eq("testuser"), eq(IdentifierType.USERNAME))).thenReturn(credentials);
        
        UserDetails details = service.loadUserByUsername("USERNAME:testuser");
        
        assertNotNull(details);
        assertEquals("testuser", details.getUsername());
        assertEquals("encoded", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "read".equals(a.getAuthority())));
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "write".equals(a.getAuthority())));
        
        verify(rbacService).loadUserCredentials("testuser", IdentifierType.USERNAME);
    }
    
    @Test
    void loadUserByUsername_whenEmptyPermissions_usesEmptyList() {
        RbacUserCredentials credentials = new RbacUserCredentials("noroles", "encoded", Collections.emptyList());
        
        when(rbacService.loadUserCredentials(eq("noroles"), eq(IdentifierType.USERNAME))).thenReturn(credentials);
        
        UserDetails details = service.loadUserByUsername("USERNAME:noroles");
        
        assertNotNull(details);
        assertEquals("noroles", details.getUsername());
        assertTrue(details.getAuthorities().isEmpty());
    }
    
    @Test
    void loadUserByUsername_whenInvalidFormat_throws() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("invalid"));
    }
    
    @Test
    void loadUserByUsername_whenInvalidIdentifierType_throws() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("INVALID:user"));
    }
    
    @Test
    void loadUserByUsername_whenUserNotFound_throws() {
        when(rbacService.loadUserCredentials(eq("unknown"), eq(IdentifierType.USERNAME))).thenReturn(null);
        
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("USERNAME:unknown"));
    }
    
    @Test
    void loadUserByUsername_whenEmployeeId_usesCorrectType() {
        RbacUserCredentials credentials = new RbacUserCredentials("emp", "pwd", List.of());
        
        when(rbacService.loadUserCredentials(eq("E001"), eq(IdentifierType.EMPLOYEE_ID))).thenReturn(credentials);
        
        UserDetails details = service.loadUserByUsername("EMPLOYEE_ID:E001");
        
        assertNotNull(details);
        assertEquals("emp", details.getUsername());
    }
}
