# 认证授权

## 运行起来必备条件

1. 提供一个实现接口LoginNameUserDetailService的bean，登录认证时用于获取用户信息。
2. 提供一个默认的RegisteredClient，比如：
   ```yaml
   spring:
      security:
         oauth2:
            authorization-server:
               client:
                  default-client-id:
                     registration:
                        client-id: default-client-id
                        client-secret: "{default}bbc3996c64ae76003d6c034cb3fa7a3e51dc624ee097681e39ec8f42"
                        client-authentication-methods:
                           - client_secret_post
                           - client_secret_get
                        authorization-grant-types:
                           - authorization_code
                           - authorization_password
                           - client_credentials
                        scopes:
                           - openid
                        redirect-uris:
                           - http://localhost/
                        post-logout-redirect-uris:
                           - http://localhost/logout
                     token:
                        access-token-time-to-live: 60m
                        access-token-format: reference
   ```
## 不透明token

需要在授权服务器下配置如下参数, 且上述配置中的access-token-format的值必须为：reference

```yaml
app:
  config:
    security:
      enable: true
      opaque-token:
        enable: true
        introspection-uri: http://127.0.0.1:8081/opaque/token/check
```

每一个微服务都需要配置app.config.security.opaque-token.enable为true, introspection-uri这个为
透明token的校验地址，其中/opaque/token/check为固定值，前面host根据实际情况替换
