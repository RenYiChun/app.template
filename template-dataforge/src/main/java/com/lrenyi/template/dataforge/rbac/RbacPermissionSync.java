package com.lrenyi.template.dataforge.rbac;

import java.util.Map;

/**
 * RBAC 权限同步接口：将权限码集合写入持久层。
 * 平台根据 EntityMeta/ActionMeta 收集权限，业务实现负责落库。
 */
@FunctionalInterface
public interface RbacPermissionSync {
    
    /**
     * 确保权限码在持久层存在，不存在则新增。
     *
     * @param codeToDescription 权限码 -> 描述
     * @return 新增条数
     */
    int ensurePermissionsExist(Map<String, String> codeToDescription);
}
