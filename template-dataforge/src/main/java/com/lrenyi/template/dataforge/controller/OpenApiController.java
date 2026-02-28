package com.lrenyi.template.dataforge.controller;

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
import com.lrenyi.template.dataforge.meta.ActionMeta;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.support.EntityDtoResolver;
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
@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("${app.dataforge.api-prefix:/api}")
public class OpenApiController {
    
    private static final String DEFAULT_RESPONSE_DESC =
            "成功时 data 为实体或列表，删除成功时 data 为 null；异常时由 Result 包装。";
    
    private final EntityRegistry entityRegistry;
    private final RequestMappingHandlerMapping handlerMapping;
    
    public OpenApiController(EntityRegistry entityRegistry, RequestMappingHandlerMapping handlerMapping) {
        this.entityRegistry = entityRegistry;
        this.handlerMapping = handlerMapping;
    }
    
    private static String tagForEntity(EntityMeta entity) {
        return entity.getDisplayName() != null && !entity.getDisplayName().isBlank() ? entity.getDisplayName() :
                entity.getPathSegment();
    }
    
    private static void putOperation(Map<String, Map<String, Object>> pathsMap,
                                     String path,
                                     String httpMethod,
                                     String summary,
                                     String operationId,
                                     boolean requestBody,
                                     Object permissions,
                                     String tag,
                                     EntityMeta entity,
                                     String requestSchemaRef,
                                     boolean requestSchemaArray,
                                     boolean requestSchemaArrayOfIds,
                                     String responseSchemaRef,
                                     String methodName,
                                     Map<String, Object> queryableFields) { // 新增参数
        pathsMap.computeIfAbsent(path, k -> new HashMap<>());
        Map<String, Object> pathItem = pathsMap.get(path);
        Map<String, Object> op = new HashMap<>();
        op.put("tags", List.of(tag));
        op.put("summary", summary);
        op.put("operationId", operationId);
        addParameters(op, path, entity);
        addResponses(op, responseSchemaRef);
        addRequestBody(op, requestBody, requestSchemaRef, requestSchemaArray, requestSchemaArrayOfIds,
                entity);
        addExtensions(op, permissions, queryableFields, methodName, entity);
        pathItem.put(httpMethod, op);
    }

    private static void addParameters(Map<String, Object> op, String path, EntityMeta entity) {
        if (path.contains("{id}") && entity != null) {
            Class<?> pkType =
                    entity.getPrimaryKeyType() != null ? entity.getPrimaryKeyType() : Long.class;
            String idSchemaType = (pkType == Long.class || pkType == long.class
                    || pkType == Integer.class || pkType == int.class) ? "integer" : "string";
            op.put("parameters",
                    List.of(Map.of("name",
                            "id",
                            "in",
                            "path",
                            "required",
                            true,
                            "schema",
                            Map.of("type", idSchemaType),
                            "description",
                            "主键，类型为 " + pkType.getSimpleName()
                    ))
            );
        }
    }

    private static void addResponses(Map<String, Object> op, String responseSchemaRef) {
        Map<String, Object> responseContent = new LinkedHashMap<>();
        if (responseSchemaRef != null) {
            responseContent.put("description", DEFAULT_RESPONSE_DESC);
            responseContent.put("content",
                    Map.of("application/json",
                            Map.of("schema", Map.of("$ref", "#/components/schemas/" + responseSchemaRef))
                    )
            );
        } else {
            responseContent.put("description", DEFAULT_RESPONSE_DESC);
        }
        op.put("responses", Map.of("200", responseContent));
    }

    private static void addRequestBody(Map<String, Object> op, boolean requestBody,
                                       String requestSchemaRef, boolean requestSchemaArray,
                                       boolean requestSchemaArrayOfIds, EntityMeta entity) {
        if (requestBody && requestSchemaRef != null) {
            Map<String, Object> schema = requestSchemaArray
                    ? Map.of("type", "array",
                            "items", Map.of("$ref", "#/components/schemas/" + requestSchemaRef))
                    : Map.of("$ref", "#/components/schemas/" + requestSchemaRef);
            boolean required = !"SearchRequest".equals(requestSchemaRef);
            op.put("requestBody",
                    Map.of("required", required, "content",
                            Map.of("application/json", Map.of("schema", schema)))
            );
        } else if (requestBody && requestSchemaArrayOfIds && entity != null) {
            Class<?> pkType =
                    entity.getPrimaryKeyType() != null ? entity.getPrimaryKeyType() : Long.class;
            String idSchemaType = (pkType == Long.class || pkType == long.class
                    || pkType == Integer.class || pkType == int.class) ? "integer" : "string";
            op.put("requestBody",
                    Map.of("required",
                            true,
                            "content",
                            Map.of("application/json",
                                    Map.of("schema",
                                            Map.of("type",
                                                    "array",
                                                    "items",
                                                    Map.of("type", idSchemaType),
                                                    "description",
                                                    "主键 ID 列表"
                                            )
                                    )
                            )
                    )
            );
        } else if (requestBody) {
            op.put("requestBody",
                    Map.of("required",
                            true,
                            "content",
                            Map.of("application/json", Map.of("schema", Map.of("type", "object")))
                    )
            );
        }
    }

    private static void addExtensions(Map<String, Object> op, Object permissions,
                                      Map<String, Object> queryableFields, String methodName,
                                      EntityMeta entity) {
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
            if (fm.getLabel() != null && !fm.getLabel().isBlank()) {
                fieldInfo.put("label", fm.getLabel());
            }
            fieldInfo.put("order", fm.getOrder());
            result.put(fm.getName(), fieldInfo);
        }
        // 按 order 排序
        return result.entrySet().stream().sorted((e1, e2) -> {
            Map<String, Object> m1 = (Map<String, Object>) e1.getValue();
            Map<String, Object> m2 = (Map<String, Object>) e2.getValue();
            int o1 = (int) m1.getOrDefault("order", 0);
            int o2 = (int) m2.getOrDefault("order", 0);
            return Integer.compare(o1, o2);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
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
            case "deleteBatch" -> "删除";
            case "updateBatch" -> "更新";
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
    
    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> docs() {
        Map<String, Map<String, Object>> pathsMap = new HashMap<>();
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            processHandlerMethod(entry.getKey(), entry.getValue(), pathsMap);
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
        Map<String, Object> securitySchemes =
                Map.of("bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT"));
        Map<String, Object> doc = new HashMap<>();
        doc.put("openapi", "3.0.0");
        doc.put("info", Map.of("title", "Entity Dataforge API", "version", "1.0"));
        doc.put("tags", tagsList);
        doc.put("paths", pathsMap);
        doc.put("components", Map.of("schemas", schemas, "securitySchemes", securitySchemes));
        doc.put("security", List.of(Map.of("bearerAuth", List.of())));
        return ResponseEntity.ok(doc);
    }

    private void processHandlerMethod(RequestMappingInfo info, HandlerMethod hm,
                                      Map<String, Map<String, Object>> pathsMap) {
        Class<?> controllerClass = ClassUtils.getUserClass(hm.getBeanType());
        if (!GenericEntityController.class.isAssignableFrom(controllerClass)) {
            return;
        }
        var pathCondition = info.getPathPatternsCondition();
        Set<PathPattern> patterns = null;
        Set<String> patternStrings = null;
        if (pathCondition != null) {
            patterns = pathCondition.getPatterns();
        } else {
            var legacy = info.getPatternsCondition();
            if (legacy != null) {
                patternStrings = legacy.getPatterns();
            }
        }
        if ((patterns == null || patterns.isEmpty())
                && (patternStrings == null || patternStrings.isEmpty())) {
            return;
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
                    processActionPattern(patternStr, httpMethod, pathsMap);
                } else {
                    processStandardPattern(patternStr, httpMethod, methodName, hasRequestBody,
                            pathsMap);
                }
            }
        }
    }

    private void processActionPattern(String patternStr, String httpMethod,
                                      Map<String, Map<String, Object>> pathsMap) {
        for (EntityMeta entity : entityRegistry.getAll()) {
            for (ActionMeta action : entity.getActions()) {
                RequestMethod actionMethod =
                        action.getMethod() != null ? action.getMethod() : RequestMethod.POST;
                if (!httpMethod.equalsIgnoreCase(actionMethod.name())) {
                    continue;
                }
                boolean hasIdParam = patternStr.contains("{id}");
                if (action.isRequireId()) {
                    if (!hasIdParam) {
                        continue;
                    }
                } else {
                    if (hasIdParam) {
                        continue;
                    }
                }
                String path = patternStr.replace("{entity}", entity.getPathSegment())
                        .replace("{actionName}", action.getActionName());
                boolean needBody =
                        action.getRequestType() != null && action.getRequestType() != Void.class;
                String tag = tagForEntity(entity);
                String opId = entity.getPathSegment() + "_" + action.getActionName();
                if (!action.isRequireId()) {
                    opId += "_NoId";
                }
                putOperation(pathsMap,
                        path,
                        httpMethod,
                        action.getSummary(),
                        opId,
                        needBody,
                        action.getPermissions().isEmpty() ? null : action.getPermissions(),
                        tag,
                        entity,
                        null,
                        false,
                        false,
                        null,
                        null,
                        null
                );
            }
        }
    }

    private void processStandardPattern(String patternStr, String httpMethod, String methodName,
                                        boolean hasRequestBody,
                                        Map<String, Map<String, Object>> pathsMap) {
        for (EntityMeta entity : entityRegistry.getAll()) {
            if (!isOperationEnabled(entity, methodName)) {
                continue;
            }
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
            Map<String, Object> queryableFields = null;
            if (("search".equals(methodName) || "export".equals(methodName)) && entity != null) {
                queryableFields = buildQueryableFields(entity);
            }
            putOperation(pathsMap,
                    path,
                    httpMethod,
                    summary,
                    operationId,
                    hasRequestBody,
                    permissions != null && !"".equals(permissions) ? permissions : null,
                    tag,
                    entity,
                    requestSchema,
                    requestSchemaArray,
                    requestSchemaArrayOfIds,
                    responseSchema,
                    methodName,
                    queryableFields
            );
        }
    }
    
    private Map<String, Object> buildSearchRequestSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("filters",
                       Map.of("type",
                              "array",
                              "items",
                              Map.of("type",
                                     "object",
                                     "properties",
                                     Map.of("field",
                                            Map.of("type", "string", "description", "字段名"),
                                            "op",
                                            Map.of("type",
                                                   "string",
                                                   "description",
                                                   "操作符: eq, ne, like, gt, gte, lt, lte, in"
                                            ),
                                            "value",
                                            Map.of("description", "值；in 时为数组")
                                     )
                              )
                       )
        );
        properties.put("sort",
                       Map.of("type",
                              "array",
                              "items",
                              Map.of("type",
                                     "object",
                                     "properties",
                                     Map.of("field",
                                            Map.of("type", "string", "description", "排序字段"),
                                            "dir",
                                            Map.of("type",
                                                   "string",
                                                   "enum",
                                                   List.of("asc", "desc"),
                                                   "description",
                                                   "asc 或 desc"
                                            )
                                     )
                              )
                       )
        );
        properties.put("page", Map.of("type", "integer", "description", "页码，默认 0"));
        properties.put("size", Map.of("type", "integer", "description", "每页条数"));
        schema.put("properties", properties);
        schema.put("example",
                   Map.of("filters",
                          List.of(Map.of("field", "username", "op", "like", "value", "john"),
                                  Map.of("field", "status", "op", "in", "value", List.of(1, 2))
                          ),
                          "sort",
                          List.of(Map.of("field", "createTime", "dir", "desc")),
                          "page",
                          0,
                          "size",
                          20
                   )
        );
        return schema;
    }
    
    private static Map<String, Object> buildSchemas() {
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (EntityMeta entity : entityRegistry.getAll()) {
            String simpleName =
                    entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
            if (simpleName == null) {
                continue;
            }
            addDtoSchema(schemas, entity, simpleName, "CreateDTO",
                    EntityDtoResolver.resolveCreateDto(entity));
            addDtoSchema(schemas, entity, simpleName, "UpdateDTO",
                    EntityDtoResolver.resolveUpdateDto(entity));
            addDtoSchema(schemas, entity, simpleName, "ResponseDTO",
                    EntityDtoResolver.resolveResponseDto(entity));
            addPageResponseDtoSchema(schemas, entity, simpleName);
            schemas.put(simpleName + "PagedResult", buildPagedResultSchema(entity));
        }
        return schemas;
    }

    private static void addDtoSchema(Map<String, Object> schemas, EntityMeta entity,
                                     String simpleName, String suffix, Class<?> dtoClass) {
        if (dtoClass != null) {
            schemas.put(dtoClass.getSimpleName(),
                    enrichDtoSchemaWithFieldLabels(buildDtoSchema(dtoClass), entity));
        } else {
            schemas.put(simpleName + suffix, emptySchema());
        }
    }

    private static void addPageResponseDtoSchema(Map<String, Object> schemas, EntityMeta entity,
                                                 String simpleName) {
        Class<?> pageResponseDto = EntityDtoResolver.resolvePageResponseDto(entity);
        if (pageResponseDto != null) {
            schemas.put(pageResponseDto.getSimpleName(),
                    enrichDtoSchemaWithFieldLabels(buildDtoSchema(pageResponseDto), entity));
        } else {
            log.warn(
                    "[OpenApi] PageResponseDTO class not found for entity {} (pathSegment={}), using empty schema. "
                            + "List columns will be empty. Fix: run 'mvn clean compile -pl "
                            + "template-dataforge-sample-backend' "
                            + "so the annotation processor generates the DTO in {}.dto package.",
                    simpleName, entity.getPathSegment(), simpleName);
            schemas.put(simpleName + "PageResponseDTO", emptySchema());
        }
    }
    
    private Map<String, Object> buildPagedResultSchema(EntityMeta entity) {
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        // 分页列表项使用 PageResponseDTO（PAGE_RESPONSE），与单条详情的 ResponseDTO（RESPONSE）独立
        Class<?> pageResponseDto = EntityDtoResolver.resolvePageResponseDto(entity);
        Class<?> responseDto = EntityDtoResolver.resolveResponseDto(entity);
        String itemRef = simpleName != null ? (pageResponseDto != null ? pageResponseDto.getSimpleName() :
                                               (responseDto != null ? responseDto.getSimpleName() :
                                                simpleName + "PageResponseDTO")) : "object";
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content", Map.of("type", "array", "items", Map.of("$ref", "#/components/schemas/" + itemRef)));
        properties.put("totalElements", Map.of("type", "integer", "format", "int64"));
        properties.put("totalPages", Map.of("type", "integer"));
        properties.put("number", Map.of("type", "integer"));
        properties.put("size", Map.of("type", "integer"));
        schema.put("properties", properties);
        return schema;
    }
    
    /** DTO 类不存在时占位用，不按实体字段生成 schema，避免泄露或列过多；约定由编译期处理器生成 DTO。 */
    private static Map<String, Object> emptySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        return schema;
    }

    private static Map<String, Object> buildDtoSchema(Class<?> dtoClass) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Field field : dtoClass.getDeclaredFields()) {
            properties.put(field.getName(), buildFieldSchema(field.getType()));
        }
        schema.put("properties", properties);
        return schema;
    }
    
    /** 用实体的 FieldMeta（@DataforgeField.label）为 schema 各属性补充 description，供前端表格列标题等展示 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> enrichDtoSchemaWithFieldLabels(Map<String, Object> schema,
                                                                      EntityMeta entity) {
        if (entity == null || entity.getFields() == null) {
            return schema;
        }
        Map<String, String> labelByField = new HashMap<>();
        for (FieldMeta fm : entity.getFields()) {
            if (fm.getName() != null && !fm.getName().isBlank()) {
                String label =
                        fm.getLabel() != null && !fm.getLabel().isBlank() ? fm.getLabel() : null;
                if (label != null) {
                    labelByField.put(fm.getName(), label);
                }
            }
        }
        Object propsObj = schema.get("properties");
        if (propsObj instanceof Map) {
            Map<String, Object> properties = (Map<String, Object>) propsObj;
            for (Map.Entry<String, Object> e : properties.entrySet()) {
                String label = labelByField.get(e.getKey());
                if (label != null && e.getValue() instanceof Map) {
                    ((Map<String, Object>) e.getValue()).put("description", label);
                }
            }
        }
        return schema;
    }
    
    private static Map<String, Object> buildFieldSchema(Class<?> fieldType) {
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
}
