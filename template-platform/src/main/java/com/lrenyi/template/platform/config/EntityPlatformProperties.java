package com.lrenyi.template.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体平台配置：API 前缀、权限默认策略等。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.platform")
public class EntityPlatformProperties {
    
    /**
     * API 路径前缀，如 /api。所有实体 CRUD 与 Action 路径均在此前缀下。
     */
    private String apiPrefix = "/api";
    
    /**
     * 是否启用权限校验。为 false 时仅要求认证通过即可。
     */
    private boolean permissionEnabled = true;
    
    /**
     * 未配置权限标识时：true = 仅认证即可；false = 拒绝访问。
     */
    private boolean defaultAllowIfNoPermission = true;
    
    /**
     * 扫描 @PlatformEntity 的包名，多个用逗号分隔。为空则不扫描实体。
     */
    private String scanPackages = "";

    /**
     * RBAC 用户权限解析缓存 TTL（分钟）。&lt;=0 表示不缓存，每次请求查库；&gt;0 时使用本地缓存，过期后反映角色权限动态变更。
     */
    private int rbacCacheTtlMinutes = 5;

    /**
     * 是否在启动时根据已注册实体自动初始化 sys_permission 缺失的权限记录。需 JPA 且应用扫描 platform.domain 时生效。
     */
    private boolean rbacInitPermissions = true;

    /**
     * 是否启用内嵌 API 文档界面（Swagger UI）。为 true 时访问 docs-ui-path 可打开文档页。
     */
    private boolean docsUiEnabled = true;

    /**
     * API 文档界面入口路径，默认 /docs。仅当 docs-ui-enabled 为 true 时生效。
     */
    private String docsUiPath = "/docs";

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix == null ? "/api" : apiPrefix;
    }
    
    public void setScanPackages(String scanPackages) {
        this.scanPackages = scanPackages != null ? scanPackages : "";
    }

    public void setDocsUiPath(String docsUiPath) {
        this.docsUiPath = docsUiPath != null ? docsUiPath : "/docs";
    }
}
