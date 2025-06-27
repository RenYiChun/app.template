package com.lrenyi.oauth2.service.oauth2.password;

import jakarta.servlet.http.HttpServletRequest;

public interface PreAuthenticationChecker {

    void check(HttpServletRequest request);
}