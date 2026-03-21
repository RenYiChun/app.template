package com.lrenyi.template.dataforge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体平台配置：API 前缀、权限默认策略等。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.dataforge")
public class DataforgeProperties {
    
    /**
     * API 文档界面入口路径的默认值，可通过 app.dataforge.docs-ui-path 自定义。
     */
    public static final String DEFAULT_DOCS_UI_PATH = "/docs";
    
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
     * 数据权限拒绝时是否伪装为 404。
     * true = 返回 404 隐藏资源存在性；false = 返回 403 显式告知无权限。
     */
    private boolean concealDataPermissionDeniedAsNotFound = true;
    
    /**
     * 扫描 @DataforgeEntity 的包名，多个用逗号分隔。为空则不扫描实体。
     */
    private String scanPackages = "";
    
    /**
     * RBAC 用户权限解析缓存 TTL（分钟）。&lt;=0 表示不缓存，每次请求查库；&gt;0 时使用本地缓存，过期后反映角色权限动态变更。
     */
    private int rbacCacheTtlMinutes = 5;
    
    /**
     * 是否在启动时根据已注册实体自动初始化 sys_permission 缺失的权限记录。需 JPA 且应用扫描 dataforge.domain 时生效。
     */
    private boolean rbacInitPermissions = true;
    
    /**
     * 是否启用内嵌 API 文档界面（Swagger UI）。为 true 时访问 docs-ui-path 可打开文档页。
     */
    private boolean docsUiEnabled = true;
    
    /**
     * API 文档界面入口路径，默认 /docs。仅当 docs-ui-enabled 为 true 时生效。
     * 可通过 app.dataforge.docs-ui-path 自定义。
     */
    private String docsUiPath = DEFAULT_DOCS_UI_PATH;
    
    /**
     * 列表接口每页最大条数。超过此值的 size 请求将被截断，避免大表全量查询导致 OOM。
     */
    private int maxPageSize = 500;
    
    /**
     * 导出 Excel 单次最大条数。超过此值的 size 请求将被截断。
     */
    private int maxExportSize = 50000;
    
    /**
     * 是否向客户端暴露异常详情（e.getMessage()）。生产环境应设为 false，避免泄露路径、SQL 等敏感信息。
     * 开发/调试时可设为 true。
     */
    private boolean exposeExceptionMessage = false;
    
    /**
     * 是否对 create/update 请求体进行 Bean Validation 校验。需 classpath 存在 spring-boot-starter-validation。
     * 在生成的 CreateDTO/UpdateDTO 或自定义 DTO 上添加 @NotNull、@Size 等注解即可生效。
     */
    private boolean validationEnabled = true;
    
    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix == null ? "/api" : apiPrefix;
    }
    
    public void setScanPackages(String scanPackages) {
        this.scanPackages = scanPackages != null ? scanPackages : "";
    }
    
    public void setDocsUiPath(String docsUiPath) {
        this.docsUiPath = docsUiPath != null ? docsUiPath : DEFAULT_DOCS_UI_PATH;
    }
}
