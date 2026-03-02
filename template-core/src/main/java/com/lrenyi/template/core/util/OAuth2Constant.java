package com.lrenyi.template.core.util;

public class OAuth2Constant {
    public static final String LOGIN_USER_NAME_TYPE_KEY = "LOGIN_IDENTIFIER_TYPE";
    public static final String LOGIN_FAIL_OF_PASSWORD = "950";  //NOSONAR
    public static final String PRE_AUTHENTICATION_FAIL = "951";
    public static final String TOKEN_ID_KEY_IN_REDIS = "token-id";
    /** OAuth2 资源所有者密码凭据授权类型标识（RFC 6749 4.3），非凭证。 */
    public static final String GRANT_TYPE_PASSWORD = "authorization_password"; // NOSONAR
    
    private OAuth2Constant() {
    
    }
}
