package com.lrenyi.template.dataforge.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.meta.ActionMeta;

/**
 * Action 注册表：按 entityPathSegment + actionName 查找执行器与元数据。
 */
public class ActionRegistry {
    
    private final Map<String, EntityActionExecutor> executorMap = new ConcurrentHashMap<>();
    private final Map<String, ActionMeta> metaMap = new ConcurrentHashMap<>();
    
    public void register(String entityPathSegment, String actionName, ActionMeta meta, EntityActionExecutor executor) {
        String k = key(entityPathSegment, actionName);
        if (meta != null) {
            metaMap.put(k, meta);
        }
        if (executor != null) {
            executorMap.put(k, executor);
        }
    }
    
    public static String key(String entityPathSegment, String actionName) {
        return (entityPathSegment == null ? "" : entityPathSegment) + ":" + (actionName == null ? "" : actionName);
    }
    
    public EntityActionExecutor getExecutor(String entityPathSegment, String actionName) {
        return executorMap.get(key(entityPathSegment, actionName));
    }
    
    public ActionMeta getMeta(String entityPathSegment, String actionName) {
        return metaMap.get(key(entityPathSegment, actionName));
    }
}
