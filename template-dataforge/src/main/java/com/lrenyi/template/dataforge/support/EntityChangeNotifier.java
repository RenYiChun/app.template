package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.dataforge.meta.EntityMeta;

/**
 * 实体变更通知：在 create/update/delete 成功后调用，供前端缓存失效（如 AssociationCache）或 WebSocket 推送等使用。
 * 应用层可注册实现为 Bean，Controller 在写操作成功后按 entityName 通知。
 */
public interface EntityChangeNotifier {

    /**
     * 某实体新增了一条记录
     */
    void notifyCreated(EntityMeta meta, Object id);

    /**
     * 某实体更新了一条记录
     */
    void notifyUpdated(EntityMeta meta, Object id);

    /**
     * 某实体删除了一条记录（含批量删除中的每条）
     */
    void notifyDeleted(EntityMeta meta, Object id);
}
