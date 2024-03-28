package com.lrenyi.oauth2.service.endpoint;

import com.alibaba.fastjson2.JSON;
import com.lrenyi.template.web.authorization.RsaPublicAndPrivateKey;
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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtPublicKeyFilter extends OncePerRequestFilter {
    private static final String ENDPOINT_URI = "/jwt/public/key";
    private final RequestMatcher endpointMatcher;
    private RsaPublicAndPrivateKey rsaPublicAndPrivateKey;
    
    @Autowired
    public void setRsaPublicAndPrivateKey(RsaPublicAndPrivateKey rsaPublicAndPrivateKey) {
        this.rsaPublicAndPrivateKey = rsaPublicAndPrivateKey;
    }
    
    public JwtPublicKeyFilter() {
        this.endpointMatcher = createDefaultRequestMatcher();
    }
    
    private RequestMatcher createDefaultRequestMatcher() {
        String method = HttpMethod.GET.name();
        return new AntPathRequestMatcher(JwtPublicKeyFilter.ENDPOINT_URI, method);
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
        String value = JSON.toJSONString(jsonObject);
        
        response.setContentType("application/json");
        OutputStream outputStream = response.getOutputStream();
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.close();
    }
}
