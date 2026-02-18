package com.lrenyi.template.platform.backend.auth;

import com.lrenyi.template.platform.annotation.EntityAction;
import com.lrenyi.template.platform.backend.domain.Auth;
import com.lrenyi.template.platform.backend.domain.User;
import com.lrenyi.template.platform.backend.repository.UserRepository;
import com.lrenyi.template.platform.action.EntityActionExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

@Component
@RequiredArgsConstructor
@EntityAction(entity = Auth.class, actionName = "me", method = RequestMethod.GET, summary = "获取当前用户", requireId = false)
public class AuthMeAction implements EntityActionExecutor {

    private final UserRepository userRepository;

    @Override
    public Object execute(Object entityId, Object request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .map(u -> java.util.Map.of("id", u.getId(), "username", u.getUsername(),
                        "email", u.getEmail() != null ? u.getEmail() : ""))
                .orElse(null);
    }
}
