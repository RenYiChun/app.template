# 认证授权

## 配置项

- app.config.security.permitUrls
  > 排除哪些接口不进行认证检查， 默认为控制
  > 模板内部已经内置过滤了如下接口："/oauth2/**,/login,/error,/opaque/token/check,/jwt/key/public"
- app.config.security.authorizationType
  > 授权服务器的资源存储类型，authorization信息，client信息等，目前支持memory, redis
- app.config.security.opaque
  > 是否开启不透明token功能，默认值为false，如果设置为true，需要做如下事项：
  > 1. 配置spring.security.oauth2.resource-server.opaque-token.introspection-uri：http:
       //${oauth2-server}/opaque/token/check

- app.config.security.localJwtPublicKey
  > 是否启用本地JWT公钥，默认为true
  >1. 默认为false，必须要配置 app.config.security.netJwtPublicKeyUrl的值
  >2. 如果为true, 需要将一个名为rsa_public.pem的文件放在项目的resources下

- app.config.security.netJwtPublicKeyUrl
  > 从这个地址获取Jwt的公钥信息，可以配置为：http://${oauth2-server}/jwt/key/public

- app.config.security.customizeLoginPage
  > 自定义登录页面的地址，如果配置这个地址，需要写一个对应地址的控制器，返回登录页面

- app.config.security.autoRedirectLoginPage
  > 是否自动跳转到登录页面，默认为true

## 运行起来必备条件

1. 提供一个实现接口IOauth2UserService的bean，实现里面的两个接口，一个是根据工号查询得到UserDetails，一个是根据登录的用户名查询得到UserDetails。

## 不透明token

需要在授权服务器下配置如下参数：

```yaml
spring:
  security:
    oauth2:
      resource-server:
        opaque-token:
          introspection-uri: http://localhost:8080/opaque/token/check
          client-id: default-client-id
          client-secret: app.template
```

每一个微服务都需要配置app.config.security.opaque为true

## 其它说明

1. 模板中默认使用的是RedisOAuth2AuthorizationService, 因此**模板默认需要配置redis的连接相关信息**
   才能运行起来；
   如果需要使用内存或者数据库来作为OAuth2AuthorizationService，只需要自己定义一个如下Bean即可：

```java
@Bean public OAuth2AuthorizationService authorizationService(){
        //在这里定义自己需要的OAuth2AuthorizationService
        }
```

2. 模板中RegisteredClientRepository默认使用的是内存方式，内置了一个默认的client:

```java
// @formatter:off
RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("default-client-id")
    .clientSecret("@#*H17defaultbbc3996c64ae76003d6c034cb3fa7a3e51dc624ee097681e39ec8f42@#*H")
    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
    .authorizationGrantType(new AuthorizationGrantType(OAuth2Constant.GRANT_TYPE_PASSWORD))
    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
    .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
    .authorizationGrantType(AuthorizationGrantType.JWT_BEARER)
    .redirectUri("http://127.0.0.1:8081/manager/oauth2/code/oidc-client")
    .postLogoutRedirectUri("http://127.0.0.1:8081/")
    .scope(OidcScopes.OPENID)
    .scope(OidcScopes.PROFILE)
    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
    .build();
// @formatter:on
        return new InMemoryRegisteredClientRepository(oidcClient);
```

3. shell的调用方法为：

```shell
curl -X POST http://localhost:8080/oauth2/token \
-H "Content-Type: application/x-www-form-urlencoded" \
-d "grant_type=authorization_password&client_id=default-client-id&client_secret=app.template&username=admin&password=123456"
```