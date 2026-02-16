package com.lrenyi.template.platform.rbac.init;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lrenyi.template.platform.config.EntityPlatformProperties;
import com.lrenyi.template.platform.domain.Permission;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.registry.EntityRegistry;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/**
 * 应用启动后根据已注册的实体元数据，向 sys_permission 表补齐缺失的权限记录。
 * 仅当启用 JPA 且 app.platform.rbac.init-permissions 为 true 时执行；若应用未扫描 platform.domain，
 * Permission 不在持久化单元内则静默跳过。
 */
public class PermissionInitializer implements ApplicationRunner, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(PermissionInitializer.class);
    
    private final EntityRegistry entityRegistry;
    private final EntityManager entityManager;
    private final EntityPlatformProperties properties;
    
    public PermissionInitializer(EntityRegistry entityRegistry,
            EntityManager entityManager,
            EntityPlatformProperties properties) {
        this.entityRegistry = entityRegistry;
        this.entityManager = entityManager;
        this.properties = properties;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRbacInitPermissions()) {
            return;
        }
        Map<String, String> toInit = collectPermissionsToInit();
        if (toInit.isEmpty()) {
            return;
        }
        try {
            int added = ensurePermissionsExist(toInit);
            if (added > 0) {
                log.info("RBAC 权限初始化：新增 {} 条权限记录", added);
            }
        } catch (Exception e) {
            log.debug("RBAC 权限初始化跳过（可能未扫描 platform.domain 或未使用 JPA 管理 Permission）: {}",
                      e.getMessage()
            );
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
                "SELECT COUNT(p) FROM com.lrenyi.template.platform.domain.Permission p WHERE p.permission = :perm";
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
