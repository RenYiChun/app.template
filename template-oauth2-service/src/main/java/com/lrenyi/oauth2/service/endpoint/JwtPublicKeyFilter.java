package com.lrenyi.oauth2.service.endpoint;

import com.lrenyi.template.core.json.JsonService;
import com.lrenyi.template.api.config.RsaPublicAndPrivateKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtPublicKeyFilter extends OncePerRequestFilter {
    private static final String ENDPOINT_URI = "/jwt/public/key";
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
        this.endpointMatcher = createDefaultRequestMatcher();
    }
    
    private RequestMatcher createDefaultRequestMatcher() {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, JwtPublicKeyFilter.ENDPOINT_URI);
    }
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!endpointMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        RSAPublicKey rsaPublicKey = rsaPublicAndPrivateKey.templateRSAPublicKey();
        String kid = rsaPublicAndPrivateKey.getKid();
        RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey).keyID(kid).build();
        Map<String, Object> jsonObject = new JWKSet(rsaKey).toJSONObject(true);
        String value = jsonService.serialize(jsonObject);
        
        response.setContentType("application/json");
        OutputStream outputStream = response.getOutputStream();
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.close();
    }
}
