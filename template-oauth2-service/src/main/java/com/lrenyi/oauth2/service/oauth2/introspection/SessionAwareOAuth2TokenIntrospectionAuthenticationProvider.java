package com.lrenyi.oauth2.service.oauth2.introspection;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.converter.ClaimConversionService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization.Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * 标准 token introspection 默认保持只读；显式传入 touch_session=true 时，
 * 才按 access token 查询并触发会话续期。
 */
public final class SessionAwareOAuth2TokenIntrospectionAuthenticationProvider implements AuthenticationProvider {
    
    public static final String TOUCH_SESSION_PARAMETER_NAME = "touch_session";
    
    private static final TypeDescriptor OBJECT_TYPE_DESCRIPTOR = TypeDescriptor.valueOf(Object.class);
    private static final TypeDescriptor LIST_STRING_TYPE_DESCRIPTOR =
            TypeDescriptor.collection(List.class, TypeDescriptor.valueOf(String.class));
    
    private final Log logger = LogFactory.getLog(getClass());
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationService authorizationService;
    
    public SessionAwareOAuth2TokenIntrospectionAuthenticationProvider(
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService) {
        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationService = authorizationService;
    }
    
    @Override
    public Authentication authenticate(Authentication authentication) throws OAuth2AuthenticationException {
        OAuth2TokenIntrospectionAuthenticationToken introspectionAuthentication =
                (OAuth2TokenIntrospectionAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal =
                getAuthenticatedClientElseThrowInvalidClient(authentication);
        OAuth2Authorization authorization = authorizationService.findByToken(introspectionAuthentication.getToken(),
                                                                             resolveLookupTokenType(
                                                                                     introspectionAuthentication));
        if (authorization == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Did not authenticate token introspection request since token was not found");
            }
            return introspectionAuthentication;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Retrieved authorization with token");
        }
        Token<OAuth2Token> authorizedToken = authorization.getToken(introspectionAuthentication.getToken());
        if (authorizedToken == null || !authorizedToken.isActive()) {
            if (logger.isTraceEnabled()) {
                logger.trace("Did not introspect token since not active");
            }
            return new OAuth2TokenIntrospectionAuthenticationToken(introspectionAuthentication.getToken(),
                                                                   clientPrincipal,
                                                                   OAuth2TokenIntrospection.builder().build());
        }
        RegisteredClient authorizedClient = registeredClientRepository.findById(authorization.getRegisteredClientId());
        OAuth2TokenIntrospection tokenClaims = withActiveTokenClaims(authorizedToken, authorizedClient);
        if (logger.isTraceEnabled()) {
            logger.trace("Authenticated token introspection request");
        }
        return new OAuth2TokenIntrospectionAuthenticationToken(authorizedToken.getToken().getTokenValue(),
                                                               clientPrincipal,
                                                               tokenClaims);
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenIntrospectionAuthenticationToken.class.isAssignableFrom(authentication);
    }
    
    private static OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(
            Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken clientAuthentication
                && clientAuthentication.isAuthenticated()) {
            return clientAuthentication;
        }
        throw new OAuth2AuthenticationException("invalid_client");
    }
    
    private static OAuth2TokenIntrospection withActiveTokenClaims(Token<OAuth2Token> authorizedToken,
            RegisteredClient authorizedClient) {
        OAuth2TokenIntrospection.Builder builder;
        if (!CollectionUtils.isEmpty(authorizedToken.getClaims())) {
            builder = OAuth2TokenIntrospection.withClaims(convertClaimsIfNecessary(authorizedToken.getClaims()))
                                              .active(true);
        } else {
            builder = OAuth2TokenIntrospection.builder(true);
        }
        builder.clientId(authorizedClient.getClientId());
        OAuth2Token token = authorizedToken.getToken();
        if (token.getIssuedAt() != null) {
            builder.issuedAt(token.getIssuedAt());
        }
        if (token.getExpiresAt() != null) {
            builder.expiresAt(token.getExpiresAt());
        }
        if (token instanceof OAuth2AccessToken accessToken) {
            builder.tokenType(accessToken.getTokenType().getValue());
        }
        return builder.build();
    }
    
    private static Map<String, Object> convertClaimsIfNecessary(Map<String, Object> claims) {
        Map<String, Object> convertedClaims = new HashMap<>(claims);
        Object issuer = claims.get("iss");
        if (issuer != null && !(issuer instanceof URL)) {
            URL convertedIssuer = ClaimConversionService.getSharedInstance().convert(issuer, URL.class);
            if (convertedIssuer != null) {
                convertedClaims.put("iss", convertedIssuer);
            }
        }
        convertClaimListIfNecessary(claims, convertedClaims, "scope");
        convertClaimListIfNecessary(claims, convertedClaims, "aud");
        return convertedClaims;
    }
    
    private static void convertClaimListIfNecessary(Map<String, Object> claims,
            Map<String, Object> convertedClaims,
            String claimName) {
        Object claimValue = claims.get(claimName);
        if (claimValue != null && !(claimValue instanceof List)) {
            Object convertedValue = ClaimConversionService.getSharedInstance()
                                                          .convert(claimValue,
                                                                   OBJECT_TYPE_DESCRIPTOR,
                                                                   LIST_STRING_TYPE_DESCRIPTOR);
            if (convertedValue != null) {
                convertedClaims.put(claimName, convertedValue);
            }
        }
    }
    
    private static OAuth2TokenType resolveLookupTokenType(
            OAuth2TokenIntrospectionAuthenticationToken introspectionAuthentication) {
        return shouldTouchSession(introspectionAuthentication) ? OAuth2TokenType.ACCESS_TOKEN : null;
    }
    
    private static boolean shouldTouchSession(
            OAuth2TokenIntrospectionAuthenticationToken introspectionAuthentication) {
        Object value = introspectionAuthentication.getAdditionalParameters().get(TOUCH_SESSION_PARAMETER_NAME);
        if (value instanceof String[] values) {
            return values.length > 0 && Boolean.parseBoolean(values[0]);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                return Boolean.parseBoolean(String.valueOf(item));
            }
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
