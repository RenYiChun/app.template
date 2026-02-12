package com.lrenyi.template.cloud.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.lrenyi.template.core.TemplateConfigProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class OauthUtilService {
    public static final Map<String, String> tokenCacheMap = new ConcurrentHashMap<>();
    private static final Map<String, LocalDateTime> expiresCacheMap = new ConcurrentHashMap<>();
    @Resource
    private TemplateConfigProperties templateConfigProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    
    public String fetchToken(String host, String clientId, String clientSecret) {
        String token = fetchTokenFromCache(host);
        if (!StringUtils.isEmpty(token)) {
            return token;
        } else {
            return fetchTokenFromLogin(host, clientId, clientSecret);
        }
    }
    
    public String fetchToken(String host) {
        TemplateConfigProperties.FeignProperties feign = templateConfigProperties.getFeign();
        String clientId = feign.getOauthClientId();
        String clientSecret = feign.getOauthClientSecret();
        return fetchToken(host, clientId, clientSecret);
    }
    
    private String fetchTokenFromCache(String host) {
        String token = tokenCacheMap.get(host);
        if (StringUtils.isBlank(token)) {
            return null;
        }
        LocalDateTime localDateTime = expiresCacheMap.get(host);
        if (LocalDateTime.now().isAfter(localDateTime)) {
            tokenCacheMap.remove(host);
            expiresCacheMap.remove(host);
            return null;
        }
        return token;
    }
    
    private String fetchTokenFromLogin(String host, String clientId, String clientSecret) {
        // 1. headers
        HttpHeaders headers = new HttpHeaders();
        String encodedAuth = makeBasicAuthInfo(clientId, clientSecret);
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 2. body
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        // 3. token url
        String url = templateConfigProperties.getOauth2().getTokenUrl();
        // 4. 调用 OAuth 服务
        ParameterizedTypeReference<Map<String, Object>> type = new ParameterizedTypeReference<>() {};
        Map<String, Object> response = sendRequest(headers, url, HttpMethod.POST, body, type);
        if (response == null) {
            throw new IllegalStateException("获取 OAuth2 Token 失败，响应为空");
        }
        // 5. 解析 access_token
        Object tokenObj = response.get("access_token");
        if (tokenObj == null) {
            log.error("OAuth2 token response missing 'access_token': {}", response);
            throw new IllegalStateException("获取 OAuth2 Token 失败，未返回 access_token");
        }
        String token = tokenObj.toString();
        // 6. 解析 expires_in
        long expiresInSeconds = 3600L; // default 1 hour
        Object expiresObj = response.get("expires_in");
        if (expiresObj != null) {
            try {
                expiresInSeconds = Long.parseLong(expiresObj.toString());
            } catch (NumberFormatException e) {
                log.warn("解析 expires_in 失败，将采用默认 3600 秒: {}", expiresObj);
            }
        }
        // 7. 计算过期时间，预留 1 分钟
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(expiresInSeconds - 60);
        // 8. 写入缓存
        tokenCacheMap.put(host, token);
        expiresCacheMap.put(host, expireTime);
        log.info("成功获取 OAuth2 token, host={}, expireAt={}", host, expireTime);
        return token;
    }
    
    private static String makeBasicAuthInfo(String clientId, String clientSecret) {
        String auth = clientId + ":" + clientSecret;
        return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }
    
    @SuppressWarnings("null")
    public <T, R> R sendRequest(HttpHeaders headers,
                                String url,
                                HttpMethod method,
                                T rb,
                                ParameterizedTypeReference<R> rt) {
        HttpEntity<T> entity;
        if (rb == null) {
            entity = new HttpEntity<>(headers);
        } else {
            entity = new HttpEntity<>(rb, headers);
        }
        ResponseEntity<R> response = restTemplate.exchange(url, method, entity, rt);
        if (response.getStatusCode().is2xxSuccessful()) {
            R body = response.getBody();
            if (body == null) {
                log.warn("请求成功但响应体为空: url={}", url);
            }
            return body;
        } else {
            log.error("请求失败: url={}, status={}", url, response.getStatusCode());
            return null;
        }
    }
}
