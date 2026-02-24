package com.lrenyi.template.dataforge.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lrenyi.template.dataforge.domain.Permission;
import com.lrenyi.template.dataforge.meta.ActionMeta;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 应用启动后根据已注册的实体元数据（CRUD + Action 权限），向 sys_permission 表补齐缺失的权限记录。
 * 仅当启用 JPA 且 app.dataforge.rbac.init-permissions 为 true 时执行；若应用未扫描 dataforge.domain，
 * Permission 不在持久化单元内会记录 WARN 日志便于排查。
 */
public class PermissionInitializer implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PermissionInitializer.class);

    private final EntityRegistry entityRegistry;
    private final EntityManager entityManager;
    private final DataforgeProperties properties;
    private final ObjectProvider<TransactionTemplate> transactionTemplateProvider;

    public PermissionInitializer(EntityRegistry entityRegistry,
            EntityManager entityManager,
            DataforgeProperties properties,
            ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
        this.entityRegistry = entityRegistry;
        this.entityManager = entityManager;
        this.properties = properties;
        this.transactionTemplateProvider = transactionTemplateProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        int entityCount = entityRegistry.getAll().size();
        log.info("RBAC 权限初始化开始，已注册实体数: {}（init-permissions={}）",
                entityCount, properties.isRbacInitPermissions());
        if (!properties.isRbacInitPermissions()) {
            return;
        }
        TransactionTemplate transactionTemplate = transactionTemplateProvider.getIfAvailable();
        if (transactionTemplate == null) {
            log.warn("RBAC 权限初始化跳过：TransactionTemplate 不可用（需 DataforgeTransactionManager）");
            return;
        }
        Map<String, String> toInit = collectPermissionsToInit();
        if (toInit.isEmpty()) {
            log.warn("RBAC 权限初始化：未收集到待初始化权限（请确认 app.dataforge.scan-packages 已包含实体包，如 com.lrenyi.template.dataforge.domain）");
            return;
        }
        try {
            Integer added = transactionTemplate.execute(status -> {
                int n = ensurePermissionsExist(toInit);
                entityManager.flush();
                return n;
            });
            if (added != null && added > 0) {
                log.info("RBAC 权限初始化：新增 {} 条权限记录", added);
            }
        } catch (Exception e) {
            log.warn("RBAC 权限初始化失败，sys_permission 将无自动补齐数据（请确认已扫描 dataforge.domain 且使用 JPA）：{}",
                    e.getMessage(), e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
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
                    String actionDesc = action.getSummary() != null && !action.getSummary().isBlank()
                            ? action.getSummary() : (action.getActionName() != null ? action.getActionName() : "自定义");
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
    
    private int ensurePermissionsExist(Map<String, String> permToDesc) {
        String jpql =
                "SELECT COUNT(p) FROM com.lrenyi.template.dataforge.domain.Permission p WHERE p.permission = :perm";
        int added = 0;
        for (Map.Entry<String, String> e : permToDesc.entrySet()) {
            String perm = e.getKey();
            String description = e.getValue();
            Long count = entityManager.createQuery(jpql, Long.class).setParameter("perm", perm).getSingleResult();
            if (count != null && count == 0) {
                Permission p = new Permission();
                p.setPermission(perm);
                p.setName(perm);
                p.setDescription(description != null && description.length() > 256 ? description.substring(0, 256) :
                                         description);
                entityManager.persist(p);
                added++;
            }
        }
        return added;
    }
}
