package com.lrenyi.template.dataforge.validation;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.support.DataforgeErrorCodes;
import com.lrenyi.template.dataforge.support.DataforgeHttpException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * 创建/更新时校验关联字段：非空时校验关联目标存在（自动排除软删除）。
 */
public class AssociationValidator {

    private final EntityRegistry entityRegistry;
    private final EntityCrudService crudService;

    public AssociationValidator(EntityRegistry entityRegistry, EntityCrudService crudService) {
        this.entityRegistry = entityRegistry;
        this.crudService = crudService;
    }

    /**
     * 校验实体中所有 foreignKey 字段：有值时关联目标必须存在。
     */
    public void validateAssociations(EntityMeta entityMeta, Object entity) {
        if (entityMeta == null || entityMeta.getFields() == null || entity == null) {
            return;
        }
        List<FieldMeta> foreignKeyFields = entityMeta.getFields().stream()
                .filter(FieldMeta::isForeignKey)
                .toList();
        if (foreignKeyFields.isEmpty()) {
            return;
        }
        var accessor = entityMeta.getAccessor();
        if (accessor == null) {
            return;
        }
        for (FieldMeta field : foreignKeyFields) {
            Object value = accessor.get(entity, field.getName());
            if (value == null) {
                if (field.isRequired()) {
                    throw new DataforgeHttpException(
                            HttpStatus.BAD_REQUEST.value(),
                            DataforgeErrorCodes.ASSOCIATION_TARGET_NOT_FOUND,
                            "关联字段不能为空: " + (StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getName()));
                }
                continue;
            }
            validateAssociationExists(entityMeta, field, value);
        }
    }

    private void validateAssociationExists(EntityMeta entityMeta, FieldMeta field, Object id) {
        String refEntity = field.getReferencedEntity();
        if (!StringUtils.hasText(refEntity)) {
            return;
        }
        EntityMeta referencedMeta = entityRegistry.getByEntityName(refEntity.trim());
        if (referencedMeta == null) {
            throw new DataforgeHttpException(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    DataforgeErrorCodes.ENTITY_NOT_FOUND,
                    "关联实体配置不存在: " + refEntity);
        }
        Object referenced = crudService.get(referencedMeta, id);
        if (referenced == null) {
            String label = StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getName();
            throw new DataforgeHttpException(
                    HttpStatus.BAD_REQUEST.value(),
                    DataforgeErrorCodes.ASSOCIATION_TARGET_NOT_FOUND,
                    label + " 不存在或已被删除");
        }
    }
}
