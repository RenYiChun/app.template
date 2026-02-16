package com.lrenyi.template.platform.controller;

import com.lrenyi.template.platform.config.EntityPlatformProperties;
import com.lrenyi.template.platform.meta.ActionMeta;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.registry.EntityRegistry;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
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
                                        needBody, action.getPermissions().isEmpty() ? null : action.getPermissions(), tag);
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
                            putOperation(pathsMap, path, httpMethod, summary, operationId, hasRequestBody,
                                    permissions != null && !"".equals(permissions) ? permissions : null, tag);
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
        Map<String, Object> doc = new HashMap<>();
        doc.put("openapi", "3.0.0");
        doc.put("info", Map.of("title", "Entity Platform API", "version", "1.0"));
        doc.put("tags", tagsList);
        doc.put("paths", pathsMap);
        return ResponseEntity.ok(doc);
    }

    private static String tagForEntity(EntityMeta entity) {
        return entity.getDisplayName() != null && !entity.getDisplayName().isBlank()
                ? entity.getDisplayName() : entity.getPathSegment();
    }

    private static void putOperation(Map<String, Map<String, Object>> pathsMap, String path, String method,
            String summary, String operationId, boolean requestBody, Object permissions, String tag) {
        pathsMap.computeIfAbsent(path, k -> new HashMap<>());
        Map<String, Object> pathItem = pathsMap.get(path);
        Map<String, Object> op = new HashMap<>();
        op.put("tags", List.of(tag));
        op.put("summary", summary);
        op.put("operationId", operationId);
        op.put("responses", Map.of("200", Map.of("description", DEFAULT_RESPONSE_DESC)));
        if (requestBody) {
            op.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("application/json", Map.of("schema", Map.of("type", "object")))));
        }
        if (permissions != null && !"".equals(permissions)) {
            op.put("x-permissions", permissions);
        }
        pathItem.put(method, op);
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
