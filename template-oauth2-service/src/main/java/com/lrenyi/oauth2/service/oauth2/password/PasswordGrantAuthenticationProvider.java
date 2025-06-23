package com.lrenyi.oauth2.service.oauth2.password;

import com.lrenyi.template.core.util.OAuth2Constant;
import jakarta.annotation.Resource;
import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class PasswordGrantAuthenticationProvider implements AuthenticationProvider {
    
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    @Resource
    private PasswordEncoder passwordEncoder;
    
    public PasswordGrantAuthenticationProvider(OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }
    
    // @formatter:off
    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        PasswordGrantAuthenticationToken grantToken =
                (PasswordGrantAuthenticationToken) authentication;
        
        Map<String, Object> parameters = grantToken.getAdditionalParameters();
        AuthorizationGrantType grantType = grantToken.getGrantType();
        String username = (String) parameters.get(OAuth2ParameterNames.USERNAME);
        String type = (String) parameters.get(OAuth2Constant.LOGIN_USER_NAME_TYPE_KEY);
        
        //请求参数权限范围
        String requestScopesStr = (String) parameters.get(OAuth2ParameterNames.SCOPE);
        Set<String> requestScopeSet = new HashSet<>();
        
        if (StringUtils.hasLength(requestScopesStr)) {
            requestScopeSet = Stream.of(requestScopesStr.split(" "))
                                    .collect(Collectors.toSet());
        }
        
        // Ensure the client is authenticated
        OAuth2ClientAuthenticationToken principal = getClient(grantToken);
        RegisteredClient client = principal.getRegisteredClient();
        
        // Ensure the client is configured to use this check grant type
        if (client == null || !client.getAuthorizationGrantTypes().contains(grantType)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        UserDetails userDetails;
        LoginNameUserDetailService userDetailService = LoginNameUserDetailService.ALL_LOGIN_NAME_TYPE.get(type);
        if (userDetailService == null && !OAuth2TokenIntrospectionClaimNames.USERNAME.equals(type)) {
            userDetailService =
                    LoginNameUserDetailService.ALL_LOGIN_NAME_TYPE.get(LoginNameType.USER_NAME.getCode());
        }
        if (userDetailService == null) {
            throw new OAuth2AuthenticationException("没有找到" + type + "类型的登录name字段类型的UserDetailService");
        }
        userDetails = userDetailService.loadUserDetail(username);
        String password = (String) parameters.get(OAuth2ParameterNames.PASSWORD);
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            OAuth2Error error = new OAuth2Error(OAuth2Constant.LOGIN_FAIL_OF_PASSWORD, "password is incorrect", "");
            throw new OAuth2AuthenticationException(error);
        }
        Collection<? extends GrantedAuthority> grantedAuthorities = userDetails.getAuthorities();
        Set<String> collect = grantedAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        OAuth2TokenContext tokenContext = DefaultOAuth2TokenContext.builder()
            .registeredClient(client)
            .principal(principal)
            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(grantType)
            .authorizedScopes(collect)
            .authorizationGrant(grantToken)
            .build();
        // @formatter:on
        
        OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);
        OAuth2AccessToken accessToken = getAuth2AccessToken(generatedAccessToken, collect);
        
        // @formatter:off
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
             .registeredClient(client)
             .principal(grantToken)
             .authorizationServerContext(AuthorizationServerContextHolder.getContext())
             .authorizationGrantType(grantType)
             .authorizedScopes(requestScopeSet)
             .authorizationGrant(grantToken);
        // @formatter:on
        
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client);
        builder.principalName(principal.getName());
        OAuth2Authorization.Builder authorizationBuilder =
                builder.authorizationGrantType(grantType);
        
        if (generatedAccessToken instanceof ClaimAccessor) {
            authorizationBuilder.token(accessToken, (metadata) -> {
                Map<String, Object> claims = ((ClaimAccessor) generatedAccessToken).getClaims();
                String metadataName = OAuth2Authorization.Token.CLAIMS_METADATA_NAME;
                metadata.put(metadataName, claims);
            });
        } else {
            authorizationBuilder.accessToken(accessToken);
        }
        parameters.forEach((key, value) -> {
            if (OAuth2ParameterNames.PASSWORD.equals(key)
                    || OAuth2ParameterNames.CLIENT_SECRET.equals(key)) {
                return;
            }
            authorizationBuilder.attribute(key, value);
        });
        authorizationBuilder.attribute(Principal.class.getName(), principal);
        authorizationBuilder.authorizedScopes(accessToken.getScopes());
        OAuth2Authorization authorization = authorizationBuilder.build();
        
        OAuth2RefreshToken refreshToken = null;
        boolean refresh =
                client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN);
        boolean eqNone =
                !principal.getClientAuthenticationMethod().equals(ClientAuthenticationMethod.NONE);
        
        if (refresh && eqNone) {
            tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContext);
            if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
                String description = "The token generator failed to generate the refresh token.";
                String serverError = OAuth2ErrorCodes.SERVER_ERROR;
                OAuth2Error error = new OAuth2Error(serverError, description, "");
                throw new OAuth2AuthenticationException(error);
            }
            refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
            authorizationBuilder.refreshToken(refreshToken);
        }
        
        // Save the OAuth2Authorization
        this.authorizationService.save(authorization);
        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("id", authorization.getId());
        parameter.put(OAuth2TokenIntrospectionClaimNames.USERNAME, username);
        return new OAuth2AccessTokenAuthenticationToken(client,
                                                        principal,
                                                        accessToken,
                                                        refreshToken,
                                                        parameter
        );
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }
    
    private static OAuth2ClientAuthenticationToken getClient(Authentication authentication) {
        OAuth2ClientAuthenticationToken clientPrincipal = null;
        if (OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication.getPrincipal()
                                                                                 .getClass())) {
            clientPrincipal = (OAuth2ClientAuthenticationToken) authentication.getPrincipal();
        }
        if (clientPrincipal != null && clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }
    
    private static OAuth2AccessToken getAuth2AccessToken(OAuth2Token generatedAccessToken,
            Set<String> collect) {
        if (generatedAccessToken == null) {
            String description = "the token generator failed to generate the access token.";
            String serverError = OAuth2ErrorCodes.SERVER_ERROR;
            OAuth2Error error = new OAuth2Error(serverError, description, null);
            throw new OAuth2AuthenticationException(error);
        }
        OAuth2AccessToken.TokenType bearer = OAuth2AccessToken.TokenType.BEARER;
        String value = generatedAccessToken.getTokenValue();
        Instant issuedAt = generatedAccessToken.getIssuedAt();
        Instant expiresAt = generatedAccessToken.getExpiresAt();
        
        return new OAuth2AccessToken(bearer, value, issuedAt, expiresAt, collect);
    }
}