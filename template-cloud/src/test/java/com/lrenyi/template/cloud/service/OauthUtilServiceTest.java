package com.lrenyi.template.cloud.service;

import com.github.benmanes.caffeine.cache.Cache;
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
    private Cache<String, String> tokenCache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new OauthUtilService();
        tokenCache = (Cache<String, String>) ReflectionTestUtils.getField(OauthUtilService.class, "tokenCache");
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
    void tearDown() {
        if (tokenCache != null) {
            tokenCache.invalidateAll();
        }
    }

    @Test
    void fetchToken_withValidCache_returnsCachedToken() {
        String host = "cache-test-host";
        String token = "cached-access-token";
        tokenCache.put(host, token);

        String result = service.fetchToken(host, "cid", "secret");

        assertEquals(token, result);
    }

    @Test
    void fetchToken_withExpiredCache_attemptsLoginAndFails() {
        String host = "expired-host";
        tokenCache.invalidateAll();

        assertThrows(Exception.class, () ->
                service.fetchToken(host, "cid", "secret"));
    }

    @Test
    void fetchToken_withHost_returnsCachedToken() {
        String host = "host-only";
        String token = "token";
        tokenCache.put(host, token);

        String result = service.fetchToken(host);

        assertEquals(token, result);
    }
}
