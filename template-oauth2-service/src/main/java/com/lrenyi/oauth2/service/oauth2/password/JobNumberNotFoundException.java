package com.lrenyi.oauth2.service.oauth2.password;

import org.springframework.security.core.AuthenticationException;

public class JobNumberNotFoundException extends AuthenticationException {
    public JobNumberNotFoundException(String msg) {
        super(msg);
    }
    
    public JobNumberNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }
}