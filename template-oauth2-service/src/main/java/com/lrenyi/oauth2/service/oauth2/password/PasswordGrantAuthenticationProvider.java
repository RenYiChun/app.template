package com.lrenyi.oauth2.service.oauth2.password;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.lrenyi.oauth2.service.config.IdentifierType;
import com.lrenyi.template.core.util.OAuth2Constant;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    
    public PasswordGrantAuthenticationProvider(OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            PasswordEncoder passwordEncoder,
            UserDetailsService userDetailsService) {
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PasswordGrantAuthenticationToken grantToken = (PasswordGrantAuthenticationToken) authentication;
        Map<String, Object> parameters = grantToken.getAdditionalParameters();
        AuthorizationGrantType grantType = grantToken.getGrantType();
        String username = (String) parameters.get(OAuth2ParameterNames.USERNAME);
        String type = parseIdentifierType(parameters);
        Set<String> requestScopeSet = parseRequestScopes(parameters);
        
        OAuth2ClientAuthenticationToken principal = getClient(grantToken);
        RegisteredClient client = principal.getRegisteredClient();
        if (client == null || !client.getAuthorizationGrantTypes().contains(grantType)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        
        UserDetails userDetails = validateAndLoadUser(username, type, parameters);
        
        // 使用 UserDetails 创建 Authentication，而不是继续使用 Client Principal
        Authentication userPrincipal = new UsernamePasswordAuthenticationToken(userDetails,
                                                                               userDetails.getPassword(),
                                                                               userDetails.getAuthorities()
        );
        
        Set<String> authorizedScopes =
                userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        
        OAuth2Token generatedAccessToken = generateAccessToken(client, userPrincipal, grantToken, authorizedScopes);
        OAuth2AccessToken accessToken = getAuth2AccessToken(generatedAccessToken, authorizedScopes);
        DefaultOAuth2TokenContext.Builder refreshContextBuilder =
                buildRefreshTokenContext(client, userPrincipal, grantToken, requestScopeSet);
        OAuth2RefreshToken refreshToken = maybeGenerateRefreshToken(client, principal, refreshContextBuilder);
        OAuth2Authorization authorization = buildAuthorization(client,
                                                               userPrincipal,
                                                               grantToken,
                                                               accessToken,
                                                               generatedAccessToken,
                                                               refreshToken,
                                                               parameters
        );
        
        this.authorizationService.save(authorization);
        HashMap<String, Object> params = new HashMap<>();
        params.put(OAuth2TokenIntrospectionClaimNames.USERNAME, username);
        // 返回的 Authentication 应该包含用户 Principal
        return new OAuth2AccessTokenAuthenticationToken(client, userPrincipal, accessToken, refreshToken, params);
    }
    
    private static String parseIdentifierType(Map<String, Object> parameters) {
        String type = (String) parameters.get(OAuth2Constant.LOGIN_USER_NAME_TYPE_KEY);
        return StringUtils.hasLength(type) ? type : IdentifierType.USERNAME.name();
    }
    
    private static Set<String> parseRequestScopes(Map<String, Object> parameters) {
        String requestScopesStr = (String) parameters.get(OAuth2ParameterNames.SCOPE);
        if (!StringUtils.hasLength(requestScopesStr)) {
            return new HashSet<>();
        }
        return Stream.of(requestScopesStr.split(" ")).collect(Collectors.toSet());
    }
    
    private static OAuth2ClientAuthenticationToken getClient(Authentication authentication) {
        OAuth2ClientAuthenticationToken clientPrincipal = null;
        if (OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication.getPrincipal().getClass())) {
            clientPrincipal = (OAuth2ClientAuthenticationToken) authentication.getPrincipal();
        }
        if (clientPrincipal != null && clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }
    
    private UserDetails validateAndLoadUser(String username, String type, Map<String, Object> parameters) {
        String password = (String) parameters.get(OAuth2ParameterNames.PASSWORD);
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(type + ":" + username);
        } catch (UsernameNotFoundException e) {
            // 用户不存在时做一次假校验以统一响应时间，再返回与“密码错误”相同的错误，防止用户枚举
            passwordEncoder.matches(password, passwordEncoder.encode("__dummy_enumeration_prevention__"));
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2Constant.LOGIN_FAIL_OF_PASSWORD,
                                                                    "password is incorrect",
                                                                    ""
            ));
        }
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2Constant.LOGIN_FAIL_OF_PASSWORD,
                                                                    "password is incorrect",
                                                                    ""
            ));
        }
        return userDetails;
    }
    
    private OAuth2Token generateAccessToken(RegisteredClient client,
            Authentication principal,
            PasswordGrantAuthenticationToken grantToken,
            Set<String> scopes) {
        OAuth2TokenContext ctx = DefaultOAuth2TokenContext.builder()
                                                          .registeredClient(client)
                                                          .principal(principal)
                                                          .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                                                          .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                                                          .authorizationGrantType(grantToken.getGrantType())
                                                          .authorizedScopes(scopes)
                                                          .authorizationGrant(grantToken)
                                                          .build();
        OAuth2Token token = tokenGenerator.generate(ctx);
        if (token == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN);
        }
        return token;
    }
    
    private static OAuth2AccessToken getAuth2AccessToken(OAuth2Token generatedAccessToken, Set<String> collect) {
        OAuth2AccessToken.TokenType bearer = OAuth2AccessToken.TokenType.BEARER;
        String value = generatedAccessToken.getTokenValue();
        Instant issuedAt = generatedAccessToken.getIssuedAt();
        Instant expiresAt = generatedAccessToken.getExpiresAt();
        
        return new OAuth2AccessToken(bearer, value, issuedAt, expiresAt, collect);
    }
    
    private static DefaultOAuth2TokenContext.Builder buildRefreshTokenContext(RegisteredClient client,
            Authentication principal,
            PasswordGrantAuthenticationToken grantToken,
            Set<String> requestScopeSet) {
        return DefaultOAuth2TokenContext.builder()
                                        .registeredClient(client)
                                        .principal(principal)
                                        .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                                        .authorizationGrantType(grantToken.getGrantType())
                                        .authorizedScopes(requestScopeSet)
                                        .authorizationGrant(grantToken);
    }
    
    private @Nullable OAuth2RefreshToken maybeGenerateRefreshToken(RegisteredClient client,
            OAuth2ClientAuthenticationToken principal,
            DefaultOAuth2TokenContext.Builder contextBuilder) {
        boolean supportsRefresh = client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN);
        boolean clientAuthenticated =
                !principal.getClientAuthenticationMethod().equals(ClientAuthenticationMethod.NONE);
        if (!supportsRefresh || !clientAuthenticated) {
            return null;
        }
        OAuth2TokenContext ctx = contextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
        OAuth2Token token = tokenGenerator.generate(ctx);
        if (!(token instanceof OAuth2RefreshToken)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                                                                    "The token generator failed to generate the "
                                                                            + "refresh token.",
                                                                    ""
            ));
        }
        return (OAuth2RefreshToken) token;
    }
    
    private OAuth2Authorization buildAuthorization(RegisteredClient client,
            Authentication principal,
            PasswordGrantAuthenticationToken grantToken,
            OAuth2AccessToken accessToken,
            OAuth2Token generatedAccessToken,
            @Nullable OAuth2RefreshToken refreshToken,
            Map<String, Object> parameters) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                                                                 .principalName(principal.getName())
                                                                 .authorizationGrantType(grantToken.getGrantType());
        if (generatedAccessToken instanceof ClaimAccessor) {
            builder.token(accessToken,
                          (metadata) -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                                                     ((ClaimAccessor) generatedAccessToken).getClaims()
                          )
            );
        } else {
            builder.accessToken(accessToken);
        }
        if (refreshToken != null) {
            builder.refreshToken(refreshToken);
        }
        parameters.forEach((key, value) -> {
            if (!OAuth2ParameterNames.PASSWORD.equals(key) && !OAuth2ParameterNames.CLIENT_SECRET.equals(key)) {
                builder.attribute(key, value);
            }
        });
        builder.attribute(Principal.class.getName(), principal);
        builder.authorizedScopes(accessToken.getScopes());
        return builder.build();
    }
    
    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
