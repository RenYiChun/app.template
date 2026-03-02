package com.lrenyi.template.dataforge.backend.action;

import java.util.Map;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.annotation.EntityAction;
import com.lrenyi.template.dataforge.backend.domain.User;
import org.springframework.stereotype.Component;

@Component
@EntityAction(
        entity = User.class, actionName = "resetPassword", requestType = ResetPasswordRequest.class,
        responseType = Void.class, summary = "重置用户密码", permissions = {"user:resetPassword"}
)
public class UserResetPasswordAction implements EntityActionExecutor {
    
    @Override
    public Object execute(Object entityId, Object request) {
        ResetPasswordRequest req = (ResetPasswordRequest) request;
        int len = (req != null && req.getNewPassword() != null) ? req.getNewPassword().length() : 0;
        return Map.of("userId", entityId, "message", "Password reset requested, newPassword length: " + len);
    }
}
