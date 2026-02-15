package com.lrenyi.template.platform.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.platform.action.EntityActionExecutor;
import com.lrenyi.template.platform.config.EntityPlatformProperties;
import com.lrenyi.template.platform.meta.ActionMeta;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.registry.ActionRegistry;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.service.EntityCrudService;
import com.lrenyi.template.platform.support.EntityDtoResolver;
import com.lrenyi.template.platform.support.ExcelExportSupport;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 统一入口 Controller：CRUD + Action 路由。
 */
@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("${app.platform.api-prefix:/api}")
public class GenericEntityController {
    
    private final EntityRegistry entityRegistry;
    private final ActionRegistry actionRegistry;
    private final EntityCrudService crudService;
    private final EntityPlatformProperties properties;
    private final ObjectMapper objectMapper;
    
    public GenericEntityController(EntityRegistry entityRegistry,
            ActionRegistry actionRegistry,
            EntityCrudService crudService,
            EntityPlatformProperties properties,
            ObjectMapper objectMapper) {
        this.entityRegistry = entityRegistry;
        this.actionRegistry = actionRegistry;
        this.crudService = crudService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }
    
    @GetMapping("/{entity}")
    public Result<?> list(@PathVariable("entity") String entity,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return notFound();
        }
        Pageable pageable = PageRequest.of(page, size);
        List<?> list = crudService.list(meta, pageable);
        return Result.getSuccess(toResponseList(meta, list));
    }
    
    @GetMapping("/{entity}/{id}")
    public Result<?> get(@PathVariable("entity") String entity, @PathVariable("id") Long id) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return notFound();
        }
        Object one = crudService.get(meta, id);
        return one == null ? notFound() : Result.getSuccess(toResponse(meta, one));
    }
    
    @PostMapping("/{entity}")
    public Result<?> create(@PathVariable("entity") String entity, @RequestBody Map<String, Object> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return notFound();
        }
        Object bodyEntity = bodyToEntity(meta, body, false);
        Object created = crudService.create(meta, bodyEntity);
        return Result.getSuccess(toResponse(meta, created));
    }
    
    @PutMapping("/{entity}/{id}")
    public Result<?> update(@PathVariable("entity") String entity,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return notFound();
        }
        Object bodyEntity = bodyToEntity(meta, body, true);
        Object updated = crudService.update(meta, id, bodyEntity);
        return updated == null ? notFound() : Result.getSuccess(toResponse(meta, updated));
    }
    
    @DeleteMapping("/{entity}/{id}")
    public Result<?> delete(@PathVariable("entity") String entity, @PathVariable("id") Long id) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return notFound();
        }
        crudService.delete(meta, id);
        return Result.getSuccess(null);
    }
    
    /**
     * 批量删除。请求体为 ID 列表，例如 [1, 2, 3]。
     */
    @DeleteMapping("/{entity}/batch")
    public Result<?> deleteBatch(@PathVariable("entity") String entity, @RequestBody List<Long> ids) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return notFound();
        }
        if (ids == null || ids.isEmpty()) {
            return Result.getSuccess(null);
        }
        crudService.deleteBatch(meta, ids);
        return Result.getSuccess(null);
    }
    
    /**
     * 批量更新。请求体为对象列表，每项需包含 id 及要更新的字段，例如 [{"id":1,"name":"a"},{"id":2,"name":"b"}]。
     */
    @PutMapping("/{entity}/batch")
    public Result<?> updateBatch(@PathVariable("entity") String entity, @RequestBody List<Map<String, Object>> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return notFound();
        }
        if (body == null || body.isEmpty()) {
            return Result.getSuccess(List.of());
        }
        List<Object> entities = new java.util.ArrayList<>(body.size());
        for (Map<String, Object> map : body) {
            Object idObj = map.get("id");
            Long id = idObj instanceof Number n ? n.longValue() : null;
            if (id == null) {
                continue;
            }
            Object bodyEntity = bodyToEntity(meta, map, true);
            setEntityId(bodyEntity, id);
            entities.add(bodyEntity);
        }
        List<?> updated = crudService.updateBatch(meta, entities);
        return Result.getSuccess(toResponseList(meta, updated));
    }
    
    /**
     * 导出 Excel。仅导出未标注 @ExportExclude 的字段；支持分页参数。
     */
    @GetMapping("/{entity}/export")
    public ResponseEntity<byte[]> export(@PathVariable("entity") String entity,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10000") int size) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCrudEnabled()) {
            return ResponseEntity.notFound().build();
        }
        int safeSize = Math.min(Math.max(1, size), 50000);
        Pageable pageable = PageRequest.of(page, safeSize);
        List<?> list = crudService.list(meta, pageable);
        try {
            byte[] bytes = ExcelExportSupport.toExcel(meta, list, objectMapper);
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
    
    @PostMapping("/{entity}/{id}/{actionName}")
    public Result<?> executeAction(@PathVariable("entity") String entity,
            @PathVariable("id") Long id,
            @PathVariable("actionName") String actionName,
            @RequestBody(required = false) Map<String, Object> body) {
        EntityMeta entityMeta = entityRegistry.getByPathSegment(entity);
        if (entityMeta == null) {
            return notFound();
        }
        EntityActionExecutor executor = actionRegistry.getExecutor(entity, actionName);
        ActionMeta actionMeta = actionRegistry.getMeta(entity, actionName);
        if (executor == null || actionMeta == null) {
            return notFound();
        }
        Object requestObj = null;
        if (body != null && !body.isEmpty() && actionMeta.getRequestType() != null
                && actionMeta.getRequestType() != Void.class) {
            requestObj = objectMapper.convertValue(body, actionMeta.getRequestType());
        }
        Object result = executor.execute(id, requestObj);
        return Result.getSuccess(result != null ? result : Map.of());
    }
    
    private Object bodyToEntity(EntityMeta meta, Map<String, Object> body, boolean forUpdate) {
        Class<?> entityClass = meta.getEntityClass();
        if (entityClass == null) {
            return objectMapper.convertValue(body, Object.class);
        }
        Class<?> dtoClass =
                forUpdate ? EntityDtoResolver.resolveUpdateDto(meta) : EntityDtoResolver.resolveCreateDto(meta);
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
    
    private List<?> toResponseList(EntityMeta meta, List<?> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        Class<?> responseDtoClass = EntityDtoResolver.resolveResponseDto(meta);
        if (responseDtoClass != null) {
            return list.stream().map(e -> objectMapper.convertValue(e, responseDtoClass)).collect(Collectors.toList());
        }
        return list;
    }
    
    private static void setEntityId(Object entity, Long id) {
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
    
    private static Result<Object> notFound() {
        Result<Object> r = Result.getError(null, "未找到");
        r.setCode(404);
        return r;
    }
}
