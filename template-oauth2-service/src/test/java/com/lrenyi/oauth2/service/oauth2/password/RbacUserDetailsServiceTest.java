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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RbacUserDetailsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RbacUserDetailsServiceTest {

    private static final String TEST_USER = "testuser";
    private static final String NO_ROLES_USER = "noroles";
    private static final String ENCODED_PASSWORD = "encoded";

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
    void loadUserByUsernameWhenValidUserReturnsUserDetails() {
        RbacUserCredentials credentials = new RbacUserCredentials(TEST_USER, ENCODED_PASSWORD, List.of("read", "write"));
        
        when(rbacService.loadUserCredentials(TEST_USER, IdentifierType.USERNAME)).thenReturn(credentials);
        
        UserDetails details = service.loadUserByUsername("USERNAME:" + TEST_USER);
        
        assertNotNull(details);
        assertEquals(TEST_USER, details.getUsername());
        assertEquals(ENCODED_PASSWORD, details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "read".equals(a.getAuthority())));
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "write".equals(a.getAuthority())));
        
        verify(rbacService).loadUserCredentials(TEST_USER, IdentifierType.USERNAME);
    }
    
    @Test
    void loadUserByUsernameWhenEmptyPermissionsUsesEmptyList() {
        RbacUserCredentials credentials = new RbacUserCredentials(NO_ROLES_USER, ENCODED_PASSWORD, Collections.emptyList());
        
        when(rbacService.loadUserCredentials(NO_ROLES_USER, IdentifierType.USERNAME)).thenReturn(credentials);
        
        UserDetails details = service.loadUserByUsername("USERNAME:" + NO_ROLES_USER);
        
        assertNotNull(details);
        assertEquals(NO_ROLES_USER, details.getUsername());
        assertTrue(details.getAuthorities().isEmpty());
    }
    
    @Test
    void loadUserByUsernameWhenInvalidFormatThrows() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("invalid"));
    }
    
    @Test
    void loadUserByUsernameWhenInvalidIdentifierTypeThrows() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("INVALID:user"));
    }
    
    @Test
    void loadUserByUsernameWhenUserNotFoundThrows() {
        when(rbacService.loadUserCredentials("unknown", IdentifierType.USERNAME)).thenReturn(null);
        
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("USERNAME:unknown"));
    }
    
    @Test
    void loadUserByUsernameWhenEmployeeIdUsesCorrectType() {
        RbacUserCredentials credentials = new RbacUserCredentials("emp", "pwd", List.of());
        
        when(rbacService.loadUserCredentials("E001", IdentifierType.EMPLOYEE_ID)).thenReturn(credentials);
        
        UserDetails details = service.loadUserByUsername("EMPLOYEE_ID:E001");
        
        assertNotNull(details);
        assertEquals("emp", details.getUsername());
    }
}
