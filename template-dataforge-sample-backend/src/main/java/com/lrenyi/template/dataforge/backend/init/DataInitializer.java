package com.lrenyi.template.dataforge.backend.init;

import java.util.UUID;
import com.lrenyi.template.dataforge.backend.domain.Department;
import com.lrenyi.template.dataforge.backend.domain.User;
import com.lrenyi.template.dataforge.backend.repository.DepartmentRepository;
import com.lrenyi.template.dataforge.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始化根部门与测试用户（admin）。admin 默认挂靠到根部门。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private static final String ROOT_DEPARTMENT_NAME = "根部门";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    @Transactional
    public void init() {
        Department root = ensureRootDepartment();
        userRepository.findByUsername("admin").ifPresentOrElse(
                admin -> {
                    if (admin.getDepartmentId() == null) {
                        admin.setDepartmentId(root.getId());
                        userRepository.save(admin);
                        log.info("Admin user attached to root department");
                    }
                },
                () -> {
                    User admin = new User();
                    admin.setUsername("admin");
                    String randomPassword = UUID.randomUUID().toString();
                    log.info("Generated admin password: {}", randomPassword);
                    admin.setPassword(passwordEncoder.encode(randomPassword));
                    admin.setEmail("admin@example.com");
                    admin.setDepartmentId(root.getId());
                    userRepository.save(admin);
                }
        );
    }

    /** 确保存在根部门（parentId 为 null），不存在则创建。 */
    private Department ensureRootDepartment() {
        return departmentRepository.findFirstByParentIdIsNullOrderBySortOrderAsc()
                .orElseGet(() -> {
                    Department root = new Department();
                    root.setName(ROOT_DEPARTMENT_NAME);
                    root.setParentId(null);
                    root.setSortOrder(0);
                    root.setStatus("1");
                    return departmentRepository.save(root);
                });
    }
}
