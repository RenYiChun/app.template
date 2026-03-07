package com.lrenyi.oauth2.service.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * 将 Spring Boot 的 OAuth2 授权服务器配置（{@link OAuth2AuthorizationServerProperties}）
 * 转换为运行时使用的 {@link RegisteredClient} 列表。
 * <p>
 * 对应配置文件中的 {@code spring.security.oauth2.authorizationserver.client.*}，
 * 供 {@link org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository} 使用，
 * 授权服务器据此校验客户端、重定向地址、授权类型及 Token 设置等。
 * </p>
 */
public class OAuth2ClientPropertiesMapper {
    
    private final OAuth2AuthorizationServerProperties properties;
    
    public OAuth2ClientPropertiesMapper(OAuth2AuthorizationServerProperties properties) {
        this.properties = properties;
    }
    
    /**
     * 从配置生成 RegisteredClient 数组，供各 Repository 实现复用，避免重复「mapper + asRegisteredClients + toArray」。
     */
    public static RegisteredClient[] fromProperties(OAuth2AuthorizationServerProperties properties) {
        return new OAuth2ClientPropertiesMapper(properties).asRegisteredClients().toArray(new RegisteredClient[0]);
    }
    
    /**
     * 将配置中所有客户端转换为 RegisteredClient 列表，用于注入内存或自定义 Repository。
     *
     * @return 每个配置项（registrationId + client）对应一个 RegisteredClient
     */
    public List<RegisteredClient> asRegisteredClients() {
        List<RegisteredClient> registeredClients = new ArrayList<>();
        BiConsumer<String, OAuth2AuthorizationServerProperties.Client> consumer =
                (registrationId, client) -> registeredClients.add(getRegisteredClient(registrationId, client));
        this.properties.getClient().forEach(consumer);
        return registeredClients;
    }
    
    /**
     * 将单个客户端配置（Client）映射为 RegisteredClient，包含注册信息、ClientSettings、TokenSettings。
     */
    private RegisteredClient getRegisteredClient(String registrationId,
            OAuth2AuthorizationServerProperties.Client client) {
        OAuth2AuthorizationServerProperties.Registration registration = client.getRegistration();
        PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
        RegisteredClient.Builder builder = RegisteredClient.withId(registrationId);
        map.from(registration::getClientId).to(builder::clientId);
        map.from(registration::getClientSecret).to(builder::clientSecret);
        map.from(registration::getClientName).to(builder::clientName);
        Consumer<String> consumer = clientAuthenticationMethod -> map.from(clientAuthenticationMethod)
                                                                     .as(ClientAuthenticationMethod::new)
                                                                     .to(builder::clientAuthenticationMethod);
        registration.getClientAuthenticationMethods().forEach(consumer);
        registration.getAuthorizationGrantTypes()
                    .forEach(authorizationGrantType -> map.from(authorizationGrantType)
                                                          .as(AuthorizationGrantType::new)
                                                          .to(builder::authorizationGrantType));
        registration.getRedirectUris().forEach(redirectUri -> map.from(redirectUri).to(builder::redirectUri));
        registration.getPostLogoutRedirectUris()
                    .forEach(redirectUri -> map.from(redirectUri).to(builder::postLogoutRedirectUri));
        registration.getScopes().forEach(scope -> map.from(scope).to(builder::scope));
        builder.clientSettings(getClientSettings(client, map));
        builder.tokenSettings(getTokenSettings(client, map));
        return builder.build();
    }
    
    /**
     * 映射客户端行为与端点设置：PKCE、授权确认、JWK Set URI、Token 端点签名算法等。
     */
    private ClientSettings getClientSettings(OAuth2AuthorizationServerProperties.Client client, PropertyMapper map) {
        ClientSettings.Builder builder = ClientSettings.builder();
        return applyAndBuild(() -> applyClientSettingsMappings(builder, map, client), builder::build);
    }
    
    /**
     * 映射 Token 相关设置：授权码/访问令牌/刷新令牌/设备码有效期、访问令牌格式、ID Token 签名算法等。
     */
    private TokenSettings getTokenSettings(OAuth2AuthorizationServerProperties.Client client, PropertyMapper map) {
        OAuth2AuthorizationServerProperties.Token token = client.getToken();
        TokenSettings.Builder builder = TokenSettings.builder();
        return applyAndBuild(() -> applyTokenSettingsMappings(builder, map, token), builder::build);
    }
    
    /** 先执行映射再 build，避免 getClientSettings/getTokenSettings 中重复「多行 map.from...to + return builder.build」结构。 */
    private static <T> T applyAndBuild(Runnable mappings, Supplier<T> build) {
        mappings.run();
        return build.get();
    }
    
    private void applyClientSettingsMappings(ClientSettings.Builder builder,
            PropertyMapper map,
            OAuth2AuthorizationServerProperties.Client client) {
        map.from(client::isRequireProofKey).to(builder::requireProofKey);
        map.from(client::isRequireAuthorizationConsent).to(builder::requireAuthorizationConsent);
        map.from(client::getJwkSetUri).to(builder::jwkSetUrl);
        map.from(client::getTokenEndpointAuthenticationSigningAlgorithm)
           .as(this::jwsAlgorithm)
           .to(builder::tokenEndpointAuthenticationSigningAlgorithm);
    }
    
    private void applyTokenSettingsMappings(TokenSettings.Builder builder,
            PropertyMapper map,
            OAuth2AuthorizationServerProperties.Token token) {
        map.from(token::getAuthorizationCodeTimeToLive).to(builder::authorizationCodeTimeToLive);
        map.from(token::getAccessTokenTimeToLive).to(builder::accessTokenTimeToLive);
        map.from(token::getAccessTokenFormat).as(OAuth2TokenFormat::new).to(builder::accessTokenFormat);
        map.from(token::getDeviceCodeTimeToLive).to(builder::deviceCodeTimeToLive);
        map.from(token::isReuseRefreshTokens).to(builder::reuseRefreshTokens);
        map.from(token::getRefreshTokenTimeToLive).to(builder::refreshTokenTimeToLive);
        map.from(token::getIdTokenSignatureAlgorithm)
           .as(this::signatureAlgorithm)
           .to(builder::idTokenSignatureAlgorithm);
    }
    
    /**
     * 将配置中的签名算法字符串转为 JwsAlgorithm（先尝试 SignatureAlgorithm，再尝试 MacAlgorithm）。
     */
    private JwsAlgorithm jwsAlgorithm(String signingAlgorithm) {
        String name = signingAlgorithm.toUpperCase();
        JwsAlgorithm jwsAlgorithm = SignatureAlgorithm.from(name);
        if (jwsAlgorithm == null) {
            jwsAlgorithm = MacAlgorithm.from(name);
        }
        return jwsAlgorithm;
    }
    
    /**
     * 将配置中的 ID Token 签名算法字符串转为 SignatureAlgorithm。
     */
    private SignatureAlgorithm signatureAlgorithm(String signatureAlgorithm) {
        return SignatureAlgorithm.from(signatureAlgorithm.toUpperCase());
    }
}
