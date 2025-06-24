package com.lrenyi.oauth2.service.oauth2.password;

import com.lrenyi.oauth2.service.config.IdentifierType;
import com.lrenyi.template.api.rbac.model.Permission;
import com.lrenyi.template.api.rbac.model.Role;
import com.lrenyi.template.api.rbac.model.User;

import java.util.List;

public interface IRbacService {
    
    User findUserByIdentifier(String identifier, IdentifierType identifierType);
    
    List<Role> getRolesByUserId(String userId);
    
    List<Permission> getPermissionsByRoleId(String roleId);
}