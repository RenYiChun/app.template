package com.lrenyi.oauth2.service;

import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.core.util.TokenBean;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

public interface TemplateOauthService {
    
    Result<TokenBean> login(MultiValueMap<String, String> body, HttpHeaders header);
    
    Result<TokenBean> login(String type, MultiValueMap<String, String> body, HttpHeaders header);
    
    Result<?> logout(String type, HttpHeaders header);
    
    Map<String, String> getUserNameByToken(String token);
}
