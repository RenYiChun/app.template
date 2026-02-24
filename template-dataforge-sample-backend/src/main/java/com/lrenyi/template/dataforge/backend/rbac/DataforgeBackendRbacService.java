package com.lrenyi.template.dataforge.backend.rbac;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.lrenyi.oauth2.service.config.IdentifierType;
import com.lrenyi.template.api.rbac.model.AppUser;
import com.lrenyi.template.api.rbac.model.Permission;
import com.lrenyi.template.api.rbac.model.Role;
import com.lrenyi.template.dataforge.backend.domain.User;
import com.lrenyi.template.dataforge.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

/**
 * 平台后端 RBAC 实现，供 OAuth2 Password Grant 用户认证使用。
 * UserRole.userId 使用 username，与认证用户标识一致。
 */
@Service
public class DataforgeBackendRbacService implements com.lrenyi.oauth2.service.oauth2.password.IRbacService {

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public DataforgeBackendRbacService(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Override
    public AppUser<?> findUserByIdentifier(String identifier, IdentifierType identifierType) {
        if (identifier.isBlank()) {
            return null;
        }
        Optional<User> userOpt = switch (identifierType) {
            case USERNAME -> userRepository.findByUsername(identifier.trim());
            case EMAIL, EMPLOYEE_ID -> Optional.empty();
        };
        return userOpt.map(this::toAppUser).orElse(null);
    }

    @Override
    public <T> List<Role> getRolesByUserId(T userId) {
        String userIdStr = resolveUserId(userId);
        if (userIdStr == null) {
            return Collections.emptyList();
        }
        String jpql = """
                SELECT ur.role FROM com.lrenyi.template.dataforge.domain.UserRole ur
                WHERE ur.userId = :userId
                """;
        TypedQuery<com.lrenyi.template.dataforge.domain.Role> query =
                entityManager.createQuery(jpql, com.lrenyi.template.dataforge.domain.Role.class);
        query.setParameter("userId", userIdStr);
        return query.getResultList().stream().map(RoleAdapter::new).collect(Collectors.toList());
    }

    private String resolveUserId(Object userId) {
        if (userId instanceof Long id) {
            return userRepository.findById(id).map(User::getUsername).orElse(null);
        }
        return userId != null ? userId.toString() : null;
    }

    @Override
    public <T> List<Permission> getPermissionsByRoleId(T roleId) {
        Object id = roleId;
        if (roleId instanceof String s) {
            try {
                id = Long.parseLong(s);
            } catch (NumberFormatException e) {
                return Collections.emptyList();
            }
        }
        String jpql = """
                SELECT rp.permission FROM com.lrenyi.template.dataforge.domain.RolePermission rp
                WHERE rp.role.id = :roleId
                """;
        TypedQuery<com.lrenyi.template.dataforge.domain.Permission> query =
                entityManager.createQuery(jpql, com.lrenyi.template.dataforge.domain.Permission.class);
        query.setParameter("roleId", id);
        return query.getResultList().stream().map(PermissionAdapter::new).collect(Collectors.toList());
    }

    private AppUser<Long> toAppUser(User user) {
        AppUser<Long> app = new AppUser<>();
        app.setId(user.getId());
        app.setUsername(user.getUsername());
        app.setPassword(user.getPassword());
        return app;
    }

    private static final class RoleAdapter implements Role {
        private final com.lrenyi.template.dataforge.domain.Role role;

        RoleAdapter(com.lrenyi.template.dataforge.domain.Role role) {
            this.role = role;
        }

        @Override
        public String getId() {
            return role.getId() != null ? role.getId().toString() : null;
        }

        @Override
        public void setId(String id) {}

        @Override
        public String getCode() {
            return role.getRoleCode();
        }

        @Override
        public void setCode(String code) {}

        @Override
        public String getName() {
            return role.getRoleName();
        }

        @Override
        public void setName(String name) {}

        @Override
        public List<Permission> getPermissions() {
            return Collections.emptyList();
        }

        @Override
        public void setPermissions(List<Permission> permissions) {}
    }
    
    private record PermissionAdapter(com.lrenyi.template.dataforge.domain.Permission perm) implements Permission {
        
        @Override
        public String getId() {
            return perm.getId() != null ? perm.getId().toString() : null;
        }
        
        @Override
        public void setId(String id) {}
        
        @Override
        public String getName() {
            return perm.getName();
        }
        
        @Override
        public void setName(String name) {}
        
        @Override
        public String getPermission() {
            return perm.getPermission();
        }
        
        @Override
        public void setPermission(String permission) {}
    }
}
