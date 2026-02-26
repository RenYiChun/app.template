package com.lrenyi.oauth2.service.oauth2.password;

import com.lrenyi.oauth2.service.config.IdentifierType;

public interface IRbacService {

    /**
     * 根据标识类型与标识加载用户认证信息（用户名、密码、权限字符串列表）。
     * 业务侧从自有 User/Role/Permission 表查询后扁平化为权限字符串即可。
     *
     * @param identifier     用户标识（如 username、employeeId）
     * @param identifierType 标识类型
     * @return 认证用凭证，用户不存在时返回 null
     */
    RbacUserCredentials loadUserCredentials(String identifier, IdentifierType identifierType);
}
