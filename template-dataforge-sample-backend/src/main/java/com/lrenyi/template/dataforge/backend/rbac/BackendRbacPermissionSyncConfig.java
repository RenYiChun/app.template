package com.lrenyi.template.dataforge.backend.rbac;

import java.util.Map;
import com.lrenyi.template.dataforge.backend.domain.Permission;
import com.lrenyi.template.dataforge.rbac.RbacPermissionSync;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 业务层 RBAC 权限同步实现：使用 Permission 实体将权限码落库。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(EntityManager.class)
class BackendRbacPermissionSyncConfig {
    
    @Bean
    public RbacPermissionSync backendRbacPermissionSync(EntityManager entityManager,
            TransactionTemplate transactionTemplate) {
        return new BackendRbacPermissionSyncImpl(entityManager, transactionTemplate);
    }
    
    private record BackendRbacPermissionSyncImpl(EntityManager entityManager,
                                                 TransactionTemplate transactionTemplate) implements
            RbacPermissionSync {
        private static final String COUNT_JPQL =
                "SELECT COUNT(p) FROM com.lrenyi.template.dataforge.backend.domain.Permission p "
                        + "WHERE p.permission = :perm";
        
        @Override
        public int ensurePermissionsExist(Map<String, String> codeToDescription) {
            Integer added = transactionTemplate.execute(status -> {
                int n = 0;
                for (Map.Entry<String, String> e : codeToDescription.entrySet()) {
                    String perm = e.getKey();
                    String description = e.getValue();
                    Long count = entityManager.createQuery(COUNT_JPQL, Long.class)
                                              .setParameter("perm", perm)
                                              .getSingleResult();
                    if (count != null && count == 0) {
                        Permission p = new Permission();
                        p.setPermission(perm);
                        p.setName(perm);
                        p.setDescription(
                                description != null && description.length() > 256 ? description.substring(0, 256) :
                                        description);
                        entityManager.persist(p);
                        n++;
                    }
                }
                entityManager.flush();
                return n;
            });
            return added != null ? added : 0;
        }
    }
}
