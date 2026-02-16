package com.lrenyi.template.platform.action;

/**
 * 实体扩展动作执行接口。实现类由开发者编写，并通过 @EntityAction 注解注册。
 */
public interface EntityActionExecutor {
    
    /**
     * 执行动作。
     *
     * @param entityId 实体主键（Long、String、UUID 等，与实体主键类型一致）
     * @param request  请求体，可为 null（当 requestType 为 Void 时）
     * @return 返回值，将序列化为 HTTP 响应体
     */
    Object execute(Object entityId, Object request);
}
