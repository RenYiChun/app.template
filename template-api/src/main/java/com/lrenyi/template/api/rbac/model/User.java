package com.lrenyi.template.api.rbac.model;

import java.io.Serializable;
import java.util.List;

public interface User extends Serializable {
    String getId();
    
    void setId(String id);
    
    String getUsername();
    
    void setUsername(String username);

    String getEmployeeId();

    void setEmployeeId(String employeeId);

    String getPassword();

    void setPassword(String password);
    
    List<Role> getRoles();
    
    void setRoles(List<Role> roles);
}