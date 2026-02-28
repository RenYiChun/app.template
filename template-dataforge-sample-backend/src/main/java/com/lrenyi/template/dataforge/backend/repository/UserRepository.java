package com.lrenyi.template.dataforge.backend.repository;

import java.util.Optional;
import com.lrenyi.template.dataforge.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
}
