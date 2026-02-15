package com.lrenyi.template.platform.controller;

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
 * 根据 GenericEntityController 的映射自动发现接口，结合 EntityMeta/ActionMeta 生成 OpenAPI 文档（JSON）。
 */
@RestController
@RequestMapping("${app.platform.api-prefix:/api}")
public class OpenApiController {

    private final EntityRegistry entityRegistry;
    private final RequestMappingHandlerMapping handlerMapping;

    public OpenApiController(EntityRegistry entityRegistry,
                            RequestMappingHandlerMapping handlerMapping) {
        this.entityRegistry = entityRegistry;
        this.handlerMapping = handlerMapping;
    }

    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> docs() {
        List<Map<String, Object>> paths = new ArrayList<>();
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
            String requestBody = hasRequestBody(hm.getMethod()) ? "body" : null;
            String methodName = hm.getMethod().getName();
            for (PathPattern pp : patterns) {
                String patternStr = pp.getPatternString();
                for (RequestMethod m : methods) {
                    String httpMethod = m.name();
                    if (patternStr.contains("{actionName}")) {
                        for (EntityMeta entity : entityRegistry.getAll()) {
                            for (ActionMeta action : entity.getActions()) {
                                String path = patternStr
                                        .replace("{entity}", entity.getPathSegment())
                                        .replace("{actionName}", action.getActionName());
                                paths.add(path(path, httpMethod,
                                        action.getRequestType() != null && action.getRequestType() != Void.class ? "body" : null,
                                        action.getSummary(),
                                        action.getPermissions().isEmpty() ? null : action.getPermissions()));
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
                            paths.add(path(path, httpMethod, requestBody, summary, permissions));
                        }
                    }
                }
            }
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("openapi", "3.0.0");
        doc.put("info", Map.of("title", "Entity Platform API", "version", "1.0"));
        doc.put("paths", paths);
        return ResponseEntity.ok(doc);
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

    private Map<String, Object> path(String path, String method, String requestBody, String summary, Object permissions) {
        Map<String, Object> p = new HashMap<>();
        p.put("path", path);
        p.put("method", method);
        p.put("summary", summary);
        if (requestBody != null) {
            p.put("requestBody", requestBody);
        }
        if (permissions != null && !"".equals(permissions)) {
            p.put("permissions", permissions);
        }
        return p;
    }
}
