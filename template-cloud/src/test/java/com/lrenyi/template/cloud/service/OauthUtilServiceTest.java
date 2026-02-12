package com.lrenyi.template.cloud.service;

import java.time.LocalDateTime;
import java.util.Map;
import com.lrenyi.template.core.TemplateConfigProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OauthUtilService 单元测试
 */
class OauthUtilServiceTest {

    private OauthUtilService service;

    @BeforeEach
    void setUp() {
        service = new OauthUtilService();
        TemplateConfigProperties config = new TemplateConfigProperties();
        TemplateConfigProperties.OAuth2Config oauth2 = new TemplateConfigProperties.OAuth2Config();
        oauth2.setTokenUrl("https://auth.example.com/oauth/token");
        config.setOauth2(oauth2);
        TemplateConfigProperties.FeignProperties feign = new TemplateConfigProperties.FeignProperties();
        feign.setOauthClientId("test-client");
        feign.setOauthClientSecret("test-secret");
        config.setFeign(feign);
        ReflectionTestUtils.setField(service, "templateConfigProperties", config);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() {
        OauthUtilService.tokenCacheMap.clear();
        Map<String, LocalDateTime> expiresMap =
                (Map<String, LocalDateTime>) ReflectionTestUtils.getField(OauthUtilService.class, "expiresCacheMap");
        if (expiresMap != null) {
            expiresMap.clear();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchToken_withValidCache_returnsCachedToken() {
        String host = "cache-test-host";
        String token = "cached-access-token";
        OauthUtilService.tokenCacheMap.put(host, token);
        Map<String, LocalDateTime> expiresMap =
                (Map<String, LocalDateTime>) ReflectionTestUtils.getField(OauthUtilService.class, "expiresCacheMap");
        if (expiresMap != null) {
            expiresMap.put(host, LocalDateTime.now().plusHours(1));
        }

        String result = service.fetchToken(host, "cid", "secret");

        assertEquals(token, result);
    }

    @Test
    void fetchToken_withExpiredCache_attemptsLoginAndFails() {
        String host = "expired-host";
        OauthUtilService.tokenCacheMap.clear();

        assertThrows(Exception.class, () ->
                service.fetchToken(host, "cid", "secret"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchToken_withHost_returnsCachedToken() {
        String host = "host-only";
        String token = "token";
        OauthUtilService.tokenCacheMap.put(host, token);
        Map<String, LocalDateTime> expiresMap =
                (Map<String, LocalDateTime>) ReflectionTestUtils.getField(OauthUtilService.class, "expiresCacheMap");
        if (expiresMap != null) {
            expiresMap.put(host, LocalDateTime.now().plusHours(1));
        }

        String result = service.fetchToken(host);

        assertEquals(token, result);
    }
}
