package com.lrenyi.template.dataforge.backend.init;

import java.util.UUID;
import com.lrenyi.template.dataforge.backend.domain.User;
import com.lrenyi.template.dataforge.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始化测试用户（admin/admin123）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @PostConstruct
    @Transactional
    public void init() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            String randomPassword = UUID.randomUUID().toString();
            log.info("Generated admin password: {}", randomPassword);
            admin.setPassword(passwordEncoder.encode(randomPassword));
            admin.setEmail("admin@example.com");
            userRepository.save(admin);
        }
    }
}
