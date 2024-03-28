package com.lrenyi.oauth2.service;

import com.alibaba.fastjson2.JSONObject;
import com.lrenyi.template.core.util.MCode;
import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.core.util.StringUtils;
import com.lrenyi.template.core.util.TokenBean;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class DefaultTemplateOauthService implements TemplateOauthService {
    
    private RestTemplate template;
    private Environment environment;
    private OAuth2AuthorizationService oAuth2AuthorizationService;
    
    @Autowired
    public void setoAuth2AuthorizationService(OAuth2AuthorizationService oAuth2AuthorizationService) {
        this.oAuth2AuthorizationService = oAuth2AuthorizationService;
    }
    
    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    @Autowired
    public void setTemplate(RestTemplate restTemplate) {
        this.template = restTemplate;
    }
    
    @Override
    public Result<TokenBean> login(MultiValueMap<String, String> body, HttpHeaders header) {
        return login("http", body, header);
    }
    
    @Override
    public Result<TokenBean> login(String type, MultiValueMap<String, String> body, HttpHeaders header) {
        header.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, header);
        TokenBean tokenBean;
        Result<TokenBean> result = new Result<>();
        String port = environment.getProperty("server.port", "8080");
        String context = environment.getProperty("server.servlet.context-path");
        String oauthPath = "/oauth2/token";
        if (com.lrenyi.template.core.util.StringUtils.hasLength(context)) {
            oauthPath = context + oauthPath;
        }
        try {
            String host = String.format("%s://localhost:%s%s", type, port, oauthPath);
            JSONObject strValue = template.postForObject(host, entity, JSONObject.class);
            if (strValue == null) {
                result.setCode(MCode.NO_PERMISSIONS.getCode());
                result.setMessage("oauth认证返回数据异常");
                return result;
            }
            result.setCode(MCode.SUCCESS.getCode());
            tokenBean = strValue.to(TokenBean.class);
            result.setData(tokenBean);
            return result;
        } catch (Throwable cause) {
            String message = "调用/oauth2/token过程中发送异常";
            log.error(message, cause);
            result.makeThrowable(cause, message);
            return result;
        }
    }
    
    @Override
    public Result<?> logout(String type, HttpHeaders header) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        header.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, header);
        String port = environment.getProperty("server.port", "8080");
        String context = environment.getProperty("server.servlet.context-path");
        String oauthPath = "/logout";
        if (com.lrenyi.template.core.util.StringUtils.hasLength(context)) {
            oauthPath = context + oauthPath;
        }
        try {
            String host = String.format("%s://localhost:%s%s", type, port, oauthPath);
            Result<?> result = template.postForObject(host, entity, Result.class);
            if (result == null) {
                return Result.getError(false, "退出登录失败，oauth返回为null");
            }
            return result;
        } catch (Throwable cause) {
            String message = "调用/oauth/token过程中发送异常";
            log.error(message, cause);
            Result<Boolean> result = new Result<>();
            result.setData(false);
            result.makeThrowable(cause, message);
            return result;
        }
    }
    
    @Override
    public Map<String, String> getUserNameByToken(String token) {
        Map<String, String> result = new HashMap<>();
        OAuth2Authorization authorization = oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            return result;
        }
        String username = authorization.getAttribute("username");
        if (!StringUtils.hasLength(username)) {
            username = authorization.getPrincipalName();
        }
        result.put("username", username);
        return result;
    }
}
