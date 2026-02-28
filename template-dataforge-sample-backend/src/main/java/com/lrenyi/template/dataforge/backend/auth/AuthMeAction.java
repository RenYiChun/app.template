package com.lrenyi.template.dataforge.backend.auth;

 import java.util.Map;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.annotation.EntityAction;
import com.lrenyi.template.dataforge.backend.domain.Auth;
import com.lrenyi.template.dataforge.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

@Component
@RequiredArgsConstructor
@EntityAction(
        entity = Auth.class, actionName = "me", method = RequestMethod.GET, summary = "获取当前用户", requireId = false
)
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
                .map(u -> Map.of("id", u.getId(), "username", u.getUsername(),
                        "email", u.getEmail() != null ? u.getEmail() : ""))
                .orElse(null);
    }
}
