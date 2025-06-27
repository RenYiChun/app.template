package com.lrenyi.template.api.rbac.model;

import java.io.Serializable;

public interface Permission extends Serializable {
    String getId();
    
    void setId(String id);
    
    String getName();
    
    void setName(String name);
    
    String getPermission();
    
    void setPermission(String permission);
}