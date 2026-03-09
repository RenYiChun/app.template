package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import java.util.List;
import java.util.Map;

/**
 * 关联字段变更审计：在 update 成功后，对比旧值与新值，若有关联字段变化则回调（可异步写入审计日志）。
 * 应用层实现并注册为 Bean 后生效。
 */
public interface AssociationChangeAuditor {

    /**
     * 某实体的关联字段发生变更，可在此记录审计（建议异步、不阻塞主流程）。
     *
     * @param meta 当前实体元数据
     * @param id 主键
     * @param changedFields 发生变化的 foreignKey 字段列表
     * @param oldDisplayValues 旧显示值，key 为字段名，value 为当时关联的 display 值（可能为空）
     * @param newDisplayValues 新显示值，key 为字段名，value 为当前关联的 display 值（可能为空）
     */
    void auditAssociationChanges(EntityMeta meta, Object id, List<FieldMeta> changedFields,
            Map<String, Object> oldDisplayValues, Map<String, Object> newDisplayValues);
}
