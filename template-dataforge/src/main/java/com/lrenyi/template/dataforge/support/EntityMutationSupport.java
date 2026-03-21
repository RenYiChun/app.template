package com.lrenyi.template.dataforge.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.mapper.BaseMapper;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.support.EntityMapperProvider.MapperInfo;
import com.lrenyi.template.dataforge.validation.AssociationValidator;
import com.lrenyi.template.dataforge.validation.Create;
import com.lrenyi.template.dataforge.validation.Update;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;

/**
 * 统一封装实体创建/更新链路中的 DTO 映射、校验、关联校验和树循环检测。
 */
public class EntityMutationSupport {

    private static final String ERR_INVALID_REQUEST_DATA = "无效的请求数据";

    private final ObjectMapper objectMapper;
    private final ConversionService conversionService;
    private final EntityMapperProvider mapperProvider;
    private final DataforgeProperties properties;
    private final ObjectProvider<Validator> validatorProvider;
    private final ObjectProvider<AssociationValidator> associationValidatorProvider;
    private final EntityCrudService crudService;

    public EntityMutationSupport(ObjectMapper objectMapper,
            ConversionService conversionService,
            EntityMapperProvider mapperProvider,
            DataforgeProperties properties,
            ObjectProvider<Validator> validatorProvider,
            ObjectProvider<AssociationValidator> associationValidatorProvider,
            EntityCrudService crudService) {
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.mapperProvider = mapperProvider;
        this.properties = properties;
        this.validatorProvider = validatorProvider;
        this.associationValidatorProvider = associationValidatorProvider;
        this.crudService = crudService;
    }

    public MutationResult prepareCreate(EntityMeta meta, Map<String, Object> body) {
        return prepareEntity(meta, body, false, null);
    }

    public MutationResult prepareUpdate(EntityMeta meta, Map<String, Object> body, Object id) {
        return prepareEntity(meta, body, true, id);
    }

    public BatchMutationResult prepareBatchUpdate(EntityMeta meta, List<Map<String, Object>> body) {
        List<Object> entities = new ArrayList<>(body.size());
        Map<Object, Object> oldEntities = new LinkedHashMap<>();
        Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
        for (Map<String, Object> map : body) {
            Object idObj = map.get("id");
            if (idObj == null) {
                return BatchMutationResult.error(badRequest("批量更新项缺少 id"));
            }
            Object idParsed;
            try {
                idParsed = conversionService.convert(idObj, pkType);
            } catch (ConversionException | IllegalArgumentException e) {
                return BatchMutationResult.error(badRequest("id 格式错误: " + e.getMessage()));
            }
            Object oldEntity = crudService.get(meta, idParsed);
            if (oldEntity != null) {
                oldEntities.put(idParsed, oldEntity);
            }
            MutationResult result = prepareUpdate(meta, map, idParsed);
            if (result.error() != null) {
                return BatchMutationResult.error(result.error());
            }
            entities.add(result.entity());
        }
        return BatchMutationResult.success(entities, oldEntities);
    }

    private MutationResult prepareEntity(EntityMeta meta, Map<String, Object> body, boolean update, Object id) {
        MapperInfo info = mapperProvider.getMapperInfo(meta.getEntityClass());
        Object bodyEntity;
        if (update && info != null && info.updateDtoClass() != null) {
            Object dto = objectMapper.convertValue(body, info.updateDtoClass());
            Result<Object> error = validateAndReturnError(dto, Update.class);
            if (error != null) {
                return MutationResult.error(error);
            }
            bodyEntity = BeanUtils.instantiateClass(meta.getEntityClass());
            ((BaseMapper<Object, ?, Object, ?, ?>) info.mapper()).updateEntity(dto, bodyEntity);
        } else if (!update && info != null && info.createDtoClass() != null) {
            Object dto = objectMapper.convertValue(body, info.createDtoClass());
            Result<Object> error = validateAndReturnError(dto, Create.class);
            if (error != null) {
                return MutationResult.error(error);
            }
            bodyEntity = ((BaseMapper<Object, Object, ?, ?, ?>) info.mapper()).toEntity(dto);
        } else {
            try {
                bodyEntity = objectMapper.convertValue(body, meta.getEntityClass());
                Result<Object> error = validateAndReturnError(bodyEntity);
                if (error != null) {
                    return MutationResult.error(error);
                }
            } catch (IllegalArgumentException e) {
                return MutationResult.error(badRequest(ERR_INVALID_REQUEST_DATA));
            }
        }
        if (update) {
            setEntityId(bodyEntity, id);
            validateTreeNoCycle(meta, id, bodyEntity);
        }
        validateAssociations(meta, bodyEntity);
        return MutationResult.success(bodyEntity);
    }

    private void validateAssociations(EntityMeta meta, Object bodyEntity) {
        AssociationValidator validator = associationValidatorProvider.getIfAvailable();
        if (validator != null) {
            validator.validateAssociations(meta, bodyEntity);
        }
    }

    private void validateTreeNoCycle(EntityMeta meta, Object selfId, Object bodyEntity) {
        if (meta == null || !meta.isTreeEntity() || bodyEntity == null || meta.getAccessor() == null) {
            return;
        }
        String parentField = meta.getTreeParentField() != null && !meta.getTreeParentField().isBlank()
                ? meta.getTreeParentField() : "parentId";
        Object newParentId = meta.getAccessor().get(bodyEntity, parentField);
        if (newParentId == null) {
            return;
        }
        int maxDepth = meta.getTreeMaxDepth() > 0 ? meta.getTreeMaxDepth() : 10;
        Object current = newParentId;
        for (int i = 0; i < maxDepth; i++) {
            if (Objects.equals(current, selfId)) {
                throw new DataforgeHttpException(HttpStatus.BAD_REQUEST.value(),
                        DataforgeErrorCodes.CIRCULAR_REFERENCE, "不能将上级设为自己或自己的下级，否则将形成循环引用");
            }
            current = getParentId(meta, current);
            if (current == null) {
                break;
            }
        }
    }

    private Object getParentId(EntityMeta meta, Object id) {
        Object entity = crudService.get(meta, id);
        if (entity == null || meta.getAccessor() == null) {
            return null;
        }
        String parentField = meta.getTreeParentField() != null && !meta.getTreeParentField().isBlank()
                ? meta.getTreeParentField() : "parentId";
        return meta.getAccessor().get(entity, parentField);
    }

    private Result<Object> validateAndReturnError(Object dto, Class<?>... groups) {
        if (!properties.isValidationEnabled() || dto == null) {
            return null;
        }
        Validator validator = validatorProvider.getIfAvailable();
        if (validator == null) {
            return null;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(dto, groups);
        if (violations.isEmpty()) {
            return null;
        }
        String message = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    private static void setEntityId(Object entity, Object id) {
        if (entity == null || id == null) {
            return;
        }
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true); // NOSONAR
            idField.set(entity, id); // NOSONAR
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // ignore
        }
    }

    private static Result<Object> badRequest(String message) {
        Result<Object> r = Result.getError(null, message);
        r.setCode(400);
        return r;
    }

    public record MutationResult(Object entity, Result<Object> error) {
        public static MutationResult success(Object entity) {
            return new MutationResult(entity, null);
        }

        public static MutationResult error(Result<Object> error) {
            return new MutationResult(null, error);
        }
    }

    public record BatchMutationResult(List<Object> entities, Map<Object, Object> oldEntities, Result<Object> error) {
        public static BatchMutationResult success(List<Object> entities, Map<Object, Object> oldEntities) {
            return new BatchMutationResult(entities, oldEntities, null);
        }

        public static BatchMutationResult error(Result<Object> error) {
            return new BatchMutationResult(List.of(), Map.of(), error);
        }
    }
}
