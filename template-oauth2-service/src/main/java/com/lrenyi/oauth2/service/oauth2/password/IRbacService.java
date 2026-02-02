package com.lrenyi.oauth2.service.oauth2.password;

import java.util.List;
import com.lrenyi.oauth2.service.config.IdentifierType;
import com.lrenyi.template.api.rbac.model.AppUser;
import com.lrenyi.template.api.rbac.model.Permission;
import com.lrenyi.template.api.rbac.model.Role;

public interface IRbacService {
    
    AppUser<?> findUserByIdentifier(String identifier, IdentifierType identifierType);
    
    <T> List<Role> getRolesByUserId(T userId);
    
    <T> List<Permission> getPermissionsByRoleId(T roleId);
}