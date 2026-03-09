package com.lrenyi.template.dataforge.service;

import com.lrenyi.template.dataforge.annotation.CascadeStrategy;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.support.DataforgeErrorCodes;
import com.lrenyi.template.dataforge.support.DataforgeHttpException;
import com.lrenyi.template.dataforge.support.FilterCondition;
import com.lrenyi.template.dataforge.support.ListCriteria;
import com.lrenyi.template.dataforge.support.Op;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * 级联删除：查找引用、校验 RESTRICT、执行 SET_NULL/CASCADE，再由调用方执行主实体删除。
 */
public class CascadeDeleteService {

    private final EntityRegistry entityRegistry;
    private final EntityCrudService crudService;

    public CascadeDeleteService(EntityRegistry entityRegistry, EntityCrudService crudService) {
        this.entityRegistry = entityRegistry;
        this.crudService = crudService;
    }

    /**
     * 校验级联约束：若有引用且策略为 RESTRICT 则抛出异常。
     */
    public void checkCascadeConstraints(EntityMeta meta, Object id) {
        String entityName = meta.getEntityName();
        if (!StringUtils.hasText(entityName)) {
            return;
        }
        for (Reference ref : findReferences(entityName, id)) {
            if (ref.refMeta == null || ref.field == null || ref.refEntities.isEmpty()) {
                continue;
            }
            if (ref.field.getCascadeDelete() == CascadeStrategy.RESTRICT) {
                String refEntityName = ref.refMeta.getEntityName();
                throw new DataforgeHttpException(HttpStatus.CONFLICT.value(),
                        DataforgeErrorCodes.CASCADE_DELETE_RESTRICT,
                        "存在关联数据，无法删除。请先解除 " + refEntityName + " 中对本记录的引用。");
            }
        }
    }

    /**
     * 执行级联：SET_NULL 将引用方字段置空，CASCADE 删除引用方记录；不删除主实体。
     */
    public void executeCascadeDelete(EntityMeta meta, Object id) {
        String entityName = meta.getEntityName();
        if (!StringUtils.hasText(entityName)) {
            return;
        }
        for (Reference ref : findReferences(entityName, id)) {
            if (ref.refMeta == null || ref.field == null || ref.refEntities.isEmpty()) {
                continue;
            }
            CascadeStrategy strategy = ref.field.getCascadeDelete();
            var accessor = ref.refMeta.getAccessor();
            if (accessor == null) {
                continue;
            }
            if (strategy == CascadeStrategy.SET_NULL) {
                for (Object refEntity : ref.refEntities) {
                    Object refId = accessor.get(refEntity, "id");
                    if (refId != null) {
                        accessor.set(refEntity, ref.field.getName(), null);
                        crudService.update(ref.refMeta, refId, refEntity);
                    }
                }
            } else if (strategy == CascadeStrategy.CASCADE) {
                for (Object refEntity : ref.refEntities) {
                    Object refId = accessor.get(refEntity, "id");
                    if (refId != null) {
                        crudService.delete(ref.refMeta, refId);
                    }
                }
            }
        }
    }

    private List<Reference> findReferences(String referencedEntityName, Object id) {
        List<Reference> list = new ArrayList<>();
        List<EntityMeta> all = entityRegistry.getAll();
        for (EntityMeta refMeta : all) {
            if (refMeta.getFields() == null) {
                continue;
            }
            for (FieldMeta field : refMeta.getFields()) {
                if (!field.isForeignKey() || !referencedEntityName.equals(field.getReferencedEntity())) {
                    continue;
                }
                List<FilterCondition> filters = List.of(new FilterCondition(field.getName(), Op.EQ, id));
                List<Object> refEntities = crudService.list(refMeta,
                                org.springframework.data.domain.Pageable.unpaged(),
                                ListCriteria.of(filters, List.of()))
                        .getContent();
                list.add(new Reference(refMeta, field, refEntities));
            }
        }
        return list;
    }

    private static class Reference {
        final EntityMeta refMeta;
        final FieldMeta field;
        final List<Object> refEntities;

        Reference(EntityMeta refMeta, FieldMeta field, List<Object> refEntities) {
            this.refMeta = refMeta;
            this.field = field;
            this.refEntities = refEntities != null ? refEntities : List.of();
        }
    }
}
