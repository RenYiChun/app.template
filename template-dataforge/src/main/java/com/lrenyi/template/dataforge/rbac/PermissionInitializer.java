package com.lrenyi.template.dataforge.rbac;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.meta.ActionMeta;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/**
 * 应用启动后根据已注册的实体元数据（CRUD + Action 权限），向持久层补齐缺失的权限记录。
 */
@Slf4j
public class PermissionInitializer implements ApplicationRunner, Ordered {
    
    private final EntityRegistry entityRegistry;
    private final DataforgeProperties properties;
    private final RbacPermissionSync rbacPermissionSync;
    
    public PermissionInitializer(ObjectProvider<EntityRegistry> entityRegistry,
            ObjectProvider<DataforgeProperties> properties,
            ObjectProvider<RbacPermissionSync> rbacPermissionSyncProvider) {
        this.entityRegistry = entityRegistry.getIfAvailable();
        this.properties = properties.getIfAvailable();
        this.rbacPermissionSync = rbacPermissionSyncProvider.getIfAvailable();
    }
    
    @Override
    public void run(ApplicationArguments args) {
        if (entityRegistry == null || properties == null || rbacPermissionSync == null) {
            log.debug("RBAC 权限初始化跳过：必要 Bean 不可用（需 RbacPermissionSync 实现）");
            return;
        }
        int entityCount = entityRegistry.getAll().size();
        log.info("RBAC 权限初始化开始，已注册实体数: {}（init-permissions={}）",
                 entityCount,
                 properties.isRbacInitPermissions()
        );
        if (!properties.isRbacInitPermissions()) {
            return;
        }
        Map<String, String> toInit = collectPermissionsToInit();
        if (toInit.isEmpty()) {
            log.warn("RBAC 权限初始化：未收集到待初始化权限（请确认 app.dataforge.scan-packages 已包含实体包）");
            return;
        }
        try {
            int added = rbacPermissionSync.ensurePermissionsExist(toInit);
            if (added > 0) {
                log.info("RBAC 权限初始化：新增 {} 条权限记录", added);
            }
        } catch (Exception e) {
            log.warn("RBAC 权限初始化失败：{}", e.getMessage(), e);
        }
    }
    
    private Map<String, String> collectPermissionsToInit() {
        Map<String, String> permToDesc = new LinkedHashMap<>();
        List<EntityMeta> all = entityRegistry.getAll();
        for (EntityMeta meta : all) {
            String displayName = meta.getDisplayName() != null ? meta.getDisplayName() : meta.getPathSegment();
            addIfNonBlank(permToDesc, meta.getPermissionCreate(), displayName, "创建");
            addIfNonBlank(permToDesc, meta.getPermissionRead(), displayName, "查询");
            addIfNonBlank(permToDesc, meta.getPermissionUpdate(), displayName, "更新");
            addIfNonBlank(permToDesc, meta.getPermissionDelete(), displayName, "删除");
            if (meta.getActions() != null) {
                for (ActionMeta action : meta.getActions()) {
                    String actionDesc =
                            action.getSummary() != null && !action.getSummary().isBlank() ? action.getSummary() :
                                    (action.getActionName() != null ? action.getActionName() : "自定义");
                    if (action.getPermissions() != null) {
                        for (String p : action.getPermissions()) {
                            addIfNonBlank(permToDesc, p, displayName, "Action " + actionDesc);
                        }
                    }
                }
            }
        }
        return permToDesc;
    }
    
    private static void addIfNonBlank(Map<String, String> map,
            String permission,
            String displayName,
            String actionDesc) {
        if (permission != null && !permission.isBlank()) {
            String key = permission.trim();
            map.putIfAbsent(key, displayName + " " + actionDesc);
        }
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
