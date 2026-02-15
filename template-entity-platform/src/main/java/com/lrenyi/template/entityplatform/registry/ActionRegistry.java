package com.lrenyi.template.entityplatform.registry;

import com.lrenyi.template.entityplatform.action.EntityActionExecutor;
import com.lrenyi.template.entityplatform.meta.ActionMeta;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Action 注册表：按 entityPathSegment + actionName 查找执行器与元数据。
 */
public class ActionRegistry {

    private final Map<String, EntityActionExecutor> executorMap = new ConcurrentHashMap<>();
    private final Map<String, ActionMeta> metaMap = new ConcurrentHashMap<>();

    public static String key(String entityPathSegment, String actionName) {
        return (entityPathSegment == null ? "" : entityPathSegment) + ":" + (actionName == null ? "" : actionName);
    }

    public void register(String entityPathSegment, String actionName, ActionMeta meta, EntityActionExecutor executor) {
        String k = key(entityPathSegment, actionName);
        if (meta != null) {
            metaMap.put(k, meta);
        }
        if (executor != null) {
            executorMap.put(k, executor);
        }
    }

    public EntityActionExecutor getExecutor(String entityPathSegment, String actionName) {
        return executorMap.get(key(entityPathSegment, actionName));
    }

    public ActionMeta getMeta(String entityPathSegment, String actionName) {
        return metaMap.get(key(entityPathSegment, actionName));
    }
}
