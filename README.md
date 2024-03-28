# 目标及其定位

基于spring-boot,spring-cloud,spring-cloud-alibaba的微服务应用，独立单应用的快速开发脚手架，内置提供如下能力：

- [x] Nats广播事件的支持；
- [x] 自定义配置文件内容加解密策略，支持spring的配置文件配置的值，环境变量中的值，nacos配置中的值的加密配置；
- [x] 加密策略的变更不会影响之前已经配置的内容；
- [x] Oauth2 自定义用户名密码认证策略的支持；
- [x] Oauth2 不透明token功能的支持；
- [x] 默认集成Spring security，已完成默认配置的定义，开箱即用；
- [x] 统一Spring Mvc接口返回数据和OpenFeign中数据序列化方式为fastjson;

# 生产注意事项

1. 替换默认的RSA公钥，私钥，及其基于这个公钥的JWT
   > 此三个文件都存放在项目的resource目录下

   > 文件名分别是： rsa_public.pem, rsa_private.pem, rsa_public.jwt;
   此三个文件的生产可以参考工具类：RSAUtils

# 快速开始

需准备如下环境

1. JDK 21的运行环境
2. maven 3.9及其以上版本

## 微服务应用

应用的pom.xml中的parent继承如下pom:

```xml

<parent>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-dependencies</artifactId>
    <version>${template-dependencies.version}</version>
</parent>
```

在pom中引入如下依赖即可：

```xml

<dependency>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-service</artifactId>
    <version>${template-dependencies.version}</version>
</dependency>
```

## 独立单应用

应用的pom.xml中的parent继承如下pom:

```xml

<parent>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-dependencies</artifactId>
    <version>${template-dependencies.version}</version>
</parent>
```

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