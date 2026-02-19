package com.lrenyi.template.platform.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.lrenyi.template.platform.meta.ActionMeta;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.meta.FieldMeta;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.support.EntityDtoResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
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
 * 根据 GenericEntityController 的映射自动发现接口，结合 EntityMeta/ActionMeta 生成 OpenAPI 3.0
 * 文档（JSON），
 * 供 Swagger UI 等标准文档界面使用。
 */
@RestController
@RequestMapping("${app.platform.api-prefix:/api}")
public class OpenApiController {

    private static final String DEFAULT_RESPONSE_DESC = "成功时 data 为实体或列表，删除成功时 data 为 null；异常时由 Result 包装。";

    private final EntityRegistry entityRegistry;
    private final RequestMappingHandlerMapping handlerMapping;

    public OpenApiController(EntityRegistry entityRegistry, RequestMappingHandlerMapping handlerMapping) {
        this.entityRegistry = entityRegistry;
        this.handlerMapping = handlerMapping;
    }

    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> docs() {
        Map<String, Map<String, Object>> pathsMap = new HashMap<>();
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod hm = entry.getValue();
            Class<?> controllerClass = ClassUtils.getUserClass(hm.getBeanType());
            if (!GenericEntityController.class.isAssignableFrom(controllerClass)) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            var pathCondition = info.getPathPatternsCondition();
            Set<PathPattern> patterns = null;
            Set<String> patternStrings = null;
            if (pathCondition != null) {
                patterns = pathCondition.getPatterns();
            } else {
                var legacy = info.getPatternsCondition();
                if (legacy != null) {
                    legacy.getPatterns();
                    patternStrings = legacy.getPatterns();
                }
            }
            if ((patterns == null || patterns.isEmpty()) && (patternStrings == null || patternStrings.isEmpty())) {
                continue;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            boolean hasRequestBody = hasRequestBody(hm.getMethod());
            String methodName = hm.getMethod().getName();
            List<String> patternsToUse = new ArrayList<>();
            if (patterns != null) {
                for (PathPattern pp : patterns) {
                    patternsToUse.add(pp.getPatternString());
                }
            }
            if (patternStrings != null) {
                patternsToUse.addAll(patternStrings);
            }
            for (String patternStr : patternsToUse) {
                for (RequestMethod m : methods) {
                    String httpMethod = m.name().toLowerCase();
                    if (patternStr.contains("{actionName}")) {
                        for (EntityMeta entity : entityRegistry.getAll()) {
                            for (ActionMeta action : entity.getActions()) {
                                RequestMethod actionMethod = action.getMethod() != null ? action.getMethod()
                                        : RequestMethod.POST;
                                if (m != actionMethod) {
                                    continue;
                                }
                                // 判断当前 path pattern 是否包含 {id}
                                boolean hasIdParam = patternStr.contains("{id}");

                                // 核心逻辑:
                                // 如果 Action 本身声明需要 ID (requireId=true, 默认)：
                                // - 仅生成含 {id} 的路径文档
                                // 如果 Action 本身声明不需要 ID (requireId=false)：
                                // - 仅生成不含 {id} 的路径文档 (避免误导用户)

                                if (action.isRequireId()) {
                                    if (!hasIdParam) {
                                        continue; // 跳过无ID路径
                                    }
                                } else {
                                    if (hasIdParam) {
                                        continue; // 跳过有ID路径
                                    }
                                }

                                String path = patternStr
                                        .replace("{entity}", entity.getPathSegment())
                                        .replace("{actionName}", action.getActionName());

                                boolean needBody = action.getRequestType() != null
                                        && action.getRequestType() != Void.class;
                                String tag = tagForEntity(entity);

                                // OperationId 策略：
                                // 有 ID 的: entity_actionName
                                // 无 ID 的: entity_actionName_NoId (可选，或者依然叫 entity_actionName，因为二者互斥了)
                                // 考虑到兼容性与唯一性，若二者互斥，直接用 entity_actionName 也可以。
                                // 但为了明确语义，保持 entity_actionName_NoId 也可以。
                                // 鉴于之前用户已经看到 NoId 后缀，我们保持这个后缀以便区分，或者如果互斥了，去掉后缀更干净？
                                // 如果互斥，同一个 entity_actionName 只会出现一次。直接用 entity_actionName 最干净。

                                String opId = entity.getPathSegment() + "_" + action.getActionName();
                                if (!action.isRequireId()) {
                                    opId += "_NoId";
                                }

                                putOperation(pathsMap, path, httpMethod, action.getSummary(),
                                        opId,
                                        needBody, action.getPermissions().isEmpty() ? null : action.getPermissions(),
                                        tag, entity,
                                        null,
                                        false,
                                        false,
                                        null,
                                        null,
                                        null);
                            }
                        }
                    } else {
                        for (EntityMeta entity : entityRegistry.getAll()) {
                            if (!isOperationEnabled(entity, methodName)) {
                                continue;
                            }
                            // 额外检查：防止 methodName 不匹配导致 batch 操作泄露
                            if (patternStr.endsWith("/batch")) {
                                if ("delete".equals(httpMethod) && !entity.isDeleteBatchEnabled()) {
                                    continue;
                                }
                                if ("put".equals(httpMethod) && !entity.isUpdateBatchEnabled()) {
                                    continue;
                                }
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
                            
                            // 修正：确保 methodName 为 search 时，也像 GenericEntityController 一样生成 x-queryable-fields
                            // 原来的逻辑只有在 GenericEntityController 的 search 方法被直接映射时才生效
                            // 但这里我们是在遍历 entityRegistry 动态生成的 operation，所以需要手动注入 x-queryable-fields
                            Map<String, Object> queryableFields = null;
                            if (("search".equals(methodName) || "export".equals(methodName)) && entity != null) {
                                queryableFields = buildQueryableFields(entity);
                            }

                            putOperation(pathsMap, path, httpMethod, summary, operationId, hasRequestBody,
                                    permissions != null && !"".equals(permissions) ? permissions : null, tag, entity,
                                    requestSchema,
                                    requestSchemaArray,
                                    requestSchemaArrayOfIds,
                                    responseSchema,
                                    methodName,
                                    queryableFields); // 传入 queryableFields
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
        schemas.put("SearchRequest", buildSearchRequestSchema());
        Map<String, Object> securitySchemes = Map.of(
                "bearerAuth", Map.of(
                        "type", "http",
                        "scheme", "bearer",
                        "bearerFormat", "JWT"
                )
        );
        Map<String, Object> doc = new HashMap<>();
        doc.put("openapi", "3.0.0");
        doc.put("info", Map.of("title", "Entity Platform API", "version", "1.0"));
        doc.put("tags", tagsList);
        doc.put("paths", pathsMap);
        doc.put("components", Map.of("schemas", schemas, "securitySchemes", securitySchemes));
        doc.put("security", List.of(Map.of("bearerAuth", List.of())));
        return ResponseEntity.ok(doc);
    }

    private Map<String, Object> buildSearchRequestSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("filters", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "field", Map.of("type", "string", "description", "字段名"),
                                "op",
                                Map.of("type", "string", "description", "操作符: eq, ne, like, gt, gte, lt, lte, in"),
                                "value", Map.of("description", "值；in 时为数组")))));
        properties.put("sort", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "field", Map.of("type", "string", "description", "排序字段"),
                                "dir", Map.of("type", "string", "enum", List.of("asc", "desc"), "description",
                                        "asc 或 desc")))));
        properties.put("page", Map.of("type", "integer", "description", "页码，默认 0"));
        properties.put("size", Map.of("type", "integer", "description", "每页条数"));
        schema.put("properties", properties);
        schema.put("example",
                Map.of("filters",
                        List.of(Map.of("field", "username", "op", "like", "value", "john"),
                                Map.of("field", "status", "op", "in", "value", List.of(1, 2))),
                        "sort",
                        List.of(Map.of("field", "createTime", "dir", "desc")),
                        "page",
                        0,
                        "size",
                        20));
        return schema;
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
            schemas.put(simpleName + "PagedResult", buildPagedResultSchema(entity));
        }
        return schemas;
    }

    private Map<String, Object> buildPagedResultSchema(EntityMeta entity) {
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        String itemRef = simpleName != null ? (EntityDtoResolver.resolveResponseDto(entity) != null
                ? EntityDtoResolver.resolveResponseDto(entity).getSimpleName()
                : simpleName + "ResponseDTO") : "object";
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content", Map.of(
                "type", "array",
                "items", Map.of("$ref", "#/components/schemas/" + itemRef)));
        properties.put("totalElements", Map.of("type", "integer", "format", "int64"));
        properties.put("totalPages", Map.of("type", "integer"));
        properties.put("number", Map.of("type", "integer"));
        properties.put("size", Map.of("type", "integer"));
        schema.put("properties", properties);
        return schema;
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
                ? entity.getDisplayName()
                : entity.getPathSegment();
    }

    private static void putOperation(Map<String, Map<String, Object>> pathsMap,
            String path,
            String httpMethod,
            String summary, String operationId, boolean requestBody, Object permissions, String tag, EntityMeta entity,
            String requestSchemaRef, boolean requestSchemaArray, boolean requestSchemaArrayOfIds,
            String responseSchemaRef,
            String methodName,
            Map<String, Object> queryableFields) { // 新增参数
        pathsMap.computeIfAbsent(path, k -> new HashMap<>());
        Map<String, Object> pathItem = pathsMap.get(path);
        Map<String, Object> op = new HashMap<>();
        op.put("tags", List.of(tag));
        op.put("summary", summary);
        op.put("operationId", operationId);
        if (path.contains("{id}") && entity != null) {
            Class<?> pkType = entity.getPrimaryKeyType() != null ? entity.getPrimaryKeyType() : Long.class;
            String idSchemaType = (pkType == Long.class || pkType == long.class || pkType == Integer.class
                    || pkType == int.class) ? "integer" : "string";
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
            boolean required = !"SearchRequest".equals(requestSchemaRef);
            op.put("requestBody", Map.of(
                    "required", required,
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
        
        // 优先使用传入的 queryableFields，如果没有传入且符合条件，尝试内部构建（兼容旧逻辑）
        if (queryableFields != null && !queryableFields.isEmpty()) {
            op.put("x-queryable-fields", queryableFields);
        } else if (("search".equals(methodName) || "export".equals(methodName)) && entity != null) {
            Map<String, Object> builtFields = buildQueryableFields(entity);
            if (!builtFields.isEmpty()) {
                op.put("x-queryable-fields", builtFields);
            }
        }
        
        pathItem.put(httpMethod, op);
    }

    private static Map<String, Object> buildQueryableFields(EntityMeta entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity.getFields() == null) {
            return result;
        }
        for (FieldMeta fm : entity.getFields()) {
            if (fm == null || fm.getName() == null || fm.getName().isBlank()) {
                continue;
            }
            if (!fm.isQueryable()) {
                continue;
            }
            String type = fm.getType() != null ? fm.getType() : "String";
            List<String> operators = operatorsForType(type);
            Map<String, Object> fieldInfo = new LinkedHashMap<>();
            fieldInfo.put("type", type);
            fieldInfo.put("operators", operators);
            if (fm.getSearchLabel() != null) {
                fieldInfo.put("label", fm.getSearchLabel());
            }
            fieldInfo.put("order", fm.getSearchOrder());
            result.put(fm.getName(), fieldInfo);
        }
        // 按 order 排序
        return result.entrySet().stream()
                .sorted((e1, e2) -> {
                    Map<String, Object> m1 = (Map<String, Object>) e1.getValue();
                    Map<String, Object> m2 = (Map<String, Object>) e2.getValue();
                    int o1 = (int) m1.getOrDefault("order", 0);
                    int o2 = (int) m2.getOrDefault("order", 0);
                    return Integer.compare(o1, o2);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private static List<String> operatorsForType(String type) {
        if (type == null || type.isBlank()) {
            return List.of("eq", "ne");
        }
        return switch (type) {
            case "String" -> List.of("eq", "ne", "like");
            case "Integer", "int", "Long", "long" -> List.of("eq", "ne", "gt", "gte", "lt", "lte", "in");
            case "Boolean", "boolean" -> List.of("eq", "ne");
            case "LocalDate", "LocalDateTime", "Date", "Instant" -> List.of("eq", "gt", "gte", "lt", "lte");
            default -> List.of("eq", "ne", "in");
        };
    }

    private String getRequestSchemaForMethod(String methodName, EntityMeta entity) {
        if ("search".equals(methodName) || "export".equals(methodName)) {
            return "SearchRequest";
        }
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
        if ("export".equals(methodName)) {
            return null;
        }
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        if (simpleName == null) {
            return null;
        }
        return switch (methodName) {
            case "search" -> simpleName + "PagedResult";
            case "get", "create", "update" -> {
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
            case "search" -> "分页搜索";
            case "get" -> "查询详情";
            case "create" -> "创建";
            case "update" -> "更新";
            case "delete" -> "删除";
            case "deleteBatch" -> "批量删除";
            case "updateBatch" -> "批量更新";
            case "export" -> "导出 Excel";
            default -> methodName;
        };
    }

    private static boolean isOperationEnabled(EntityMeta entity, String methodName) {
        return switch (methodName) {
            case "search" -> entity.isListEnabled();
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
            case "search", "get", "export" -> entity.getPermissionRead();
            case "create" -> entity.getPermissionCreate();
            case "update", "updateBatch" -> entity.getPermissionUpdate();
            case "delete", "deleteBatch" -> entity.getPermissionDelete();
            default -> "";
        };
    }
}
