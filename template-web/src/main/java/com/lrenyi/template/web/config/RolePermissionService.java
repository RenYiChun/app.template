package com.lrenyi.template.web.config;

import java.util.Set;

public interface RolePermissionService {
    boolean check(Set<String> scopes, String requestUri);
}
