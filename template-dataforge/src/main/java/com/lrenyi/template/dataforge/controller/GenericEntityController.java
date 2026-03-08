package com.lrenyi.template.dataforge.controller;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.core.util.Result;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.mapper.BaseMapper;
import com.lrenyi.template.dataforge.meta.ActionMeta;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.permission.DataPermissionApplicator;
import com.lrenyi.template.dataforge.permission.DataforgePermissionChecker;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.CascadeDeleteService;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.support.DataforgeErrorCodes;
import com.lrenyi.template.dataforge.support.DataforgeHttpException;
import com.lrenyi.template.dataforge.support.DataforgeServices;
import com.lrenyi.template.dataforge.support.AssociationChangeAuditor;
import com.lrenyi.template.dataforge.support.EntityChangeNotifier;
import com.lrenyi.template.dataforge.support.EntityMapperProvider;
import com.lrenyi.template.dataforge.support.EntityOption;
import com.lrenyi.template.dataforge.support.DisplayValueEnricher;
import com.lrenyi.template.dataforge.support.ExcelExportSupport;
import com.lrenyi.template.dataforge.support.FilterCondition;
import com.lrenyi.template.dataforge.support.ListCriteria;
import com.lrenyi.template.dataforge.support.Op;
import com.lrenyi.template.dataforge.support.PagedResult;
import com.lrenyi.template.dataforge.support.SearchRequest;
import com.lrenyi.template.dataforge.support.SortOrder;
import com.lrenyi.template.dataforge.support.TreeNode;
import com.lrenyi.template.dataforge.validation.AssociationValidator;
import com.lrenyi.template.dataforge.validation.Create;
import com.lrenyi.template.dataforge.validation.Update;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
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

    private static final String CRUD_READ = "read";
    private static final String CRUD_CREATE = "create";
    private static final String CRUD_UPDATE = "update";
    private static final String CRUD_DELETE = "delete";
    private static final String ERR_ID_FORMAT = "id 格式错误";
    private static final String ERR_INVALID_REQUEST_DATA = "无效的请求数据";
    private static final int OPTIONS_MAX_SIZE = 100;
    private static final int BATCH_LOOKUP_MAX_IDS = 1000;
    private final EntityRegistry entityRegistry;
    private final ActionRegistry actionRegistry;
    private final EntityCrudService crudService;
    private final DataforgeProperties properties;
    private final DataforgePermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Validator> validatorProvider;
    private final ConversionService conversionService;
    private final EntityMapperProvider mapperProvider;
    private final ObjectProvider<AssociationValidator> associationValidatorProvider;
    private final ObjectProvider<CascadeDeleteService> cascadeDeleteServiceProvider;
    private final ObjectProvider<DataPermissionApplicator> dataPermissionApplicatorProvider;
    private final ObjectProvider<EntityChangeNotifier> entityChangeNotifierProvider;
    private final ObjectProvider<AssociationChangeAuditor> associationChangeAuditorProvider;

    public GenericEntityController(DataforgeServices services) {
        this.entityRegistry = services.entityRegistry();
        this.actionRegistry = services.actionRegistry();
        this.crudService = services.crudService();
        this.properties = services.properties();
        this.permissionChecker = services.permissionChecker();
        this.objectMapper = services.objectMapper();
        this.validatorProvider = services.validatorProvider();
        this.conversionService = services.conversionService();
        this.mapperProvider = services.mapperProvider();
        this.associationValidatorProvider = services.associationValidatorProvider();
        this.cascadeDeleteServiceProvider = services.cascadeDeleteServiceProvider();
        this.dataPermissionApplicatorProvider = services.dataPermissionApplicatorProvider();
        this.entityChangeNotifierProvider = services.entityChangeNotifierProvider();
        this.associationChangeAuditorProvider = services.associationChangeAuditorProvider();
    }

    private void notifyCreated(EntityMeta meta, Object id) {
        EntityChangeNotifier notifier = entityChangeNotifierProvider.getIfAvailable();
        if (notifier != null) {
            notifier.notifyCreated(meta, id);
        }
    }

    private void notifyUpdated(EntityMeta meta, Object id) {
        EntityChangeNotifier notifier = entityChangeNotifierProvider.getIfAvailable();
        if (notifier != null) {
            notifier.notifyUpdated(meta, id);
        }
    }

    private void notifyDeleted(EntityMeta meta, Object id) {
        EntityChangeNotifier notifier = entityChangeNotifierProvider.getIfAvailable();
        if (notifier != null) {
            notifier.notifyDeleted(meta, id);
        }
    }

    /**
     * 合并数据权限过滤条件到已有 filters；当 meta.isEnableDataPermission() 且存在 DataPermissionApplicator 时追加。
     */
    private List<FilterCondition> mergeDataPermissionFilters(EntityMeta meta, List<FilterCondition> baseFilters) {
        List<FilterCondition> filters = new ArrayList<>(baseFilters);
        if (meta.isEnableDataPermission()) {
            DataPermissionApplicator applicator = dataPermissionApplicatorProvider.getIfAvailable();
            if (applicator != null) {
                List<FilterCondition> extra = applicator.getDataPermissionFilters(meta);
                if (extra != null && !extra.isEmpty()) {
                    filters.addAll(extra);
                }
            }
        }
        return filters;
    }

    /**
     * 获取实体的选项列表（按 entityName 解析，供关联下拉等使用）。
     * 支持 query 按显示字段模糊匹配，size 上限 100。
     */
    @GetMapping("/{entity}/options")
    public Result<Object> getOptions(
            @PathVariable String entity,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String sort) {
        EntityMeta meta = entityRegistry.getByEntityName(entity);
        if (meta == null || !meta.isListEnabled()) {
            throw new DataforgeHttpException(
                    org.springframework.http.HttpStatus.NOT_FOUND.value(),
                    DataforgeErrorCodes.ENTITY_NOT_FOUND,
                    "实体不存在或未开启列表: " + entity);
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_READ));
        if (forbidden != null) {
            return forbidden;
        }
        String displayField = (meta.getTreeNameField() != null && !meta.getTreeNameField().isBlank())
                ? meta.getTreeNameField() : "name";
        int pageNum = page != null ? Math.max(0, page) : 0;
        int sizeVal = size != null ? Math.clamp(size, 1, OPTIONS_MAX_SIZE) : 20;
        List<FilterCondition> filters = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            filters.add(new FilterCondition(displayField, Op.LIKE, "%" + query.trim() + "%"));
        }
        filters = mergeDataPermissionFilters(meta, filters);
        List<SortOrder> sortOrders = parseSortParam(sort, displayField);
        ListCriteria criteria = ListCriteria.of(filters, sortOrders);
        Pageable pageable = buildPageable(pageNum, sizeVal, sortOrders);
        Page<?> pageResult = crudService.list(meta, pageable, criteria);
        List<EntityOption> options = toEntityOptions(meta, pageResult.getContent(), displayField);
        PagedResult<EntityOption> pagedResult = PagedResult.from(pageResult, options);
        return Result.getSuccess(pagedResult);
    }

    private static List<SortOrder> parseSortParam(String sort, String defaultField) {
        if (sort == null || sort.isBlank()) {
            return List.of(new SortOrder(defaultField, "asc"));
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        String dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "desc" : "asc";
        return List.of(new SortOrder(field.isBlank() ? defaultField : field, dir));
    }

    private List<EntityOption> toEntityOptions(EntityMeta meta, List<?> content, String displayField) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        var accessor = meta.getAccessor();
        if (accessor == null) {
            return List.of();
        }
        List<EntityOption> list = new ArrayList<>(content.size());
        for (Object entity : content) {
            Object id = accessor.get(entity, "id");
            Object labelVal = accessor.get(entity, displayField);
            String label = labelVal != null ? labelVal.toString() : "";
            list.add(new EntityOption(id, label));
        }
        return list;
    }

    /**
     * 获取树形数据（按 entityName 解析，仅树形实体可用）。
     */
    @GetMapping("/{entity}/tree")
    public Result<Object> getTree(
            @PathVariable String entity,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String parentId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer maxDepth,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean includeDisabled) {
        EntityMeta meta = entityRegistry.getByEntityName(entity);
        if (meta == null || !meta.isListEnabled()) {
            throw new DataforgeHttpException(HttpStatus.NOT_FOUND.value(), DataforgeErrorCodes.ENTITY_NOT_FOUND,
                    "实体不存在或未开启列表: " + entity);
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_READ));
        if (forbidden != null) {
            return forbidden;
        }
        if (!meta.isTreeEntity()) {
            throw new DataforgeHttpException(HttpStatus.BAD_REQUEST.value(), DataforgeErrorCodes.ENTITY_NOT_FOUND,
                    "该实体不是树形结构: " + entity);
        }
        int depth = maxDepth != null && maxDepth > 0 ? maxDepth : meta.getTreeMaxDepth() > 0 ? meta.getTreeMaxDepth() : 10;
        boolean include = Boolean.TRUE.equals(includeDisabled);
        List<FilterCondition> filters = new ArrayList<>();
        boolean hasStatusField = meta.getFields() != null && meta.getFields().stream().anyMatch(f -> "status".equals(f.getName()));
        if (!include && hasStatusField) {
            filters.add(new FilterCondition("status", Op.EQ, "1"));
        }
        filters = mergeDataPermissionFilters(meta, filters);
        ListCriteria criteria = ListCriteria.of(filters, List.of());
        Page<?> pageResult = crudService.list(meta, Pageable.unpaged(), criteria);
        List<?> allNodes = pageResult.getContent();
        Object parentIdObj = parseParentId(meta, parentId);
        List<TreeNode> tree = buildTree(allNodes, meta, parentIdObj, depth);
        return Result.getSuccess(tree);
    }

    private Object parseParentId(EntityMeta meta, String parentId) {
        if (parentId == null || parentId.isBlank() || "null".equalsIgnoreCase(parentId)) {
            return null;
        }
        try {
            Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
            return conversionService.convert(parentId.trim(), pkType);
        } catch (ConversionException | IllegalArgumentException e) {
            return null;
        }
    }

    private List<TreeNode> buildTree(List<?> allNodes, EntityMeta meta, Object parentId, int maxDepth) {
        if (allNodes == null || maxDepth <= 0) {
            return List.of();
        }
        var accessor = meta.getAccessor();
        if (accessor == null) {
            return List.of();
        }
        String parentField = meta.getTreeParentField() != null && !meta.getTreeParentField().isBlank()
                ? meta.getTreeParentField() : "parentId";
        String nameField = meta.getTreeNameField() != null && !meta.getTreeNameField().isBlank()
                ? meta.getTreeNameField() : "name";
        return allNodes.stream()
                .filter(node -> Objects.equals(accessor.get(node, parentField), parentId))
                .map(node -> {
                    Object id = accessor.get(node, "id");
                    Object nodeParentId = accessor.get(node, parentField);
                    Object nameVal = accessor.get(node, nameField);
                    String label = nameVal != null ? nameVal.toString() : "";
                    List<TreeNode> children = buildTree(allNodes, meta, id, maxDepth - 1);
                    return new TreeNode(id, label, nodeParentId, children, null, children.isEmpty());
                })
                .toList();
    }

    /**
     * 批量查显示值（按 entityName 解析），ids 逗号分隔，单次上限 1000。
     * @param fields 可选，逗号分隔的字段名，返回 Map 中仅包含这些键；未指定时默认返回 id、label
     */
    @GetMapping("/{entity}/batch-lookup")
    public Result<Object> batchLookup(
            @PathVariable String entity,
            @org.springframework.web.bind.annotation.RequestParam String ids,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String fields) {
        EntityMeta meta = entityRegistry.getByEntityName(entity);
        if (meta == null || !meta.isListEnabled()) {
            throw new DataforgeHttpException(HttpStatus.NOT_FOUND.value(), DataforgeErrorCodes.ENTITY_NOT_FOUND,
                    "实体不存在或未开启列表: " + entity);
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_READ));
        if (forbidden != null) {
            return forbidden;
        }
        List<Object> idList = parseIds(ids, meta);
        if (idList.size() > BATCH_LOOKUP_MAX_IDS) {
            throw new DataforgeHttpException(HttpStatus.BAD_REQUEST.value(),
                    DataforgeErrorCodes.BATCH_LOOKUP_IDS_OVERFLOW,
                    "单次查询最多支持 " + BATCH_LOOKUP_MAX_IDS + " 个 ID");
        }
        if (idList.isEmpty()) {
            return Result.getSuccess(Map.of());
        }
        List<FilterCondition> filters = new ArrayList<>(List.of(new FilterCondition("id", Op.IN, idList)));
        filters = mergeDataPermissionFilters(meta, filters);
        ListCriteria criteria = ListCriteria.of(filters, List.of());
        Page<?> pageResult = crudService.list(meta, Pageable.unpaged(), criteria);
        List<?> content = pageResult.getContent();
        String displayField = meta.getTreeNameField() != null && !meta.getTreeNameField().isBlank()
                ? meta.getTreeNameField() : "name";
        List<String> fieldList = parseFieldsParam(fields);
        var accessor = meta.getAccessor();
        Map<Object, Map<String, Object>> result = new LinkedHashMap<>();
        if (accessor != null) {
            for (Object obj : content) {
                Object id = accessor.get(obj, "id");
                Map<String, Object> item = new LinkedHashMap<>();
                for (String f : fieldList) {
                    Object val = accessor.get(obj, f);
                    item.put(f, val);
                }
                Object labelVal = accessor.get(obj, displayField);
                item.put("label", labelVal != null ? labelVal.toString() : "");
                result.put(id, item);
            }
        }
        return Result.getSuccess(result);
    }

    /** 解析 batch-lookup 的 fields 参数：空则返回 [id, label]，否则返回去重后的字段列表（始终含 id 和 label）。 */
    private List<String> parseFieldsParam(String fields) {
        if (fields == null || fields.isBlank()) {
            return List.of("id", "label");
        }
        List<String> list = new ArrayList<>();
        list.add("id");
        for (String s : fields.split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !"id".equals(t) && !"label".equals(t) && !list.contains(t)) {
                list.add(t);
            }
        }
        list.add("label");
        return list;
    }

    private List<Object> parseIds(String ids, EntityMeta meta) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
        List<Object> list = new ArrayList<>();
        for (String s : ids.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                list.add(conversionService.convert(trimmed, pkType));
            } catch (ConversionException | IllegalArgumentException ignored) {
                // skip invalid id
            }
        }
        return list;
    }

    /**
     * 搜索。POST 请求体为 SearchRequest（filters、sort、page、size），body 为空时使用默认值。
     */
    @PostMapping("/{entity}/search")
    public Result<Object> search(@PathVariable String entity, @RequestBody(required = false) SearchRequest req) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isListEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_READ));
        if (forbidden != null) {
            return forbidden;
        }
        SearchRequest request = req != null ? req : SearchRequest.empty();
        int safeSize = Math.clamp(request.size(), 1, properties.getMaxPageSize());
        Pageable pageable = buildPageable(request.page(), safeSize, request.sort());
        ListCriteria criteria = ListCriteria.from(request, meta);
        List<FilterCondition> mergedFilters = mergeDataPermissionFilters(meta, criteria.getFilters());
        criteria = ListCriteria.of(mergedFilters, criteria.getSortOrders());
        Page<?> pageResult = crudService.list(meta, pageable, criteria);
        List<?> converted = toResponseList(meta, pageResult.getContent());
        PagedResult<Object> pagedResult = PagedResult.from(pageResult, converted);
        return Result.getSuccess(pagedResult);
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

    private static List<String> getRequiredPermissionsForCrud(EntityMeta meta, String crudOp) {
        String p = switch (crudOp) {
            case CRUD_READ -> meta.getPermissionRead();
            case CRUD_CREATE -> meta.getPermissionCreate();
            case CRUD_UPDATE -> meta.getPermissionUpdate();
            case CRUD_DELETE -> meta.getPermissionDelete();
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
        List<Sort.Order> orders = sortOrders.stream()
                .filter(so -> so != null && so.field() != null && !so.field().isBlank())
                .map(so -> "desc".equalsIgnoreCase(so.dir()) ? Sort.Order.desc(so.field()) :
                                   Sort.Order.asc(so.field()))
                .toList();
        return orders.isEmpty() ? PageRequest.of(page, size) : PageRequest.of(page, size, Sort.by(orders));
    }

    /** 分页列表项使用 PageResponseDTO，并填充关联字段 _display。Mongo $lookup 可能已返回 Map 带 _display。 */
    @SuppressWarnings("unchecked")
    private List<?> toResponseList(EntityMeta meta, List<?> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        List<?> converted;
        Object first = list.getFirst();
        if (first instanceof Map) {
            converted = list.stream().map(o -> new java.util.LinkedHashMap<>((Map<String, Object>) o)).toList();
        } else {
            EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(meta.getEntityClass());
            if (info != null && info.mapper() instanceof BaseMapper<?, ?, ?, ?, ?> rawMapper) {
                BaseMapper<Object, ?, ?, ?, Object> mapper = (BaseMapper<Object, ?, ?, ?, Object>) rawMapper;
                converted = list.stream().map(mapper::toPageResponse).toList();
            } else {
                converted = list;
            }
        }
        boolean hasForeignKey = meta.getFields() != null && meta.getFields().stream().anyMatch(FieldMeta::isForeignKey);
        if (hasForeignKey) {
            return DisplayValueEnricher.enrich(meta, converted, entityRegistry, crudService, objectMapper);
        }
        return converted;
    }

    private static Result<Object> forbidden() {
        Result<Object> r = Result.getError(null, "无权限");
        r.setCode(403);
        return r;
    }

    @GetMapping("/{entity}/{id}")
    public Result<Object> get(@PathVariable String entity, @PathVariable String id) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isGetEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_READ));
        if (forbidden != null) {
            return forbidden;
        }
        Object idObj = parseIdOrBadRequest(meta, id);
        if (idObj == null) {
            return badRequest(ERR_ID_FORMAT);
        }
        Object one = crudService.get(meta, idObj);
        return one == null ? notFound() : Result.getSuccess(toResponse(meta, one));
    }

    private Object parseIdOrBadRequest(EntityMeta meta, String id) {
        try {
            Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
            return conversionService.convert(id, pkType);
        } catch (ConversionException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Result<Object> badRequest(String message) {
        Result<Object> r = Result.getError(null, message);
        r.setCode(400);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object toResponse(EntityMeta meta, Object entity) {
        if (entity == null) {
            return null;
        }
        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(meta.getEntityClass());
        if (info != null && info.mapper() instanceof BaseMapper<?, ?, ?, ?, ?> rawMapper) {
            return ((BaseMapper<Object, ?, ?, Object, ?>) rawMapper).toResponse(entity);
        }
        return entity;
    }

    @PostMapping("/{entity}")
    @SuppressWarnings("unchecked")
    public Result<Object> create(@PathVariable String entity, @RequestBody Map<String, Object> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isCreateEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_CREATE));
        if (forbidden != null) {
            return forbidden;
        }

        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(meta.getEntityClass());
        Object bodyEntity;

        if (info != null && info.createDtoClass() != null) {
            // Use Mapper
            Object dto = objectMapper.convertValue(body, info.createDtoClass());
            Result<Object> error = validateAndReturnError(dto, Create.class);
            if (error != null) {
                return error;
            }
            bodyEntity = ((BaseMapper<Object, Object, ?, ?, ?>) info.mapper()).toEntity(dto);
        } else {
            // No Mapper found, try to convert directly to Entity (simple fallback without DTO)
            try {
                bodyEntity = objectMapper.convertValue(body, meta.getEntityClass());
                Result<Object> error = validateAndReturnError(bodyEntity);
                if (error != null) {
                    return error;
                }
            } catch (IllegalArgumentException e) {
                return badRequest(ERR_INVALID_REQUEST_DATA);
            }
        }

        validateAssociations(meta, bodyEntity);
        Object created = crudService.create(meta, bodyEntity);
        Object createdId = meta.getAccessor() != null ? meta.getAccessor().get(created, "id") : null;
        if (createdId != null) {
            notifyCreated(meta, createdId);
        }
        return Result.getSuccess(toResponse(meta, created));
    }

    private void validateAssociations(EntityMeta meta, Object bodyEntity) {
        AssociationValidator validator = associationValidatorProvider.getIfAvailable();
        if (validator != null) {
            validator.validateAssociations(meta, bodyEntity);
        }
    }

    /**
     * 树形实体更新时检测循环引用：新 parentId 不能是自身或自身的后代。
     */
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

    @PutMapping("/{entity}/{id}")
    @SuppressWarnings("unchecked")
    public Result<Object> update(@PathVariable String entity,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isUpdateEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_UPDATE));
        if (forbidden != null) {
            return forbidden;
        }
        Object idObj = parseIdOrBadRequest(meta, id);
        if (idObj == null) {
            return badRequest(ERR_ID_FORMAT);
        }

        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(meta.getEntityClass());
        Object bodyEntity;

        if (info != null && info.updateDtoClass() != null) {
            // Use Mapper
            Object dto = objectMapper.convertValue(body, info.updateDtoClass());
            Result<Object> error = validateAndReturnError(dto, Update.class);
            if (error != null) {
                return error;
            }
            // Instantiate entity and update it
            bodyEntity = BeanUtils.instantiateClass(meta.getEntityClass());
            ((BaseMapper<Object, ?, Object, ?, ?>) info.mapper()).updateEntity(dto, bodyEntity);
        } else {
            // No Mapper found, try to convert directly to Entity
            try {
                bodyEntity = objectMapper.convertValue(body, meta.getEntityClass());
                Result<Object> error = validateAndReturnError(bodyEntity);
                if (error != null) {
                    return error;
                }
            } catch (IllegalArgumentException e) {
                return badRequest(ERR_INVALID_REQUEST_DATA);
            }
        }

        setEntityId(bodyEntity, idObj);
        validateAssociations(meta, bodyEntity);
        validateTreeNoCycle(meta, idObj, bodyEntity);
        Object oldEntity = crudService.get(meta, idObj);
        Object updated = crudService.update(meta, idObj, bodyEntity);
        if (updated != null) {
            notifyUpdated(meta, idObj);
            auditAssociationChangesIfNeeded(meta, idObj, oldEntity, updated);
        }
        return updated == null ? notFound() : Result.getSuccess(toResponse(meta, updated));
    }

    private void auditAssociationChangesIfNeeded(EntityMeta meta, Object id, Object oldEntity, Object updated) {
        AssociationChangeAuditor auditor = associationChangeAuditorProvider.getIfAvailable();
        if (auditor == null || meta.getFields() == null || meta.getAccessor() == null) {
            return;
        }
        List<FieldMeta> changedFields = new ArrayList<>();
        Map<String, Object> oldDisplays = new LinkedHashMap<>();
        Map<String, Object> newDisplays = new LinkedHashMap<>();
        for (FieldMeta field : meta.getFields()) {
            if (!field.isForeignKey()) {
                continue;
            }
            Object oldVal = meta.getAccessor().get(oldEntity, field.getName());
            Object newVal = meta.getAccessor().get(updated, field.getName());
            if (Objects.equals(oldVal, newVal)) {
                continue;
            }
            changedFields.add(field);
            String refEntity = field.getReferencedEntity();
            String displayField = (field.getDisplayField() != null && !field.getDisplayField().isBlank())
                    ? field.getDisplayField() : "name";
            EntityMeta refMeta = refEntity != null && !refEntity.isBlank()
                    ? entityRegistry.getByEntityName(refEntity.trim()) : null;
            if (refMeta != null && refMeta.getAccessor() != null) {
                Object oldRef = oldVal != null ? crudService.get(refMeta, oldVal) : null;
                Object newRef = newVal != null ? crudService.get(refMeta, newVal) : null;
                oldDisplays.put(field.getName(), oldRef != null ? refMeta.getAccessor().get(oldRef, displayField) : null);
                newDisplays.put(field.getName(), newRef != null ? refMeta.getAccessor().get(newRef, displayField) : null);
            }
        }
        if (!changedFields.isEmpty()) {
            auditor.auditAssociationChanges(meta, id, changedFields, oldDisplays, newDisplays);
        }
    }

    private static void setEntityId(Object entity, Object id) {
        if (entity == null || id == null) {
            return;
        }
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true); //NOSONAR
            idField.set(entity, id);     //NOSONAR
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // ignore
        }
    }

    @DeleteMapping("/{entity}/{id}")
    public Result<Object> delete(@PathVariable String entity, @PathVariable String id) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isDeleteEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_DELETE));
        if (forbidden != null) {
            return forbidden;
        }
        Object idObj = parseIdOrBadRequest(meta, id);
        if (idObj == null) {
            return badRequest(ERR_ID_FORMAT);
        }
        CascadeDeleteService cascadeDelete = cascadeDeleteServiceProvider.getIfAvailable();
        if (cascadeDelete != null) {
            cascadeDelete.checkCascadeConstraints(meta, idObj);
            cascadeDelete.executeCascadeDelete(meta, idObj);
        }
        crudService.delete(meta, idObj);
        notifyDeleted(meta, idObj);
        return Result.getSuccess(null);
    }

    /**
     * 删除。请求体为 ID 列表，例如 [1, 2, 3] 或 ["uuid1", "uuid2"]，按实体主键类型解析。
     */
    @DeleteMapping("/{entity}/batch")
    public Result<Object> deleteBatch(@PathVariable String entity, @RequestBody List<Object> ids) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isDeleteBatchEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_DELETE));
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
        CascadeDeleteService cascadeDelete = cascadeDeleteServiceProvider.getIfAvailable();
        if (cascadeDelete != null) {
            for (Object idObj : parsedIds) {
                cascadeDelete.checkCascadeConstraints(meta, idObj);
            }
            for (Object idObj : parsedIds) {
                cascadeDelete.executeCascadeDelete(meta, idObj);
            }
        }
        crudService.deleteBatch(meta, parsedIds);
        for (Object idObj : parsedIds) {
            notifyDeleted(meta, idObj);
        }
        return Result.getSuccess(null);
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
            return List.of();
        }
    }

    /**
     * 更新。请求体为对象列表，每项需包含 id 及要更新的字段，例如 [{"id":1,"name":"a"},{"id":2,"name":"b"}]。
     */
    @PutMapping("/{entity}/batch")
    public Result<Object> updateBatch(@PathVariable String entity, @RequestBody List<Map<String, Object>> body) {
        EntityMeta meta = entityRegistry.getByPathSegment(entity);
        if (meta == null || !meta.isUpdateBatchEnabled()) {
            return notFound();
        }
        Result<Object> forbidden = requirePermission(getRequiredPermissionsForCrud(meta, CRUD_UPDATE));
        if (forbidden != null) {
            return forbidden;
        }
        if (body == null || body.isEmpty()) {
            return Result.getSuccess(List.of());
        }

        List<Object> entities = new ArrayList<>(body.size());
        Map<Object, Object> oldEntities = new LinkedHashMap<>();
        Class<?> pkType = meta.getPrimaryKeyType() != null ? meta.getPrimaryKeyType() : Long.class;
        EntityMapperProvider.MapperInfo info = mapperProvider.getMapperInfo(meta.getEntityClass());

        for (Map<String, Object> map : body) {
            Result<Object> error = processBatchUpdateItem(map, meta, pkType, info, entities, oldEntities);
            if (error != null) {
                return error;
            }
        }
        List<?> updated = crudService.updateBatch(meta, entities);
        for (Object object : updated) {
            Object id = meta.getAccessor() != null ? meta.getAccessor().get(object, "id") : null;
            if (id != null) {
                notifyUpdated(meta, id);
                Object oldEntity = oldEntities.get(id);
                if (oldEntity != null) {
                    auditAssociationChangesIfNeeded(meta, id, oldEntity, object);
                }
            }
        }
        return Result.getSuccess(toResponseList(meta, updated));
    }

    @SuppressWarnings("unchecked")
    private Result<Object> processBatchUpdateItem(Map<String, Object> map,
            EntityMeta meta, Class<?> pkType, EntityMapperProvider.MapperInfo info,
            List<Object> entities, Map<Object, Object> oldEntities) {
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
        Object oldEntity = crudService.get(meta, idParsed);
        if (oldEntity != null) {
            oldEntities.put(idParsed, oldEntity);
        }

        Object bodyEntity;
        if (info != null && info.updateDtoClass() != null) {
            Object dto = objectMapper.convertValue(map, info.updateDtoClass());
            Result<Object> ve = validateAndReturnError(dto, Update.class);
            if (ve != null) {
                return ve;
            }
            bodyEntity = BeanUtils.instantiateClass(meta.getEntityClass());
            ((BaseMapper<Object, ?, Object, ?, ?>) info.mapper()).updateEntity(dto, bodyEntity);
        } else {
            try {
                bodyEntity = objectMapper.convertValue(map, meta.getEntityClass());
                Result<Object> ve = validateAndReturnError(bodyEntity);
                if (ve != null) {
                    return ve;
                }
            } catch (IllegalArgumentException e) {
                return badRequest(ERR_INVALID_REQUEST_DATA);
            }
        }

        setEntityId(bodyEntity, idParsed);
        validateAssociations(meta, bodyEntity);
        validateTreeNoCycle(meta, idParsed, bodyEntity);
        entities.add(bodyEntity);
        return null;
    }

    /**
     * 导出 Excel。请求体与 search 相同（filters、sort、page、size），仅 size
     * 默认更大；仅导出未标注 @DataforgeExport(enabled=false) 的字段。
     */
    @PostMapping("/{entity}/export")
    public ResponseEntity<byte[]> export(@PathVariable String entity,
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
        int safeSize = Math.clamp(request.size(), 1, properties.getMaxExportSize());
        Pageable pageable = buildPageable(request.page(), safeSize, request.sort());
        ListCriteria criteria = ListCriteria.from(request, meta);
        Page<?> pageResult = crudService.list(meta, pageable, criteria);
        try {
            byte[] bytes = ExcelExportSupport.toExcel(meta, pageResult.getContent(), entityRegistry, crudService);
            String filename = entity + "-export.xlsx";
            return ResponseEntity.ok()
                                 .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                 .contentType(MediaType.parseMediaType(
                                         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                 .body(bytes);
        } catch (ReflectiveOperationException | UncheckedIOException | IllegalStateException e) {
            throw new IllegalStateException("导出 Excel 失败", e);
        }
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

    /**
     * Action 路由在映射层接受 GET/POST/PUT/DELETE，以便不同 Action 可声明不同 HTTP 方法。
     * 安全性由 executeActionInternal 保证：每个 Action 的 ActionMeta.method 指定唯一允许的方法，
     * 不匹配时返回 405；且权限校验在方法校验之后执行，两种校验均通过才执行业务逻辑。
     */
    @RequestMapping(
            path = "/{entity}/{id}/_action/{actionName}",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE} //NOSONAR
    )
    public Result<Object> executeAction(jakarta.servlet.http.HttpServletRequest request,
            @PathVariable String entity,
            @PathVariable String id,
            @PathVariable String actionName,
            @RequestBody(required = false) Map<String, Object> body) {
        return executeActionInternal(request, entity, id, actionName, body);
    }

    private Result<Object> executeActionInternal(jakarta.servlet.http.HttpServletRequest request,
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
                return badRequest(ERR_ID_FORMAT);
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
        List<String> actionPerms =
                actionMeta.getPermissions() != null ? actionMeta.getPermissions() : Collections.emptyList();
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
     * 无 ID 的 Action，支持 GET/POST/PUT/DELETE。
     * 安全性同上：每个 Action 的 method 指定唯一允许的方法，不匹配返回 405，且需通过权限校验。
     */
    @RequestMapping(
            path = "/{entity}/_action/{actionName}",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE} //NOSONAR
    )
    public Result<Object> executeActionNoId(HttpServletRequest request,
            @PathVariable String entity,
            @PathVariable String actionName,
            @RequestBody(required = false) Map<String, Object> body) {
        // search, export, batch 等保留字已经由具体方法处理，此处处理其他自定义 Action
        return executeActionInternal(request, entity, null, actionName, body);
    }
}
