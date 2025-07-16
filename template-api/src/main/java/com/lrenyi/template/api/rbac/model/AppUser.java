package com.lrenyi.template.api.rbac.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private int id;
    private String username;
    private String password;
    private String employeeId;
    private List<String> roles;
}