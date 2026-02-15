package com.lrenyi.template.entityplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体平台配置：API 前缀、权限默认策略等。
 */
@ConfigurationProperties(prefix = "app.entity-platform")
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

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix == null ? "/api" : apiPrefix;
    }

    public boolean isPermissionEnabled() {
        return permissionEnabled;
    }

    public void setPermissionEnabled(boolean permissionEnabled) {
        this.permissionEnabled = permissionEnabled;
    }

    public boolean isDefaultAllowIfNoPermission() {
        return defaultAllowIfNoPermission;
    }

    public void setDefaultAllowIfNoPermission(boolean defaultAllowIfNoPermission) {
        this.defaultAllowIfNoPermission = defaultAllowIfNoPermission;
    }

    public String getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(String scanPackages) {
        this.scanPackages = scanPackages != null ? scanPackages : "";
    }
}
