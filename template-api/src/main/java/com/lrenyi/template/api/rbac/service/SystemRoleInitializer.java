package com.lrenyi.template.api.rbac.service;

import com.lrenyi.template.api.rbac.model.Permission;
import com.lrenyi.template.api.rbac.model.SystemPermission;
import com.lrenyi.template.api.rbac.model.SystemRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 系统角色初始化服务，在应用启动时自动创建预定义的系统角色和权限。
 */
@Slf4j
@Service
public class SystemRoleInitializer implements CommandLineRunner {
    
    private final IRbacAdminService rbacAdminService;
    
    @Autowired
    public SystemRoleInitializer(ObjectProvider<IRbacAdminService> rbacAdminServiceObjectProvider) {
        this.rbacAdminService = rbacAdminServiceObjectProvider.getIfAvailable();
    }
    
    @Override
    @Transactional
    public void run(String... args) {
        if (rbacAdminService == null) {
            log.warn("rbac admin service is null, cannot initialize system role");
            return;
        }
        for (SystemRole systemRole : SystemRole.values()) {
            // 检查角色是否存在，如果不存在则创建
            rbacAdminService.findRoleByCode(systemRole.getRoleCode()).orElseGet(() -> {
                Set<Permission> permissions = new HashSet<>();
                for (SystemPermission p : systemRole.getPermissions()) {
                    // 检查权限是否存在，不存在则创建
                    Permission perm = rbacAdminService.findPermissionByName(p.getPermission())
                                                      .orElseGet(() -> rbacAdminService.createPermission(p));
                    permissions.add(perm);
                }
                return rbacAdminService.createRoleWithPermissions(systemRole, permissions);
            });
        }
    }
}