package com.lrenyi.template.core.util;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenBean {
    @JSONField(serialize = false)
    private String id;
    private String access_token;
    private String refresh_token;
    private String token_type;
    private String expires_in;
    private String error = "";
    private String error_description = "";
    @JSONField(serialize = false)
    private String userName;
}
