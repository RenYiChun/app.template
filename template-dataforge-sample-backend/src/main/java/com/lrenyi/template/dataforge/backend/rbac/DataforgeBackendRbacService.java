package com.lrenyi.template.dataforge.backend.rbac;

import java.util.List;
import java.util.Optional;
import com.lrenyi.oauth2.service.config.IdentifierType;
import com.lrenyi.oauth2.service.oauth2.password.RbacUserCredentials;
import com.lrenyi.template.dataforge.backend.domain.User;
import com.lrenyi.template.dataforge.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.jspecify.annotations.NonNull;
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
    public RbacUserCredentials loadUserCredentials(@NonNull String identifier, @NonNull IdentifierType identifierType) {
        if (identifier.isBlank()) {
            return null;
        }
        Optional<User> userOpt = switch (identifierType) {
            case USERNAME -> userRepository.findByUsername(identifier.trim());
            case EMAIL, EMPLOYEE_ID -> Optional.empty();
        };
        return userOpt.map(user -> {
            List<String> permissions = getPermissionStringsByUsername(user.getUsername());
            return new RbacUserCredentials(user.getUsername(), user.getPassword(), permissions);
        }).orElse(null);
    }
    
    private List<String> getPermissionStringsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        String jpql = """
                SELECT DISTINCT p.permission FROM RolePermission rp
                JOIN rp.role r
                JOIN rp.permission p
                WHERE r.id IN (SELECT ur.role.id FROM UserRole ur WHERE ur.userId = :userId)
                """;
        TypedQuery<String> query = entityManager.createQuery(jpql, String.class);
        query.setParameter("userId", username.trim());
        return query.getResultList();
    }
}
