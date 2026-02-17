package com.lrenyi.template.platform.backend.init;

import com.lrenyi.template.platform.backend.repository.UserRepository;
import com.lrenyi.template.platform.domain.Permission;
import com.lrenyi.template.platform.domain.Role;
import com.lrenyi.template.platform.domain.RolePermission;
import com.lrenyi.template.platform.domain.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始化 RBAC 数据：默认角色、admin 用户角色关联。
 * 在 PermissionInitializer 之后执行，以便可为角色分配权限。
 */
@Component
public class RbacDataInitializer implements ApplicationRunner, Ordered {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ROLE_ADMIN_CODE = "ROLE_ADMIN";

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public RbacDataInitializer(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByUsername(ADMIN_USERNAME).ifPresent(admin -> {
            Role role = ensureRoleExists();
            ensureUserRoleExists(admin.getId(), role.getId());
            ensureRoleHasPermission(role.getId());
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE + 1;
    }

    private Role ensureRoleExists() {
        TypedQuery<Role> q = entityManager.createQuery(
                "SELECT r FROM com.lrenyi.template.platform.domain.Role r WHERE r.roleCode = :code",
                Role.class);
        q.setParameter("code", ROLE_ADMIN_CODE);
        List<Role> list = q.getResultList();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        Role role = new Role();
        role.setRoleCode(ROLE_ADMIN_CODE);
        role.setRoleName("管理员");
        entityManager.persist(role);
        return role;
    }

    private void ensureUserRoleExists(Long userId, Long roleId) {
        TypedQuery<Long> q = entityManager.createQuery(
                "SELECT COUNT(ur) FROM com.lrenyi.template.platform.domain.UserRole ur " +
                "WHERE ur.userId = :userId AND ur.role.id = :roleId",
                Long.class);
        q.setParameter("userId", ADMIN_USERNAME);
        q.setParameter("roleId", roleId);
        Long count = q.getSingleResult();
        if (count != null && count > 0) {
            return;
        }
        Role role = entityManager.find(Role.class, roleId);
        if (role == null) {
            return;
        }
        UserRole ur = new UserRole();
        ur.setUserId(ADMIN_USERNAME);
        ur.setRole(role);
        entityManager.persist(ur);
    }

    private void ensureRoleHasPermission(Long roleId) {
        TypedQuery<Long> q = entityManager.createQuery(
                "SELECT COUNT(rp) FROM com.lrenyi.template.platform.domain.RolePermission rp " +
                "WHERE rp.role.id = :roleId",
                Long.class);
        q.setParameter("roleId", roleId);
        Long count2 = q.getSingleResult();
        if (count2 != null && count2 > 0) {
            return;
        }
        TypedQuery<Permission> permQ = entityManager.createQuery(
                "SELECT p FROM com.lrenyi.template.platform.domain.Permission p WHERE p.permission LIKE 'users:%'",
                Permission.class);
        permQ.setMaxResults(1);
        List<Permission> perms = permQ.getResultList();
        if (perms.isEmpty()) {
            return;
        }
        Role role = entityManager.find(Role.class, roleId);
        if (role != null) {
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(perms.get(0));
            entityManager.persist(rp);
        }
    }
}
