package com.lrenyi.oauth2.service.oauth2.password;

import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;

@Getter
public enum LoginNameType {
    USER_NAME(OAuth2TokenIntrospectionClaimNames.USERNAME),
    JOB_NUMBER("jobNumber");
    
    private final String code;
    
    LoginNameType(String code) {
        this.code = code;
    }
}
