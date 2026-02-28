package com.lrenyi.template.dataforge.backend.auth;

import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.annotation.EntityAction;
import com.lrenyi.template.dataforge.backend.domain.Auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
@EntityAction(
        entity = Auth.class, actionName = "logout", method = RequestMethod.POST, summary = "登出", requireId = false
)
public class AuthLogoutAction implements EntityActionExecutor {
    
    @Override
    public Object execute(Object entityId, Object request) {
        SecurityContextHolder.clearContext();
        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return null;
    }
}
