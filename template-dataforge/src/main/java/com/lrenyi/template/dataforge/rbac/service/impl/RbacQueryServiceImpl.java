package com.lrenyi.template.dataforge.rbac.service.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.lrenyi.template.dataforge.rbac.service.RbacQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * 基于 EntityManager + JPQL 的 RBAC 权限查询实现。
 */
public class RbacQueryServiceImpl implements RbacQueryService {
    
    private final EntityManager entityManager;
    
    public RbacQueryServiceImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
    @Override
    public Set<String> getPermissionStringsByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptySet();
        }
        String jpql = """
                select distinct p.permission from RolePermission rp
                join rp.role r
                join rp.permission p
                where r.id in (select ur.role.id from UserRole ur where ur.userId = :userId)
                """;
        TypedQuery<String> query = entityManager.createQuery(jpql, String.class);
        query.setParameter("userId", userId.trim());
        return new HashSet<>(query.getResultList());
    }
}
