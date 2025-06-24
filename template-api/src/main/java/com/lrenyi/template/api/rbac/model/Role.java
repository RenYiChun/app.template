package com.lrenyi.template.api.rbac.model;

import java.io.Serializable;
import java.util.List;

public interface Role extends Serializable {
    String getId();
    
    void setId(String id);
    
    String getName();
    
    void setName(String name);
    
    List<Permission> getPermissions();
    
    void setPermissions(List<Permission> permissions);
}