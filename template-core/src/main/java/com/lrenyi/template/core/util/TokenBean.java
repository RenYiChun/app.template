package com.lrenyi.template.core.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenBean {
    private String id;
    private String access_token;
    private String refresh_token;
    private String token_type;
    private String expires_in;
    private String error = "";
    private String error_description = "";
    private String userName;
}
