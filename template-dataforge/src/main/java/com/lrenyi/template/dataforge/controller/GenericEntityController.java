package com.lrenyi.template.dataforge.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.meta.ActionMeta;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.permission.DataforgePermissionChecker;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.support.DataforgeServices;
import com.lrenyi.template.dataforge.support.EntityDtoResolver;
import com.lrenyi.template.dataforge.support.ExcelExportSupport;
import com.lrenyi.template.dataforge.support.ListCriteria;
import com.lrenyi.template.dataforge.support.PagedResult;
import com.lrenyi.template.dataforge.support.SearchRequest;
import com.lrenyi.template.dataforge.support.SortOrder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一入口 Controller：CRUD + Action 路由。
 */
@RestController
@RequestMapping("${app.dataforge.api-prefix:/api}")
public class GenericEntityController {

    private final EntityRegistry entityRegistry;
    private final ActionRegistry actionRegistry;
    private final EntityCrudService crudService;
    private final DataforgeProperties properties;
    private final DataforgePermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Validator> validatorProvider;
    private final ConversionService conversionService;

    public GenericEntityController(DataforgeServices services) {
        this.entityRegistry = services.entityRegistry();
        this.actionRegistry = services.actionRegistry();
        this.crudService = services.crudService();
        this.properties = services.properties();
        this.permissionChecker = services.permissionChecker();
        this.objectMapper = services.objectMapper();
        this.validatorProvider = services.validatorProvider();
        this.conversionService = services.conversionService();
    }

    /**
     * 搜索。POST 请求体为 SearchRequest（filters、sort、page、size），body 为空时使用默认值。
     */
    @PostMapping("/{entity}/search")
    public Result<?> search(@PathVariable("entity") String entity,
            @RequestBody(required = false) SearchRequest req) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isListEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, "read"));
        if (forbidden != null) {
            return forbidden;
        }
        SearchRequest request = req != null ? req : SearchRequest.empty();
        int safeSize = Math.min(Math.max(1, request.size()), properties.getMaxPageSize());
        Pageable pageable = buildPageable(request.page(), safeSize, request.sort());
        ListCriteria criteria = ListCriteria.from(request, meta);
        Page<?> pageResult = crudService.list(meta, pageable, criteria);
        List<?> converted = toResponseList(meta, pageResult.getContent());
        PagedResult<Object> pagedResult = PagedResult.from(pageResult, converted);
        return Result.getSuccess(pagedResult);
    }

    @GetMapping("/{entity}/{id}")
    public Result<?> get(@PathVariable("entity") String entity, @PathVariable("id") String id) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isGetEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, "read"));
        if (forbidden != null) {
            return forbidden;
        }
        Object idObj = parseIdOrBadRequest(meta, id);
        if (idObj == null) {
            return badRequest("id 格式错误");
        }
        Object one = crudService.get(meta, idObj);
        return one == null ? notFound() : Result.getSuccess(toResponse(meta, one));
    }

    @PostMapping("/{entity}")
    public Result<?> create(@PathVariable("entity") String entity, @RequestBody Map<String, Object> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCreateEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, "create"));
        if (forbidden != null) {
            return forbidden;
        }
        Result<Object> validationError = validateBody(meta, body, false);
        if (validationError != null) {
            return validationError;
        }
        Object bodyEntity = bodyToEntity(meta, body, false);
        Object created = crudService.create(meta, bodyEntity);
        return Result.getSuccess(toResponse(meta, created));
    }

    @PutMapping("/{entity}/{id}")
    public Result<?> update(@PathVariable("entity") String entity,
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isUpdateEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, "update"));
        if (forbidden != null) {
            return forbidden;
        }
        Object idObj = parseIdOrBadRequest(meta, id);
        if (idObj == null) {
            return badRequest("id 格式错误");
        }
        Result<Object> validationError = validateBody(meta, body, true);
        if (validationError != null) {
            return validationError;
        }
        Object bodyEntity = bodyToEntity(meta, body, true);
        Object updated = crudService.update(meta, idObj, bodyEntity);
        return updated == null ? notFound() : Result.getSuccess(toResponse(meta, updated));
    }

    @DeleteMapping("/{entity}/{id}")
    public Result<?> delete(@PathVariable("entity") String entity, @PathVariable("id") String id) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isDeleteEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, "delete"));
        if (forbidden != null) {
            return forbidden;
        }
        Object idObj = parseIdOrBadRequest(meta, id);
        if (idObj == null) {
            return badRequest("id 格式错误");
        }
        crudService.delete(meta, idObj);
        return Result.getSuccess(null);
    }

    /**
     * 删除。请求体为 ID 列表，例如 [1, 2, 3] 或 ["uuid1", "uuid2"]，按实体主键类型解析。
     */
    @DeleteMapping("/{entity}/batch")
    public Result<?> deleteBatch(@PathVariable("entity") String entity, @RequestBody List<Object> ids) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isDeleteBatchEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, "delete"));
        if (forbidden != null) {
            return forbidden;
        }
        if (ids == null || ids.isEmpty()) {
            return Result.getSuccess(null);
        }
        List<Object> parsedIds = parseIdsOrBadRequest(meta, ids);
        if (parsedIds == null) {
            return badRequest("id 列表格式错误");
        }
        crudService.deleteBatch(meta, parsedIds);
        return Result.getSuccess(null);
    }

    /**
     * 更新。请求体为对象列表，每项需包含 id 及要更新的字段，例如 [{"id":1,"name":"a"},{"id":2,"name":"b"}]。
     */
    @PutMapping("/{entity}/batch")
    public Result<?> updateBatch(@PathVariable("entity") String entity, @RequestBody List<Map<String, Object>> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isUpdateBatchEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, "update"));
        if (forbidden != null) {
            return forbidden;
        }
        if (body == null || body.isEmpty()) {
            return Result.getSuccess(List.of());
        }
        List<Object> entities = new java.util.ArrayList<>(body.size());
        Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
        Class<?> updateDtoClass = EntityDtoResolver.resolveUpdateDto(meta);
        Validator validator = validatorProvider.getIfAvailable();
        for (Map<String, Object> map : body) {
            Object idObj = map.get("id");
            if (idObj == null) {
                return badRequest("批量更新项缺少 id");
            }
            Object idParsed;
            try {
                idParsed = conversionService.convert(idObj, pkType);
            } catch (ConversionException | IllegalArgumentException e) {
                return badRequest("id 格式错误: " + e.getMessage());
            }
            if (properties.isValidationEnabled() && validator != null && updateDtoClass != null) {
                Object dto = objectMapper.convertValue(map, updateDtoClass);
                Result<Object> ve = validateAndReturnError(dto, validator);
                if (ve != null) {
                    return ve;
                }
            }
            Object bodyEntity = bodyToEntity(meta, map, true);
            setEntityId(bodyEntity, idParsed);
            entities.add(bodyEntity);
        }
        List<?> updated = crudService.updateBatch(meta, entities);
        return Result.getSuccess(toResponseList(meta, updated));
    }

    /**
     * 导出 Excel。请求体与 search 相同（filters、sort、page、size），仅 size
     * 默认更大；仅导出未标注 @DataforgeExport(enabled=false) 的字段。
     */
    @PostMapping("/{entity}/export")
    public ResponseEntity<byte[]> export(@PathVariable("entity") String entity,
            @RequestBody(required = false) SearchRequest req) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isExportEnabled()) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity<byte[]> forbiddenResp = requirePermissionForExport(getRequiredPermissionsForCrud(meta, "read"));
        if (forbiddenResp != null) {
            return forbiddenResp;
        }
        SearchRequest request = req != null ? req : SearchRequest.emptyForExport();
        int safeSize = Math.min(Math.max(1, request.size()), properties.getMaxExportSize());
        Pageable pageable = buildPageable(request.page(), safeSize, request.sort());
        ListCriteria criteria = ListCriteria.from(request, meta);
        Page<?> pageResult = crudService.list(meta, pageable, criteria);
        try {
            byte[] bytes = ExcelExportSupport.toExcel(meta, pageResult.getContent(), objectMapper);
            String filename = entity + "-export.xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } catch (Exception e) {
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    @RequestMapping(path = "/{entity}/{id}/_action/{actionName}", method = { RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE })
    public Result<?> executeAction(
            jakarta.servlet.http.HttpServletRequest request,
            @PathVariable("entity") String entity,
            @PathVariable("id") String id,
            @PathVariable("actionName") String actionName,
            @RequestBody(required = false) Map<String, Object> body) {
        return executeActionInternal(request, entity, id, actionName, body);
    }

    /**
     * 无 ID 的 Action，支持 GET/POST/PUT/DELETE。
     */
    @RequestMapping(path = "/{entity}/_action/{actionName}", method = { RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE })
    public Result<?> executeActionNoId(
            jakarta.servlet.http.HttpServletRequest request,
            @PathVariable("entity") String entity,
            @PathVariable("actionName") String actionName,
            @RequestBody(required = false) Map<String, Object> body) {
        // search, export, batch 等保留字已经由具体方法处理，此处处理其他自定义 Action
        return executeActionInternal(request, entity, null, actionName, body);
    }

    private Result<?> executeActionInternal(
            jakarta.servlet.http.HttpServletRequest request,
            String entity,
            String id,
            String actionName,
            Map<String, Object> body) {
        EntityMeta entityMeta = entityRegistry.getByPathSegment(entity);
        if (entityMeta == null) {
            return notFound();
        }
        Object idObj = null;
        if (id != null) {
            idObj = parseIdOrBadRequest(entityMeta, id);
            if (idObj == null) {
                return badRequest("id 格式错误");
            }
        }
        EntityActionExecutor executor = actionRegistry.getExecutor(entity, actionName);
        ActionMeta actionMeta = actionRegistry.getMeta(entity, actionName);
        if (executor == null || actionMeta == null) {
            return notFound();
        }
        RequestMethod allowed = actionMeta.getMethod() != null ? actionMeta.getMethod() : RequestMethod.POST;
        RequestMethod actual = RequestMethod.valueOf(request.getMethod());
        if (actual != allowed) {
            Result<Object> r = Result.getError(null, "请求方法不允许: 需要 " + allowed + ", 收到 " + actual);
            r.setCode(405);
            return r;
        }
        List<String> actionPerms = actionMeta.getPermissions() != null ? actionMeta.getPermissions()
                : Collections.emptyList();
        Result<Object> forbidden = requirePermission(actionPerms);
        if (forbidden != null) {
            return forbidden;
        }
        Object requestObj = null;
        if (body != null && !body.isEmpty() && actionMeta.getRequestType() != null
                && actionMeta.getRequestType() != Void.class) {
            requestObj = objectMapper.convertValue(body, actionMeta.getRequestType());
        }
        Object result = executor.execute(idObj, requestObj);
        return Result.getSuccess(result != null ? result : Map.of());
    }

    /**
     * 当 DTO 存在且启用 Bean Validation 时，校验请求体。校验失败返回 400 的 Result，通过返回 null。
     */
    private Result<Object> validateBody(EntityMeta meta, Map<String, Object> body, boolean forUpdate) {
        if (!properties.isValidationEnabled() || body == null) {
            return null;
        }
        Validator validator = validatorProvider.getIfAvailable();
        if (validator == null) {
            return null;
        }
        Class<?> dtoClass = forUpdate ? EntityDtoResolver.resolveUpdateDto(meta)
                : EntityDtoResolver.resolveCreateDto(meta);
        if (dtoClass == null) {
            return null;
        }
        try {
            Object dto = objectMapper.convertValue(body, dtoClass);
            return validateAndReturnError(dto, validator);
        } catch (Exception e) {
            return null;
        }
    }

    private static Result<Object> validateAndReturnError(Object dto, Validator validator) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        if (violations.isEmpty()) {
            return null;
        }
        String message = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    private Object bodyToEntity(EntityMeta meta, Map<String, Object> body, boolean forUpdate) {
        Class<?> entityClass = meta.getEntityClass();
        if (entityClass == null) {
            return objectMapper.convertValue(body, Object.class);
        }
        Class<?> dtoClass = forUpdate ? EntityDtoResolver.resolveUpdateDto(meta)
                : EntityDtoResolver.resolveCreateDto(meta);
        if (dtoClass != null) {
            Object dto = objectMapper.convertValue(body, dtoClass);
            return objectMapper.convertValue(dto, entityClass);
        }
        return objectMapper.convertValue(body, entityClass);
    }

    private Object toResponse(EntityMeta meta, Object entity) {
        if (entity == null) {
            return null;
        }
        Class<?> responseDtoClass = EntityDtoResolver.resolveResponseDto(meta);
        if (responseDtoClass != null) {
            return objectMapper.convertValue(entity, responseDtoClass);
        }
        return entity;
    }

    /** 分页列表项使用 PageResponseDTO，与单条详情的 ResponseDTO 独立。 */
    private List<?> toResponseList(EntityMeta meta, List<?> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        Class<?> dtoClass = EntityDtoResolver.resolvePageResponseDto(meta);
        if (dtoClass == null) {
            dtoClass = EntityDtoResolver.resolveResponseDto(meta);
        }
        if (dtoClass != null) {
            Class<?> finalDtoClass = dtoClass;
            return list.stream().map(e -> objectMapper.convertValue(e, finalDtoClass)).collect(Collectors.toList());
        }
        return list;
    }

    private static void setEntityId(Object entity, Object id) {
        if (entity == null || id == null) {
            return;
        }
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // ignore
        }
    }

    private Object parseIdOrBadRequest(EntityMeta meta, String id) {
        try {
            Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
            return conversionService.convert(id, pkType);
        } catch (ConversionException | IllegalArgumentException e) {
            return null;
        }
    }

    private List<Object> parseIdsOrBadRequest(EntityMeta meta, List<?> ids) {
        try {
            Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            List<Object> result = new ArrayList<>(ids.size());
            for (Object id : ids) {
                result.add(conversionService.convert(id, pkType));
            }
            return result;
        } catch (ConversionException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Result<Object> badRequest(String message) {
        Result<Object> r = Result.getError(null, message);
        r.setCode(400);
        return r;
    }

    private static Result<Object> notFound() {
        Result<Object> r = Result.getError(null, "未找到");
        r.setCode(404);
        return r;
    }

    /**
     * 权限校验：不通过时返回 403 的 Result，通过时返回 null。
     */
    private Result<Object> requirePermission(List<String> requiredPermissions) {
        if (!properties.isPermissionEnabled()) {
            return null;
        }
        boolean empty = requiredPermissions == null || requiredPermissions.isEmpty();
        if (empty) {
            if (!properties.isDefaultAllowIfNoPermission()) {
                return forbidden();
            }
            // Even when no specific permission is required, the user must be authenticated
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return forbidden();
            }
            return null;
        }
        return permissionChecker.hasAnyPermission(requiredPermissions) ? null : forbidden();
    }

    /**
     * 权限校验（用于返回 ResponseEntity 的 export）：不通过时返回 403 响应，通过时返回 null。
     */
    private ResponseEntity<byte[]> requirePermissionForExport(List<String> requiredPermissions) {
        if (requirePermission(requiredPermissions) == null) {
            return null;
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private static Result<Object> forbidden() {
        Result<Object> r = Result.getError(null, "无权限");
        r.setCode(403);
        return r;
    }

    private static List<String> getRequiredPermissionsForCrud(EntityMeta meta, String crudOp) {
        String p = switch (crudOp) {
            case "read" -> meta.getPermissionRead();
            case "create" -> meta.getPermissionCreate();
            case "update" -> meta.getPermissionUpdate();
            case "delete" -> meta.getPermissionDelete();
            default -> "";
        };
        if (p == null || p.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(p.trim());
    }

    private static Pageable buildPageable(int page, int size, List<SortOrder> sortOrders) {
        if (sortOrders == null || sortOrders.isEmpty()) {
            return PageRequest.of(page, size);
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (SortOrder so : sortOrders) {
            if (so != null && so.field() != null && !so.field().isBlank()) {
                orders.add("desc".equalsIgnoreCase(so.dir())
                        ? Sort.Order.desc(so.field())
                        : Sort.Order.asc(so.field()));
            }
        }
        return orders.isEmpty()
                ? PageRequest.of(page, size)
                : PageRequest.of(page, size, Sort.by(orders));
    }
}
