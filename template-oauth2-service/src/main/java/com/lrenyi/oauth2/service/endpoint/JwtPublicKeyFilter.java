package com.lrenyi.oauth2.service.endpoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import com.lrenyi.template.api.config.RsaPublicAndPrivateKey;
import com.lrenyi.template.core.json.JsonService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 对 GET /jwt/public/key 返回 JWK Set（仅公钥），供资源方校验 JWT 签名。
 * <p>
 * 安全说明：公钥本身可公开，无需鉴权；若未配置本地密钥（如仅用远程 IdP），返回 503 并继续链，避免 NPE 与异常堆栈泄露。
 * </p>
 */
@Slf4j
@Component
public class JwtPublicKeyFilter extends OncePerRequestFilter {

    private static final String ENDPOINT_URI = "/jwt/public/key";
    private static final String CACHE_CONTROL = "public, max-age=300";

    private final RequestMatcher endpointMatcher;
    private RsaPublicAndPrivateKey rsaPublicAndPrivateKey;
    private JsonService jsonService;

    @Autowired
    public void setJsonService(JsonService jsonService) {
        this.jsonService = jsonService;
    }
    
    @Autowired(required = false)
    public void setRsaPublicAndPrivateKey(RsaPublicAndPrivateKey rsaPublicAndPrivateKey) {
        this.rsaPublicAndPrivateKey = rsaPublicAndPrivateKey;
    }
    
    public JwtPublicKeyFilter() {
        this.endpointMatcher = new AntPathRequestMatcher(ENDPOINT_URI, HttpMethod.GET.name());
    }
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!endpointMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (rsaPublicAndPrivateKey == null) {
            sendServiceUnavailable(response, "JWT public key not available (no local key configured).");
            return;
        }
        try {
            RSAPublicKey rsaPublicKey = rsaPublicAndPrivateKey.templateRSAPublicKey();
            String kid = rsaPublicAndPrivateKey.getKid();
            RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey).keyID(kid).build();
            Map<String, Object> jsonObject = new JWKSet(rsaKey).toJSONObject(true);
            String value = jsonService.serialize(jsonObject);
            
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", CACHE_CONTROL);
            response.getOutputStream().write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to build JWK Set for {}", ENDPOINT_URI, e);
            sendServiceUnavailable(response, "JWT public key temporarily unavailable.");
        }
    }
    
    private void sendServiceUnavailable(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"error\":\"service_unavailable\",\"message\":\"" + escapeJson(message) + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
    
    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
