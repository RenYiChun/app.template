package com.lrenyi.oauth2.service.oauth2.password;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

public interface LoginNameUserDetailService {
    Map<String, LoginNameUserDetailService> ALL_LOGIN_NAME_TYPE = new HashMap<>();
    
    String loginNameType();
    
    UserDetails loadUserDetail(String code) throws AuthenticationException;
}
