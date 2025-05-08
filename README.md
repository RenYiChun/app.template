# 目标及其定位

基于spring-boot,spring-cloud,spring-cloud-alibaba的微服务应用，独立单应用的快速开发脚手架，内置提供如下能力：

- [x] Nats事件的定义及其消息发送和接受的封装，一个消息只需要实现接口EventProcessor即可；
- [x] 扩展Spring的PasswordEncoder接口TemplateDataCoder，增加直接解密的方法支持；
- [x] TemplateDataCoder接口默认提供两种实现，一种default加密方式，一种RSA2048加解密方式；
- [x] spring的配置属性加密方式的扩展，支持TemplateDataCoder定义的加解密方式，密码字段用"aENC("开头，")"结尾；
- [x] 配置的加密方式支持环境变量配置，nacos配置，启动命令参数配置，bootstrap.yml/application.yml等配置
- [x] Oauth2 支持用户名密码认证的方式；
- [x] Oauth2 不透明token功能的支持；
- [x] jwt的token支持本地和远程校验两种方式；
- [x] 统一Spring Mvc接口返回数据和OpenFeign中数据序列化方式为fastjson;

# 生产注意事项

1. 替换默认的RSA公钥，私钥，覆盖如下Bean的定义
   ```java
    @Bean
    @ConditionalOnMissingBean
    public RsaPublicAndPrivateKey rsaPublicAndPrivateKey() {
        return new TemplateRsaPublicAndPrivateKey();
    }
   ```

# 快速开始

需准备如下环境

1. JDK 21的运行环境
2. maven 3.6及其以上版本
3. 新应用的pom.xml中的parent继承如下pom
   ```xml
   <parent>
       <groupId>com.lrenyi</groupId>
       <artifactId>template-dependencies</artifactId>
       <version>${template-dependencies.version}</version>
   </parent>
   ```
## 微服务应用

在pom中引入如下依赖即可：

```xml

<dependency>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-service</artifactId>
    <version>${template-dependencies.version}</version>
</dependency>
```

## 独立单应用
在pom中引入如下依赖即可：

```xml

<dependency>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-web</artifactId>
    <version>${template-dependencies.version}</version>
</dependency>
```

## Oauth2认证服务

每一个服务默认开启了security功能，如果不需要，可以配置app.config.security.enable=false 来禁用此功能

如果服务需要作为oauth2的认证服务器，**_需要移除template-web的依赖_**，直接引入如下依赖即可:

```xml

<dependency>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-oauth2-service</artifactId>
    <version>${template-dependencies.version}</version>
</dependency>
```

详细内容可以查阅文档：[Oauth2 service](doc/spring_security.md)

# 配置指南

## 最小配置

### 1. 基础配置
在 `application.yml` 中添加以下最小配置：

```yaml
app:
  config:
    security:
      enable: true  # 启用安全认证
      jwt:
        enable: true  # 启用JWT
        local-verify: true  # 本地验证JWT
```

### 2. 用户认证配置
```yaml
app:
  config:
    security:
      user:
        username: admin
        password: aENC(加密后的密码)  # 使用TemplateDataCoder加密
```

## 详细配置

### 1. 安全认证配置
```yaml
app:
  config:
    security:
      enable: true
      jwt:
        enable: true
        local-verify: true
        token-validity-in-seconds: 3600
        token-validity-in-seconds-for-remember-me: 2592000
      oauth2:
        client:
          client-id: your-client-id
          client-secret: aENC(加密后的密钥)
          scope: read,write
          authorized-grant-types: password,refresh_token
          access-token-validity-seconds: 3600
          refresh-token-validity-seconds: 86400
```

### 2. 用户服务配置
```yaml
app:
  config:
    security:
      user:
        username: admin
        password: aENC(加密后的密码)
        authorities: ROLE_ADMIN,ROLE_USER
        account-non-expired: true
        account-non-locked: true
        credentials-non-expired: true
        enabled: true
```

### 3. 自定义用户服务
如果需要自定义用户服务，可以实现 `UserDetailsService` 接口：

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 实现自定义的用户加载逻辑
        return new User(username, password, authorities);
    }
}
```

### 4. 密码加密配置
默认提供两种密码加密方式：

1. 默认加密方式：
```yaml
app:
  config:
    security:
      password-encoder: default
```


## 注意事项

1. 所有密码字段建议使用 `aENC()` 进行加密
2. 生产环境必须替换默认的RSA密钥对
3. 建议使用环境变量或配置中心管理敏感信息
4. JWT token验证支持本地和远程两种方式，根据需求选择配置