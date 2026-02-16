package com.lrenyi.template.platform.controller;

import com.lrenyi.template.platform.config.EntityPlatformProperties;
import com.lrenyi.template.platform.meta.ActionMeta;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.meta.FieldMeta;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.support.EntityDtoResolver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * 根据 GenericEntityController 的映射自动发现接口，结合 EntityMeta/ActionMeta 生成 OpenAPI 3.0 文档（JSON），
 * 供 Swagger UI 等标准文档界面使用。
 */
@RestController
@RequestMapping("${app.platform.api-prefix:/api}")
public class OpenApiController {

    private static final String DEFAULT_RESPONSE_DESC = "成功时 data 为实体或列表，删除成功时 data 为 null；异常时由 Result 包装。";

    private final EntityRegistry entityRegistry;
    private final RequestMappingHandlerMapping handlerMapping;
    private final EntityPlatformProperties properties;

    public OpenApiController(EntityRegistry entityRegistry,
            RequestMappingHandlerMapping handlerMapping,
            EntityPlatformProperties properties) {
        this.entityRegistry = entityRegistry;
        this.handlerMapping = handlerMapping;
        this.properties = properties;
    }

    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> docs() {
        Map<String, Map<String, Object>> pathsMap = new HashMap<>();
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod hm = entry.getValue();
            if (!GenericEntityController.class.isAssignableFrom(hm.getBeanType())) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            var pathCondition = info.getPathPatternsCondition();
            if (pathCondition == null) {
                continue;
            }
            Set<PathPattern> patterns = pathCondition.getPatterns();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            boolean hasRequestBody = hasRequestBody(hm.getMethod());
            String methodName = hm.getMethod().getName();
            for (PathPattern pp : patterns) {
                String patternStr = pp.getPatternString();
                for (RequestMethod m : methods) {
                    String httpMethod = m.name().toLowerCase();
                    if (patternStr.contains("{actionName}")) {
                        for (EntityMeta entity : entityRegistry.getAll()) {
                            for (ActionMeta action : entity.getActions()) {
                                String path = patternStr
                                        .replace("{entity}", entity.getPathSegment())
                                        .replace("{actionName}", action.getActionName());
                                boolean needBody = action.getRequestType() != null
                                        && action.getRequestType() != Void.class;
                                String tag = tagForEntity(entity);
                                putOperation(pathsMap, path, httpMethod, action.getSummary(),
                                        entity.getPathSegment() + "_" + action.getActionName(),
                                        needBody, action.getPermissions().isEmpty() ? null : action.getPermissions(), tag, entity,
                                        null, false, false, null);
                            }
                        }
                    } else {
                        for (EntityMeta entity : entityRegistry.getAll()) {
                            if (!isOperationEnabled(entity, methodName)) {
                                continue;
                            }
                            String path = patternStr.replace("{entity}", entity.getPathSegment());
                            String summary = summaryFromMethodName(methodName);
                            Object permissions = permissionsForMethod(methodName, entity);
                            String operationId = entity.getPathSegment() + "_" + methodName;
                            String tag = tagForEntity(entity);
                            String requestSchema = getRequestSchemaForMethod(methodName, entity);
                            boolean requestSchemaArray = isRequestSchemaArrayForMethod(methodName);
                            boolean requestSchemaArrayOfIds = isRequestSchemaArrayOfIdsForMethod(methodName);
                            String responseSchema = getResponseSchemaForMethod(methodName, entity);
                            putOperation(pathsMap, path, httpMethod, summary, operationId, hasRequestBody,
                                    permissions != null && !"".equals(permissions) ? permissions : null, tag, entity,
                                    requestSchema, requestSchemaArray, requestSchemaArrayOfIds, responseSchema);
                        }
                    }
                }
            }
        }
        List<Map<String, String>> tagsList = new ArrayList<>();
        for (EntityMeta entity : entityRegistry.getAll()) {
            Map<String, String> tagDoc = new HashMap<>();
            tagDoc.put("name", tagForEntity(entity));
            tagDoc.put("description", "pathSegment: " + entity.getPathSegment());
            tagsList.add(tagDoc);
        }
        Map<String, Object> schemas = buildSchemas();
        Map<String, Object> doc = new HashMap<>();
        doc.put("openapi", "3.0.0");
        doc.put("info", Map.of("title", "Entity Platform API", "version", "1.0"));
        doc.put("tags", tagsList);
        doc.put("paths", pathsMap);
        doc.put("components", Map.of("schemas", schemas));
        return ResponseEntity.ok(doc);
    }

    private Map<String, Object> buildSchemas() {
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (EntityMeta entity : entityRegistry.getAll()) {
            String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
            if (simpleName == null) {
                continue;
            }
            try {
                Class<?> createDto = EntityDtoResolver.resolveCreateDto(entity);
                Class<?> updateDto = EntityDtoResolver.resolveUpdateDto(entity);
                Class<?> responseDto = EntityDtoResolver.resolveResponseDto(entity);
                if (createDto != null) {
                    schemas.put(createDto.getSimpleName(), buildDtoSchema(createDto));
                } else {
                    schemas.put(simpleName + "CreateDTO", buildSchemaFromEntityFields(entity, false));
                }
                if (updateDto != null) {
                    schemas.put(updateDto.getSimpleName(), buildDtoSchema(updateDto));
                } else {
                    schemas.put(simpleName + "UpdateDTO", buildSchemaFromEntityFields(entity, true));
                }
                if (responseDto != null) {
                    schemas.put(responseDto.getSimpleName(), buildDtoSchema(responseDto));
                } else {
                    schemas.put(simpleName + "ResponseDTO", buildSchemaFromEntityFields(entity, true));
                }
            } catch (Exception e) {
                // 回退：仅按实体字段生成 schema
                schemas.put(simpleName + "CreateDTO", buildSchemaFromEntityFields(entity, false));
                schemas.put(simpleName + "UpdateDTO", buildSchemaFromEntityFields(entity, true));
                schemas.put(simpleName + "ResponseDTO", buildSchemaFromEntityFields(entity, true));
            }
        }
        return schemas;
    }

    /** 当 DTO 类不可用时，根据实体字段元数据生成 schema，保证文档始终有请求/响应参数说明。 */
    private Map<String, Object> buildSchemaFromEntityFields(EntityMeta entity, boolean includeId) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (FieldMeta fm : entity.getFields()) {
            if (!includeId && fm.isPrimaryKey()) {
                continue;
            }
            properties.put(fm.getName(), fieldMetaTypeToSchema(fm.getType()));
        }
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> fieldMetaTypeToSchema(String simpleTypeName) {
        if (simpleTypeName == null) {
            return Map.of("type", "string");
        }
        return switch (simpleTypeName) {
            case "String" -> Map.of("type", "string");
            case "Integer", "int" -> Map.of("type", "integer", "format", "int32");
            case "Long", "long" -> Map.of("type", "integer", "format", "int64");
            case "Boolean", "boolean" -> Map.of("type", "boolean");
            case "Double", "double" -> Map.of("type", "number", "format", "double");
            case "Float", "float" -> Map.of("type", "number", "format", "float");
            default -> Map.of("type", "object");
        };
    }

    private Map<String, Object> buildDtoSchema(Class<?> dtoClass) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Field field : dtoClass.getDeclaredFields()) {
            properties.put(field.getName(), buildFieldSchema(field.getType()));
        }
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> buildFieldSchema(Class<?> fieldType) {
        Map<String, Object> fieldSchema = new LinkedHashMap<>();
        if (fieldType == String.class) {
            fieldSchema.put("type", "string");
        } else if (fieldType == Integer.class || fieldType == int.class) {
            fieldSchema.put("type", "integer");
            fieldSchema.put("format", "int32");
        } else if (fieldType == Long.class || fieldType == long.class) {
            fieldSchema.put("type", "integer");
            fieldSchema.put("format", "int64");
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            fieldSchema.put("type", "boolean");
        } else if (fieldType == Double.class || fieldType == double.class) {
            fieldSchema.put("type", "number");
            fieldSchema.put("format", "double");
        } else if (fieldType == Float.class || fieldType == float.class) {
            fieldSchema.put("type", "number");
            fieldSchema.put("format", "float");
        } else if (fieldType.isEnum()) {
            fieldSchema.put("type", "string");
            List<String> enumValues = new ArrayList<>();
            for (Object e : fieldType.getEnumConstants()) {
                enumValues.add(e.toString());
            }
            fieldSchema.put("enum", enumValues);
        } else {
            fieldSchema.put("type", "object");
        }
        return fieldSchema;
    }

    private static String tagForEntity(EntityMeta entity) {
        return entity.getDisplayName() != null && !entity.getDisplayName().isBlank()
                ? entity.getDisplayName() : entity.getPathSegment();
    }

    private static void putOperation(Map<String, Map<String, Object>> pathsMap, String path, String method,
            String summary, String operationId, boolean requestBody, Object permissions, String tag, EntityMeta entity,
            String requestSchemaRef, boolean requestSchemaArray, boolean requestSchemaArrayOfIds,
            String responseSchemaRef) {
        pathsMap.computeIfAbsent(path, k -> new HashMap<>());
        Map<String, Object> pathItem = pathsMap.get(path);
        Map<String, Object> op = new HashMap<>();
        op.put("tags", List.of(tag));
        op.put("summary", summary);
        op.put("operationId", operationId);
        if (path.contains("{id}") && entity != null) {
            Class<?> pkType = entity.getPrimaryKeyType() != null ? entity.getPrimaryKeyType() : Long.class;
            String idSchemaType = (pkType == Long.class || pkType == long.class || pkType == Integer.class || pkType == int.class) ? "integer" : "string";
            op.put("parameters", List.of(Map.of(
                    "name", "id",
                    "in", "path",
                    "required", true,
                    "schema", Map.of("type", idSchemaType),
                    "description", "主键，类型为 " + pkType.getSimpleName())));
        }
        Map<String, Object> responseContent = new LinkedHashMap<>();
        if (responseSchemaRef != null) {
            responseContent.put("description", DEFAULT_RESPONSE_DESC);
            responseContent.put("content", Map.of("application/json", 
                Map.of("schema", Map.of("$ref", "#/components/schemas/" + responseSchemaRef))));
        } else {
            responseContent.put("description", DEFAULT_RESPONSE_DESC);
        }
        op.put("responses", Map.of("200", responseContent));
        if (requestBody && requestSchemaRef != null) {
            Map<String, Object> schema = requestSchemaArray
                    ? Map.of(
                            "type", "array",
                            "items", Map.of("$ref", "#/components/schemas/" + requestSchemaRef))
                    : Map.of("$ref", "#/components/schemas/" + requestSchemaRef);
            op.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("application/json", 
                        Map.of("schema", schema))));
        } else if (requestBody && requestSchemaArrayOfIds && entity != null) {
            Class<?> pkType = entity.getPrimaryKeyType() != null ? entity.getPrimaryKeyType() : Long.class;
            String idSchemaType = (pkType == Long.class || pkType == long.class
                    || pkType == Integer.class || pkType == int.class) ? "integer" : "string";
            op.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("application/json", Map.of("schema", Map.of(
                            "type", "array",
                            "items", Map.of("type", idSchemaType),
                            "description", "主键 ID 列表")))));
        } else if (requestBody) {
            op.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("application/json", Map.of("schema", Map.of("type", "object")))));
        }
        if (permissions != null && !"".equals(permissions)) {
            op.put("x-permissions", permissions);
        }
        pathItem.put(method, op);
    }

    private String getRequestSchemaForMethod(String methodName, EntityMeta entity) {
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        if (simpleName == null) {
            return null;
        }
        return switch (methodName) {
            case "create" -> {
                Class<?> createDto = EntityDtoResolver.resolveCreateDto(entity);
                yield createDto != null ? createDto.getSimpleName() : (simpleName + "CreateDTO");
            }
            case "update", "updateBatch" -> {
                Class<?> updateDto = EntityDtoResolver.resolveUpdateDto(entity);
                yield updateDto != null ? updateDto.getSimpleName() : (simpleName + "UpdateDTO");
            }
            default -> null;
        };
    }

    private boolean isRequestSchemaArrayForMethod(String methodName) {
        return "updateBatch".equals(methodName);
    }

    private boolean isRequestSchemaArrayOfIdsForMethod(String methodName) {
        return "deleteBatch".equals(methodName);
    }

    private String getResponseSchemaForMethod(String methodName, EntityMeta entity) {
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        if (simpleName == null) {
            return null;
        }
        return switch (methodName) {
            case "list", "get", "create", "update" -> {
                Class<?> responseDto = EntityDtoResolver.resolveResponseDto(entity);
                yield responseDto != null ? responseDto.getSimpleName() : (simpleName + "ResponseDTO");
            }
            default -> null;
        };
    }

    private static boolean hasRequestBody(Method method) {
        for (Parameter p : method.getParameters()) {
            if (p.getAnnotation(RequestBody.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static String summaryFromMethodName(String methodName) {
        return switch (methodName) {
            case "list" -> "list";
            case "get" -> "get";
            case "create" -> "create";
            case "update" -> "update";
            case "delete" -> "delete";
            case "deleteBatch" -> "批量删除";
            case "updateBatch" -> "批量更新";
            case "export" -> "导出 Excel";
            default -> methodName;
        };
    }

    private static boolean isOperationEnabled(EntityMeta entity, String methodName) {
        return switch (methodName) {
            case "list" -> entity.isListEnabled();
            case "get" -> entity.isGetEnabled();
            case "create" -> entity.isCreateEnabled();
            case "update" -> entity.isUpdateEnabled();
            case "updateBatch" -> entity.isUpdateBatchEnabled();
            case "delete" -> entity.isDeleteEnabled();
            case "deleteBatch" -> entity.isDeleteBatchEnabled();
            case "export" -> entity.isExportEnabled();
            default -> false;
        };
    }

    private static Object permissionsForMethod(String methodName, EntityMeta entity) {
        return switch (methodName) {
            case "list", "get", "export" -> entity.getPermissionRead();
            case "create" -> entity.getPermissionCreate();
            case "update", "updateBatch" -> entity.getPermissionUpdate();
            case "delete", "deleteBatch" -> entity.getPermissionDelete();
            default -> "";
        };
    }
}
