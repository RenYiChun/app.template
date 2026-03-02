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
import com.lrenyi.template.dataforge.support.EntityMapperProvider;
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
    private static final String JSON = "application/json";
    private static final String SEARCH_REQUEST = "SearchRequest";
    private static final String X_PERMISSIONS = "x-permissions";
    private static final String X_QUERYABLE_FIELDS = "x-queryable-fields";
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String VALUE = "value";
    private static final String FIELD = "field";
    private static final String OP = "op";
    private static final String DIR = "dir";
    private static final String NUMBER = "number";
    private static final String FORMAT = "format";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_SUMMARY = "summary";
    private static final String KEY_OPERATION_ID = "operationId";
    private static final String KEY_PARAMETERS = "parameters";
    private static final String KEY_RESPONSES = "responses";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_SCHEMA = "schema";
    private static final String KEY_REQUIRED = "required";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_ARRAY = "array";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_ENUM = "enum";
    private static final String KEY_IN = "in";
    private static final String IN_PATH = "path";
    private static final String KEY_NAME = "name";
    private static final String KEY_200 = "200";
    private static final String KEY_REQUEST_BODY = "requestBody";
    private static final String KEY_OPENAPI = "openapi";
    private static final String OPENAPI_VERSION = "3.0.0";
    private static final String KEY_INFO = "info";
    private static final String KEY_TITLE = "title";
    private static final String KEY_VERSION = "version";
    private static final String KEY_COMPONENTS = "components";
    private static final String KEY_SCHEMAS = "schemas";
    private static final String KEY_SECURITY_SCHEMES = "securitySchemes";
    private static final String KEY_SECURITY = "security";
    private static final String KEY_PAGE = "page";
    private static final String KEY_SIZE = "size";
    private static final String KEY_LABEL = "label";
    private static final String KEY_ORDER = "order";
    private static final String REF = "$ref";
    private static final String REF_PREFIX = "#/components/schemas/";
    private static final String KEY_ASC = "asc";
    private static final String KEY_DESC = "desc";
    private static final String KEY_ID = "id";
    private static final String OP_EQ = "eq";
    private static final String OP_NE = "ne";
    private static final String OP_LIKE = "like";
    private static final String OP_GT = "gt";
    private static final String OP_GTE = "gte";
    private static final String OP_LT = "lt";
    private static final String OP_LTE = "lte";
    private static final String OP_IN = "in";
    private static final String METHOD_SEARCH = "search";
    private static final String METHOD_GET = "get";
    private static final String METHOD_CREATE = "create";
    private static final String METHOD_UPDATE = "update";
    private static final String METHOD_DELETE = "delete";
    private static final String METHOD_DELETE_BATCH = "deleteBatch";
    private static final String METHOD_UPDATE_BATCH = "updateBatch";
    private static final String METHOD_EXPORT = "export";
    private static final String HTTP_DELETE = "delete";
    private static final String HTTP_PUT = "put";
    
    private final EntityRegistry entityRegistry;
    private final RequestMappingHandlerMapping handlerMapping;
    private final EntityMapperProvider mapperProvider;
    
    public OpenApiController(EntityRegistry entityRegistry,
            RequestMappingHandlerMapping handlerMapping,
            EntityMapperProvider mapperProvider) {
        this.entityRegistry = entityRegistry;
        this.handlerMapping = handlerMapping;
        this.mapperProvider = mapperProvider;
    }
    
    private static String tagForEntity(EntityMeta entity) {
        return entity.getDisplayName() != null && !entity.getDisplayName().isBlank() ? entity.getDisplayName() :
                entity.getPathSegment();
    }
    
    private static void putOperation(Map<String, Map<String, Object>> pathsMap, OperationSpec spec) {
        pathsMap.computeIfAbsent(spec.path, k -> new HashMap<>());
        Map<String, Object> pathItem = pathsMap.get(spec.path);
        Map<String, Object> op = new HashMap<>();
        op.put(KEY_TAGS, List.of(spec.tag));
        op.put(KEY_SUMMARY, spec.summary);
        op.put(KEY_OPERATION_ID, spec.operationId);
        addParameters(op, spec.path, spec.entity);
        addResponses(op, spec.responseSchemaRef);
        addRequestBody(op,
                       spec.requestBody,
                       spec.requestSchemaRef,
                       spec.requestSchemaArray,
                       spec.requestSchemaArrayOfIds,
                       spec.entity
        );
        addExtensions(op, spec.permissions, spec.queryableFields, spec.methodName, spec.entity);
        pathItem.put(spec.httpMethod, op);
    }
    
    private static void addParameters(Map<String, Object> op, String path, EntityMeta entity) {
        if (path.contains("{id}") && entity != null) {
            Class<?> pkType = entity.getPrimaryKeyType() != null ? entity.getPrimaryKeyType() : Long.class;
            String idSchemaType =
                    (pkType == Long.class || pkType == long.class || pkType == Integer.class || pkType == int.class) ?
                            TYPE_INTEGER : TYPE_STRING;
            op.put(KEY_PARAMETERS,
                   List.of(Map.of(KEY_NAME,
                                  KEY_ID,
                                  KEY_IN,
                                  IN_PATH,
                                  KEY_REQUIRED,
                                  true,
                                  KEY_SCHEMA,
                                  Map.of(KEY_TYPE, idSchemaType),
                                  KEY_DESCRIPTION,
                                  "主键，类型为 " + pkType.getSimpleName()
                   ))
            );
        }
    }
    
    private static void addResponses(Map<String, Object> op, String responseSchemaRef) {
        Map<String, Object> responseContent = new LinkedHashMap<>();
        if (responseSchemaRef != null) {
            responseContent.put(KEY_DESCRIPTION, DEFAULT_RESPONSE_DESC);
            responseContent.put(KEY_CONTENT,
                                Map.of(JSON, Map.of(KEY_SCHEMA, Map.of(REF, REF_PREFIX + responseSchemaRef)))
            );
        } else {
            responseContent.put(KEY_DESCRIPTION, DEFAULT_RESPONSE_DESC);
        }
        op.put(KEY_RESPONSES, Map.of(KEY_200, responseContent));
    }
    
    private static void addRequestBody(Map<String, Object> op,
            boolean requestBody,
            String requestSchemaRef,
            boolean requestSchemaArray,
            boolean requestSchemaArrayOfIds,
            EntityMeta entity) {
        if (requestBody && requestSchemaRef != null) {
            Map<String, Object> schema = requestSchemaArray ?
                    Map.of(KEY_TYPE, TYPE_ARRAY, KEY_ITEMS, Map.of(REF, REF_PREFIX + requestSchemaRef)) :
                    Map.of(REF, REF_PREFIX + requestSchemaRef);
            boolean required = !SEARCH_REQUEST.equals(requestSchemaRef);
            op.put(KEY_REQUEST_BODY,
                   Map.of(KEY_REQUIRED, required, KEY_CONTENT, Map.of(JSON, Map.of(KEY_SCHEMA, schema)))
            );
        } else if (requestBody && requestSchemaArrayOfIds && entity != null) {
            Class<?> pkType = entity.getPrimaryKeyType() != null ? entity.getPrimaryKeyType() : Long.class;
            String idSchemaType =
                    (pkType == Long.class || pkType == long.class || pkType == Integer.class || pkType == int.class) ?
                            TYPE_INTEGER : TYPE_STRING;
            op.put(KEY_REQUEST_BODY,
                   Map.of(KEY_REQUIRED,
                          true,
                          KEY_CONTENT,
                          Map.of(JSON,
                                 Map.of(KEY_SCHEMA,
                                        Map.of(KEY_TYPE,
                                               TYPE_ARRAY,
                                               KEY_ITEMS,
                                               Map.of(KEY_TYPE, idSchemaType),
                                               KEY_DESCRIPTION,
                                               "主键 ID 列表"
                                        )
                                 )
                          )
                   )
            );
        } else if (requestBody) {
            op.put(KEY_REQUEST_BODY,
                   Map.of(KEY_REQUIRED,
                          true,
                          KEY_CONTENT,
                          Map.of(JSON, Map.of(KEY_SCHEMA, Map.of(KEY_TYPE, TYPE_OBJECT)))
                   )
            );
        }
    }
    
    private static void addExtensions(Map<String, Object> op,
            Object permissions,
            Map<String, Object> queryableFields,
            String methodName,
            EntityMeta entity) {
        if (permissions != null && !"".equals(permissions)) {
            op.put(X_PERMISSIONS, permissions);
        }
        // 优先使用传入的 queryableFields，如果没有传入且符合条件，尝试内部构建（兼容旧逻辑）
        if (queryableFields != null && !queryableFields.isEmpty()) {
            op.put(X_QUERYABLE_FIELDS, queryableFields);
        } else if ((METHOD_SEARCH.equals(methodName) || METHOD_EXPORT.equals(methodName)) && entity != null) {
            Map<String, Object> builtFields = buildQueryableFields(entity);
            if (!builtFields.isEmpty()) {
                op.put(X_QUERYABLE_FIELDS, builtFields);
            }
        }
    }
    
    private static Map<String, Object> buildQueryableFields(EntityMeta entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity.getFields() == null) {
            return result;
        }
        for (FieldMeta fm : entity.getFields()) {
            if (fm == null || fm.getName() == null || fm.getName().isBlank() || !fm.isQueryable()) {
                continue;
            }
            String type = fm.getType() != null ? fm.getType() : "String";
            List<String> operators = operatorsForType(type);
            Map<String, Object> fieldInfo = new LinkedHashMap<>();
            fieldInfo.put(KEY_TYPE, type);
            fieldInfo.put("operators", operators);
            if (fm.getLabel() != null && !fm.getLabel().isBlank()) {
                fieldInfo.put(KEY_LABEL, fm.getLabel());
            }
            fieldInfo.put(KEY_ORDER, fm.getOrder());
            result.put(fm.getName(), fieldInfo);
        }
        // 按 order 排序
        return result.entrySet().stream().sorted((e1, e2) -> {
            Map<String, Object> m1 = (Map<String, Object>) e1.getValue();
            Map<String, Object> m2 = (Map<String, Object>) e2.getValue();
            int o1 = (int) m1.getOrDefault(KEY_ORDER, 0);
            int o2 = (int) m2.getOrDefault(KEY_ORDER, 0);
            return Integer.compare(o1, o2);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }
    
    private static List<String> operatorsForType(String type) {
        if (type == null || type.isBlank()) {
            return List.of(OP_EQ, OP_NE);
        }
        return switch (type) {
            case "String" -> List.of(OP_EQ, OP_NE, OP_LIKE);
            case "Integer", "int", "Long", "long" -> List.of(OP_EQ, OP_NE, OP_GT, OP_GTE, OP_LT, OP_LTE, OP_IN);
            case "Boolean", TYPE_BOOLEAN -> List.of(OP_EQ, OP_NE);
            case "LocalDate", "LocalDateTime", "Date", "Instant" -> List.of(OP_EQ, OP_GT, OP_GTE, OP_LT, OP_LTE);
            default -> List.of(OP_EQ, OP_NE, OP_IN);
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
            case METHOD_SEARCH -> "分页搜索";
            case METHOD_GET -> "查询详情";
            case METHOD_CREATE -> "创建";
            case METHOD_UPDATE -> "更新";
            case METHOD_DELETE -> "删除";
            case METHOD_DELETE_BATCH -> "删除";
            case METHOD_UPDATE_BATCH -> "更新";
            case METHOD_EXPORT -> "导出 Excel";
            default -> methodName;
        };
    }
    
    private static boolean isOperationEnabled(EntityMeta entity, String methodName) {
        return switch (methodName) {
            case METHOD_SEARCH -> entity.isListEnabled();
            case METHOD_GET -> entity.isGetEnabled();
            case METHOD_CREATE -> entity.isCreateEnabled();
            case METHOD_UPDATE -> entity.isUpdateEnabled();
            case METHOD_UPDATE_BATCH -> entity.isUpdateBatchEnabled();
            case METHOD_DELETE -> entity.isDeleteEnabled();
            case METHOD_DELETE_BATCH -> entity.isDeleteBatchEnabled();
            case METHOD_EXPORT -> entity.isExportEnabled();
            default -> false;
        };
    }
    
    private static Object permissionsForMethod(String methodName, EntityMeta entity) {
        return switch (methodName) {
            case METHOD_SEARCH, METHOD_GET, METHOD_EXPORT -> entity.getPermissionRead();
            case METHOD_CREATE -> entity.getPermissionCreate();
            case METHOD_UPDATE, METHOD_UPDATE_BATCH -> entity.getPermissionUpdate();
            case METHOD_DELETE, METHOD_DELETE_BATCH -> entity.getPermissionDelete();
            default -> "";
        };
    }
    
    private static boolean isGenericEntityController(HandlerMethod hm) {
        Class<?> controllerClass = ClassUtils.getUserClass(hm.getBeanType());
        return GenericEntityController.class.isAssignableFrom(controllerClass);
    }
    
    private static List<String> extractPatternStrings(RequestMappingInfo info) {
        var pathCondition = info.getPathPatternsCondition();
        if (pathCondition != null) {
            List<String> list = new ArrayList<>();
            for (PathPattern pp : pathCondition.getPatterns()) {
                list.add(pp.getPatternString());
            }
            return list;
        }
        var legacy = info.getPatternsCondition();
        if (legacy != null) {
            return new ArrayList<>(legacy.getPatterns());
        }
        return List.of();
    }
    
    private static boolean methodMatches(String httpMethod, ActionMeta action) {
        RequestMethod m = action.getMethod() != null ? action.getMethod() : RequestMethod.POST;
        return httpMethod.equalsIgnoreCase(m.name());
    }
    
    private static boolean idRequirementSatisfied(boolean requireId, boolean hasIdParam) {
        return requireId ? hasIdParam : !hasIdParam;
    }
    
    private static String buildActionPath(String patternStr, EntityMeta entity, ActionMeta action) {
        return patternStr.replace("{entity}", entity.getPathSegment()).replace("{actionName}", action.getActionName());
    }
    
    private static boolean hasRequestBodyForAction(ActionMeta action) {
        return action.getRequestType() != null && action.getRequestType() != Void.class;
    }
    
    private static String buildActionOperationId(EntityMeta entity, ActionMeta action) {
        String base = entity.getPathSegment() + "_" + action.getActionName();
        return action.isRequireId() ? base : base + "_NoId";
    }
    
    private static void addDtoSchema(Map<String, Object> schemas,
            EntityMeta entity,
            String simpleName,
            String suffix,
            Class<?> dtoClass) {
        if (dtoClass != null) {
            schemas.put(dtoClass.getSimpleName(), enrichDtoSchemaWithFieldLabels(buildDtoSchema(dtoClass), entity));
        } else {
            schemas.put(simpleName + suffix, emptySchema());
        }
    }
    
    private void addPageResponseDtoSchema(Map<String, Object> schemas, EntityMeta entity, String simpleName) {
        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(entity.getEntityClass());
        Class<?> pageResponseDto = info != null ? info.pageResponseDtoClass() : null;
        if (pageResponseDto != null) {
            schemas.put(pageResponseDto.getSimpleName(),
                        enrichDtoSchemaWithFieldLabels(buildDtoSchema(pageResponseDto), entity)
            );
        } else {
            log.warn("[OpenApi] PageResponseDTO class not found for entity {} (pathSegment={}), using empty schema. "
                             + "List columns will be empty. Fix: run 'mvn clean compile -pl "
                             + "template-dataforge-sample-backend' "
                             + "so the annotation processor generates the DTO in {}.dto package.",
                     simpleName,
                     entity.getPathSegment(),
                     simpleName
            );
            schemas.put(simpleName + "PageResponseDTO", emptySchema());
        }
    }
    
    /** DTO 类不存在时占位用，不按实体字段生成 schema，避免泄露或列过多；约定由编译期处理器生成 DTO。 */
    private static Map<String, Object> emptySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(KEY_TYPE, TYPE_OBJECT);
        schema.put(KEY_PROPERTIES, new LinkedHashMap<String, Object>());
        return schema;
    }
    
    private static Map<String, Object> buildDtoSchema(Class<?> dtoClass) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(KEY_TYPE, TYPE_OBJECT);
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Field field : dtoClass.getDeclaredFields()) {
            properties.put(field.getName(), buildFieldSchema(field.getType()));
        }
        schema.put(KEY_PROPERTIES, properties);
        return schema;
    }
    
    /** 用实体的 FieldMeta（@DataforgeField.label）为 schema 各属性补充 description，供前端表格列标题等展示 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> enrichDtoSchemaWithFieldLabels(Map<String, Object> schema, EntityMeta entity) {
        if (entity == null || entity.getFields() == null) {
            return schema;
        }
        Map<String, String> labels = buildLabelByField(entity);
        applyLabelsToSchemaProperties(schema, labels);
        return schema;
    }
    
    private static Map<String, String> buildLabelByField(EntityMeta entity) {
        Map<String, String> labelByField = new HashMap<>();
        for (FieldMeta fm : entity.getFields()) {
            if (fm == null) {
                continue;
            }
            String name = fm.getName();
            String label = fm.getLabel();
            if (name != null && !name.isBlank() && label != null && !label.isBlank()) {
                labelByField.put(name, label);
            }
        }
        return labelByField;
    }
    
    @SuppressWarnings("unchecked")
    private static void applyLabelsToSchemaProperties(Map<String, Object> schema, Map<String, String> labels) {
        Object propsObj = schema.get(KEY_PROPERTIES);
        if (!(propsObj instanceof Map)) {
            return;
        }
        Map<String, Object> properties = (Map<String, Object>) propsObj;
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            String label = labels.get(e.getKey());
            if (label != null && e.getValue() instanceof Map) {
                ((Map<String, Object>) e.getValue()).put(KEY_DESCRIPTION, label);
            }
        }
    }
    
    private static Map<String, Object> buildFieldSchema(Class<?> fieldType) {
        Map<String, Object> fieldSchema = new LinkedHashMap<>();
        if (fieldType == String.class) {
            fieldSchema.put(KEY_TYPE, TYPE_STRING);
        } else if (isIntegerType(fieldType)) {
            fieldSchema.put(KEY_TYPE, TYPE_INTEGER);
            fieldSchema.put(FORMAT, "int32");
        } else if (isLongType(fieldType)) {
            fieldSchema.put(KEY_TYPE, TYPE_INTEGER);
            fieldSchema.put(FORMAT, "int64");
        } else if (isBooleanType(fieldType)) {
            fieldSchema.put(KEY_TYPE, TYPE_BOOLEAN);
        } else if (isDoubleType(fieldType)) {
            fieldSchema.put(KEY_TYPE, NUMBER);
            fieldSchema.put(FORMAT, "double");
        } else if (isFloatType(fieldType)) {
            fieldSchema.put(KEY_TYPE, NUMBER);
            fieldSchema.put(FORMAT, "float");
        } else if (fieldType == java.math.BigDecimal.class) {
            fieldSchema.put(KEY_TYPE, NUMBER);
        } else if (isDateType(fieldType)) {
            fieldSchema.put(KEY_TYPE, TYPE_STRING);
            fieldSchema.put(FORMAT, fieldType == java.time.LocalDate.class ? "date" : "date-time");
        } else if (fieldType.isEnum()) {
            buildEnumSchema(fieldSchema, fieldType);
        } else {
            fieldSchema.put(KEY_TYPE, TYPE_OBJECT);
        }
        return fieldSchema;
    }
    
    private static boolean isIntegerType(Class<?> type) {
        return type == Integer.class || type == int.class;
    }
    
    private static boolean isLongType(Class<?> type) {
        return type == Long.class || type == long.class;
    }
    
    private static boolean isBooleanType(Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }
    
    private static boolean isDoubleType(Class<?> type) {
        return type == Double.class || type == double.class;
    }
    
    private static boolean isFloatType(Class<?> type) {
        return type == Float.class || type == float.class;
    }
    
    private static boolean isDateType(Class<?> type) {
        return type == java.time.LocalDate.class || type == java.time.LocalDateTime.class
                || type == java.util.Date.class || type == java.time.Instant.class;
    }
    
    private static void buildEnumSchema(Map<String, Object> schema, Class<?> type) {
        schema.put(KEY_TYPE, TYPE_STRING);
        List<String> enumValues = new ArrayList<>();
        for (Object e : type.getEnumConstants()) {
            enumValues.add(e.toString());
        }
        schema.put(KEY_ENUM, enumValues);
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
            tagDoc.put(KEY_DESCRIPTION, "pathSegment: " + entity.getPathSegment());
            tagsList.add(tagDoc);
        }
        Map<String, Object> schemas = buildSchemas();
        schemas.put(SEARCH_REQUEST, buildSearchRequestSchema());
        Map<String, Object> securitySchemes =
                Map.of(BEARER_AUTH, Map.of(KEY_TYPE, "http", "scheme", "bearer", "bearerFormat", "JWT"));
        Map<String, Object> doc = new HashMap<>();
        doc.put(KEY_OPENAPI, OPENAPI_VERSION);
        doc.put(KEY_INFO, Map.of(KEY_TITLE, "Entity Dataforge API", KEY_VERSION, "1.0"));
        doc.put(KEY_TAGS, tagsList);
        doc.put("paths", pathsMap);
        doc.put(KEY_COMPONENTS, Map.of(KEY_SCHEMAS, schemas, KEY_SECURITY_SCHEMES, securitySchemes));
        doc.put(KEY_SECURITY, List.of(Map.of(BEARER_AUTH, List.of())));
        return ResponseEntity.ok(doc);
    }
    
    private void processHandlerMethod(RequestMappingInfo info,
            HandlerMethod hm,
            Map<String, Map<String, Object>> pathsMap) {
        if (!isGenericEntityController(hm)) {
            return;
        }
        List<String> patternsToUse = extractPatternStrings(info);
        if (patternsToUse.isEmpty()) {
            return;
        }
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        boolean hasRequestBody = hasRequestBody(hm.getMethod());
        String methodName = hm.getMethod().getName();
        for (String patternStr : patternsToUse) {
            processMethodsForPattern(patternStr, methods, methodName, hasRequestBody, pathsMap);
        }
    }
    
    private void processMethodsForPattern(String patternStr,
            Set<RequestMethod> methods,
            String methodName,
            boolean hasRequestBody,
            Map<String, Map<String, Object>> pathsMap) {
        for (RequestMethod m : methods) {
            String httpMethod = m.name().toLowerCase();
            if (patternStr.contains("{actionName}")) {
                processActionPattern(patternStr, httpMethod, pathsMap);
            } else {
                processStandardPattern(patternStr, httpMethod, methodName, hasRequestBody, pathsMap);
            }
        }
    }
    
    private void processActionPattern(String patternStr, String httpMethod, Map<String, Map<String, Object>> pathsMap) {
        for (EntityMeta entity : entityRegistry.getAll()) {
            for (ActionMeta action : entity.getActions()) {
                boolean hasIdParam = patternStr.contains("{id}");
                boolean eligible =
                        methodMatches(httpMethod, action) && idRequirementSatisfied(action.isRequireId(), hasIdParam);
                if (!eligible) {
                    continue;
                }
                String path = buildActionPath(patternStr, entity, action);
                boolean needBody = hasRequestBodyForAction(action);
                String tag = tagForEntity(entity);
                String opId = buildActionOperationId(entity, action);
                Object perms = action.getPermissions().isEmpty() ? null : action.getPermissions();
                OperationSpec spec = OperationSpec.builder()
                                                  .path(path)
                                                  .httpMethod(httpMethod)
                                                  .summary(action.getSummary())
                                                  .operationId(opId)
                                                  .requestBody(needBody)
                                                  .permissions(perms)
                                                  .tag(tag)
                                                  .entity(entity)
                                                  .build();
                putOperation(pathsMap, spec);
            }
        }
    }
    
    private void processStandardPattern(String patternStr,
            String httpMethod,
            String methodName,
            boolean hasRequestBody,
            Map<String, Map<String, Object>> pathsMap) {
        for (EntityMeta entity : entityRegistry.getAll()) {
            boolean shouldSkip = !isOperationEnabled(entity, methodName) || (patternStr.endsWith("/batch") && (
                    (HTTP_DELETE.equals(httpMethod) && !entity.isDeleteBatchEnabled()) || (HTTP_PUT.equals(httpMethod)
                            && !entity.isUpdateBatchEnabled())));
            if (shouldSkip) {
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
            Map<String, Object> queryableFields = null;
            if ((METHOD_SEARCH.equals(methodName) || METHOD_EXPORT.equals(methodName)) && entity != null) {
                queryableFields = buildQueryableFields(entity);
            }
            OperationSpec spec = OperationSpec.builder()
                                              .path(path)
                                              .httpMethod(httpMethod)
                                              .summary(summary)
                                              .operationId(operationId)
                                              .requestBody(hasRequestBody)
                                              .permissions(
                                                      permissions != null && !"".equals(permissions) ? permissions :
                                                              null)
                                              .tag(tag)
                                              .entity(entity)
                                              .requestSchemaRef(requestSchema)
                                              .requestSchemaArray(requestSchemaArray)
                                              .requestSchemaArrayOfIds(requestSchemaArrayOfIds)
                                              .responseSchemaRef(responseSchema)
                                              .methodName(methodName)
                                              .queryableFields(queryableFields)
                                              .build();
            putOperation(pathsMap, spec);
        }
    }
    
    private Map<String, Object> buildSearchRequestSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(KEY_TYPE, TYPE_OBJECT);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("filters",
                       Map.of(KEY_TYPE,
                              TYPE_ARRAY,
                              KEY_ITEMS,
                              Map.of(KEY_TYPE,
                                     TYPE_OBJECT,
                                     KEY_PROPERTIES,
                                     Map.of(FIELD,
                                            Map.of(KEY_TYPE, TYPE_STRING, KEY_DESCRIPTION, "字段名"),
                                            OP,
                                            Map.of(KEY_TYPE,
                                                   TYPE_STRING,
                                                   KEY_DESCRIPTION,
                                                   "操作符: eq, ne, like, gt, gte, lt, lte, in"
                                            ),
                                            VALUE,
                                            Map.of(KEY_DESCRIPTION, "值；in 时为数组")
                                     )
                              )
                       )
        );
        properties.put("sort",
                       Map.of(KEY_TYPE,
                              TYPE_ARRAY,
                              KEY_ITEMS,
                              Map.of(KEY_TYPE,
                                     TYPE_OBJECT,
                                     KEY_PROPERTIES,
                                     Map.of(FIELD,
                                            Map.of(KEY_TYPE, TYPE_STRING, KEY_DESCRIPTION, "排序字段"),
                                            DIR,
                                            Map.of(KEY_TYPE,
                                                   TYPE_STRING,
                                                   KEY_ENUM,
                                                   List.of(KEY_ASC, KEY_DESC),
                                                   KEY_DESCRIPTION,
                                                   "asc 或 desc"
                                            )
                                     )
                              )
                       )
        );
        properties.put(KEY_PAGE, Map.of(KEY_TYPE, TYPE_INTEGER, KEY_DESCRIPTION, "页码，默认 0"));
        properties.put(KEY_SIZE, Map.of(KEY_TYPE, TYPE_INTEGER, KEY_DESCRIPTION, "每页条数"));
        schema.put(KEY_PROPERTIES, properties);
        schema.put("example",
                   Map.of("filters",
                          List.of(Map.of(FIELD, "username", OP, "like", VALUE, "john"),
                                  Map.of(FIELD, "status", OP, "in", VALUE, List.of(1, 2))
                          ),
                          "sort",
                          List.of(Map.of(FIELD, "createTime", DIR, "desc")),
                          KEY_PAGE,
                          0,
                          KEY_SIZE,
                          20
                   )
        );
        return schema;
    }
    
    private Map<String, Object> buildSchemas() {
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (EntityMeta entity : entityRegistry.getAll()) {
            String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
            if (simpleName == null) {
                continue;
            }
            
            EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(entity.getEntityClass());
            Class<?> createDto = info != null ? info.createDtoClass() : null;
            Class<?> updateDto = info != null ? info.updateDtoClass() : null;
            Class<?> responseDto = info != null ? info.responseDtoClass() : null;
            
            addDtoSchema(schemas, entity, simpleName, "CreateDTO", createDto);
            addDtoSchema(schemas, entity, simpleName, "UpdateDTO", updateDto);
            addDtoSchema(schemas, entity, simpleName, "ResponseDTO", responseDto);
            addPageResponseDtoSchema(schemas, entity, simpleName);
            schemas.put(simpleName + "PagedResult", buildPagedResultSchema(entity));
        }
        return schemas;
    }
    
    private Map<String, Object> buildPagedResultSchema(EntityMeta entity) {
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        // 分页列表项使用 PageResponseDTO（PAGE_RESPONSE），与单条详情的 ResponseDTO（RESPONSE）独立
        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(entity.getEntityClass());
        Class<?> pageResponseDto = info != null ? info.pageResponseDtoClass() : null;
        Class<?> responseDto = info != null ? info.responseDtoClass() : null;
        
        String itemRef;
        if (simpleName == null) {
            itemRef = TYPE_OBJECT;
        } else if (pageResponseDto != null) {
            itemRef = pageResponseDto.getSimpleName();
        } else if (responseDto != null) {
            itemRef = responseDto.getSimpleName();
        } else {
            itemRef = simpleName + "PageResponseDTO";
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(KEY_TYPE, TYPE_OBJECT);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(KEY_CONTENT, Map.of(KEY_TYPE, TYPE_ARRAY, KEY_ITEMS, Map.of(REF, REF_PREFIX + itemRef)));
        properties.put("totalElements", Map.of(KEY_TYPE, TYPE_INTEGER, FORMAT, "int64"));
        properties.put("totalPages", Map.of(KEY_TYPE, TYPE_INTEGER));
        properties.put(NUMBER, Map.of(KEY_TYPE, TYPE_INTEGER));
        properties.put("size", Map.of(KEY_TYPE, TYPE_INTEGER));
        schema.put(KEY_PROPERTIES, properties);
        return schema;
    }
    
    private String getRequestSchemaForMethod(String methodName, EntityMeta entity) {
        if (METHOD_SEARCH.equals(methodName) || METHOD_EXPORT.equals(methodName)) {
            return SEARCH_REQUEST;
        }
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        if (simpleName == null) {
            return null;
        }
        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(entity.getEntityClass());
        return switch (methodName) {
            case METHOD_CREATE -> {
                Class<?> createDto = info != null ? info.createDtoClass() : null;
                yield createDto != null ? createDto.getSimpleName() : (simpleName + "CreateDTO");
            }
            case METHOD_UPDATE, METHOD_UPDATE_BATCH -> {
                Class<?> updateDto = info != null ? info.updateDtoClass() : null;
                yield updateDto != null ? updateDto.getSimpleName() : (simpleName + "UpdateDTO");
            }
            default -> null;
        };
    }
    
    private boolean isRequestSchemaArrayForMethod(String methodName) {
        return METHOD_UPDATE_BATCH.equals(methodName);
    }
    
    private boolean isRequestSchemaArrayOfIdsForMethod(String methodName) {
        return METHOD_DELETE_BATCH.equals(methodName);
    }
    
    private String getResponseSchemaForMethod(String methodName, EntityMeta entity) {
        if (METHOD_EXPORT.equals(methodName)) {
            return null;
        }
        String simpleName = entity.getEntityClass() != null ? entity.getEntityClass().getSimpleName() : null;
        if (simpleName == null) {
            return null;
        }
        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(entity.getEntityClass());
        return switch (methodName) {
            case METHOD_SEARCH -> simpleName + "PagedResult";
            case METHOD_GET, METHOD_CREATE, METHOD_UPDATE -> {
                Class<?> responseDto = info != null ? info.responseDtoClass() : null;
                yield responseDto != null ? responseDto.getSimpleName() : (simpleName + "ResponseDTO");
            }
            default -> null;
        };
    }
    
    private static final class OperationSpec {
        private final String path;
        private final String httpMethod;
        private final String summary;
        private final String operationId;
        private final boolean requestBody;
        private final Object permissions;
        private final String tag;
        private final EntityMeta entity;
        private final String requestSchemaRef;
        private final boolean requestSchemaArray;
        private final boolean requestSchemaArrayOfIds;
        private final String responseSchemaRef;
        private final String methodName;
        private final Map<String, Object> queryableFields;
        
        private OperationSpec(Builder b) {
            this.path = b.path;
            this.httpMethod = b.httpMethod;
            this.summary = b.summary;
            this.operationId = b.operationId;
            this.requestBody = b.requestBody;
            this.permissions = b.permissions;
            this.tag = b.tag;
            this.entity = b.entity;
            this.requestSchemaRef = b.requestSchemaRef;
            this.requestSchemaArray = b.requestSchemaArray;
            this.requestSchemaArrayOfIds = b.requestSchemaArrayOfIds;
            this.responseSchemaRef = b.responseSchemaRef;
            this.methodName = b.methodName;
            this.queryableFields = b.queryableFields;
        }
        
        static Builder builder() {
            return new Builder();
        }
        
        static final class Builder {
            String path;
            String httpMethod;
            String summary;
            String operationId;
            boolean requestBody;
            Object permissions;
            String tag;
            EntityMeta entity;
            String requestSchemaRef;
            boolean requestSchemaArray;
            boolean requestSchemaArrayOfIds;
            String responseSchemaRef;
            String methodName;
            Map<String, Object> queryableFields;
            
            Builder path(String v) {
                path = v;
                return this;
            }
            
            Builder httpMethod(String v) {
                httpMethod = v;
                return this;
            }
            
            Builder summary(String v) {
                summary = v;
                return this;
            }
            
            Builder operationId(String v) {
                operationId = v;
                return this;
            }
            
            Builder requestBody(boolean v) {
                requestBody = v;
                return this;
            }
            
            Builder permissions(Object v) {
                permissions = v;
                return this;
            }
            
            Builder tag(String v) {
                tag = v;
                return this;
            }
            
            Builder entity(EntityMeta v) {
                entity = v;
                return this;
            }
            
            Builder requestSchemaRef(String v) {
                requestSchemaRef = v;
                return this;
            }
            
            Builder requestSchemaArray(boolean v) {
                requestSchemaArray = v;
                return this;
            }
            
            Builder requestSchemaArrayOfIds(boolean v) {
                requestSchemaArrayOfIds = v;
                return this;
            }
            
            Builder responseSchemaRef(String v) {
                responseSchemaRef = v;
                return this;
            }
            
            Builder methodName(String v) {
                methodName = v;
                return this;
            }
            
            Builder queryableFields(Map<String, Object> v) {
                queryableFields = v;
                return this;
            }
            
            OperationSpec build() {
                return new OperationSpec(this);
            }
        }
    }
}
