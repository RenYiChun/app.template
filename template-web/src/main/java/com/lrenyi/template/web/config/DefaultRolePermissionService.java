package com.lrenyi.template.web.config;

import com.lrenyi.template.web.config.RolePermissionService;
import java.util.Set;

public class DefaultRolePermissionService implements RolePermissionService {
    @Override
    public boolean check(Set<String> scopes, String requestUri) {
        return true;
    }
}
