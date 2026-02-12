package com.lrenyi.template.api.rbac.model;

import org.junit.jupiter.api.Test;

import static com.lrenyi.template.api.rbac.model.SystemPermission.AUDIT_READ;
import static com.lrenyi.template.api.rbac.model.SystemPermission.USER_CREATE;
import static com.lrenyi.template.api.rbac.model.SystemPermission.USER_READ;
import static com.lrenyi.template.api.rbac.model.SystemPermission.USER_UPDATE;
import static com.lrenyi.template.api.rbac.model.SystemPermission.USER_DELETE;
import static com.lrenyi.template.api.rbac.model.SystemPermission.USER_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SystemRole 单元测试
 */
class SystemRoleTest {

    @Test
    void userAdmin_hasUserManagementPermissions() {
        SystemRole role = SystemRole.USER_ADMIN;
        assertEquals("UserAdmin", role.getRoleCode());
        assertEquals("用户管理员", role.getRoleName());
        assertTrue(role.getPermissions().contains(USER_CREATE));
        assertTrue(role.getPermissions().contains(USER_READ));
        assertTrue(role.getPermissions().contains(USER_UPDATE));
        assertTrue(role.getPermissions().contains(USER_DELETE));
        assertTrue(role.getPermissions().contains(USER_LIST));
        assertEquals(5, role.getPermissions().size());
    }

    @Test
    void authorizationAdmin_hasRoleAndPermissionManagement() {
        SystemRole role = SystemRole.AUTHORIZATION_ADMIN;
        assertEquals("AuthorizationAdmin", role.getRoleCode());
        assertEquals("授权管理员", role.getRoleName());
        assertNotNull(role.getPermissions());
        assertFalse(role.getPermissions().isEmpty());
    }

    @Test
    void auditAdmin_hasAuditReadOnly() {
        SystemRole role = SystemRole.AUDIT_ADMIN;
        assertEquals("AuditAdmin", role.getRoleCode());
        assertEquals("审计管理员", role.getRoleName());
        assertEquals(1, role.getPermissions().size());
        assertTrue(role.getPermissions().contains(AUDIT_READ));
    }

    @Test
    void getPermissions_returnsUnmodifiableSet() {
        SystemRole role = SystemRole.USER_ADMIN;
        assertThrows(UnsupportedOperationException.class, () ->
                role.getPermissions().add(SystemPermission.USER_CREATE));
    }
}
