package com.lrenyi.template.dataforge.support;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.annotation.DataforgeDto;
import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.annotation.DataforgeExport;
import com.lrenyi.template.dataforge.annotation.DataforgeField;
import com.lrenyi.template.dataforge.annotation.DataforgeImport;
import com.lrenyi.template.dataforge.annotation.DtoType;
import com.lrenyi.template.dataforge.annotation.EntityAction;
import com.lrenyi.template.dataforge.domain.BaseEntity;
import com.lrenyi.template.dataforge.meta.ActionMeta;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import jakarta.persistence.EntityManagerFactory;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 启动时扫描 @DataforgeEntity 与 @EntityAction，构建 EntityMeta/ActionMeta 并注册。
 * 当 scan-packages 配置非空时使用 classpath 扫描，可注册 JPA 与非 JPA 的 @DataforgeEntity（如
 * Auth）；
 * 否则若 JPA 可用则从 Metamodel 获取。
 */
public class MetaScanner {

    private static final Logger log = LoggerFactory.getLogger(MetaScanner.class);

    private final EntityRegistry entityRegistry;
    private final ActionRegistry actionRegistry;
    private final String basePackage;
    /**
     * -- SETTER --
     * 在 @PostConstruct 时注入，确保 JPA 已就绪后再扫描。
     */
    @Setter
    private EntityManagerFactory entityManagerFactory;

    public MetaScanner(EntityRegistry entityRegistry, ActionRegistry actionRegistry, String basePackage) {
        this.entityRegistry = entityRegistry;
        this.actionRegistry = actionRegistry;
        this.basePackage = basePackage == null || basePackage.isEmpty() ? "" : basePackage;
    }

    /**
     * 仅扫描并注册 @DataforgeEntity 实体（不触发 getBeansOfType，避免在 SmartInitializingSingleton
     * 等阶段卡住）。scan-packages 非空时优先 classpath 扫描（覆盖 JPA 与非 JPA 的 @DataforgeEntity）；
     * 否则且 JPA 可用时从 Metamodel 获取。
     */
    public void registerEntitiesOnly() {
        if (StringUtils.hasText(basePackage)) {
            registerFromClasspathScan();
        } else if (entityManagerFactory != null) {
            registerFromMetamodel();
        }
    }

    /**
     * 仅注册 Action 执行器（实体需已通过 registerEntitiesOnly 注册）。
     */
    public void registerActionExecutors(List<Object> actionExecutorBeans) {
        if (actionExecutorBeans == null) {
            return;
        }
        for (Object bean : actionExecutorBeans) {
            if (!(bean instanceof EntityActionExecutor executor)) {
                continue;
            }
            Class<?> clazz = ClassUtils.getUserClass(bean.getClass());
            EntityAction ann = clazz.getAnnotation(EntityAction.class);
            if (ann == null) {
                continue;
            }
            String entityPathSegment = pathSegmentFor(ann.entity());
            ActionMeta actionMeta = buildActionMeta(ann);
            actionRegistry.register(entityPathSegment, ann.actionName(), actionMeta, executor);
            EntityMeta entityMeta = entityRegistry.getByEntityName(ann.entity().getSimpleName());
            if (entityMeta != null) {
                entityMeta.getActions().add(actionMeta);
            }
            log.debug("Registered action: {}:{}", entityPathSegment, ann.actionName());
        }
    }

    /**
     * 扫描 @DataforgeEntity 实体并注册；注册带 @EntityAction 的 Action 执行器。
     * 实体注册逻辑见 registerEntitiesOnly()。
     */
    public void scanAndRegister(List<Object> actionExecutorBeans) {
        registerEntitiesOnly();
        registerActionExecutors(actionExecutorBeans);
    }

    /** 从 JPA Metamodel 获取实体，避免 classpath 扫描。 */
    private void registerFromMetamodel() {
        Set<String> packagePrefixes = basePackage.isEmpty() ? Set.of()
                : Arrays.stream(basePackage.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
        for (var managedType : entityManagerFactory.getMetamodel().getManagedTypes()) {
            Class<?> clazz = managedType.getJavaType();
            if (clazz == null) {
                continue;
            }
            if (!packagePrefixes.isEmpty()) {
                String pkg = clazz.getPackageName();
                boolean inScope = packagePrefixes.stream().anyMatch(p -> pkg.equals(p) || pkg.startsWith(p + "."));
                if (!inScope) {
                    continue;
                }
            }
            registerEntity(clazz);
        }
    }

    private void registerEntity(Class<?> clazz) {
        DataforgeEntity ann = clazz.getAnnotation(DataforgeEntity.class);
        if (ann == null) {
            return;
        }
        validateExtendsBaseEntity(clazz);
        EntityMeta meta = buildEntityMeta(clazz, ann);
        meta.setEntityClass(clazz);
        entityRegistry.register(meta);
        log.debug("Registered entity: {}", meta.getPathSegment());
    }

    /** 回退：通过 classpath 扫描（在长 classpath 下可能较慢）。 */
    private void registerFromClasspathScan() {
        String[] packages = basePackage.isEmpty() ? new String[0] : basePackage.split(",");
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(DataforgeEntity.class));
        for (String pkg : packages) {
            String trimmed = pkg.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            for (BeanDefinition bd : provider.findCandidateComponents(trimmed)) {
                String className = bd.getBeanClassName();
                if (className == null) {
                    continue;
                }
                try {
                    Class<?> clazz = ClassUtils.forName(className, null);
                    registerEntity(clazz);
                } catch (ClassNotFoundException e) {
                    log.warn("Cannot load entity class: {}", className, e);
                }
            }
        }
    }

    private static void validateExtendsBaseEntity(Class<?> clazz) {
        if (!BaseEntity.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException("@DataforgeEntity 实体 " + clazz.getName() + " 必须继承 BaseEntity<ID>");
        }
    }

    private EntityMeta buildEntityMeta(Class<?> clazz, DataforgeEntity ann) {
        EntityMeta meta = new EntityMeta();
        String simpleName = clazz.getSimpleName();
        meta.setEntityName(simpleName);
        meta.setTableName(StringUtils.hasText(ann.table()) ? ann.table() : toSnakeCase(simpleName));
        meta.setPathSegment(StringUtils.hasText(ann.pathSegment()) ? ann.pathSegment() : toPluralLower(simpleName));
        meta.setDisplayName(StringUtils.hasText(ann.displayName()) ? ann.displayName() : simpleName);
        meta.setCrudEnabled(ann.crudEnabled());
        meta.setListEnabled(ann.crudEnabled() && ann.enableList());
        meta.setGetEnabled(ann.crudEnabled() && ann.enableGet());
        meta.setCreateEnabled(ann.crudEnabled() && ann.enableCreate());
        meta.setUpdateEnabled(ann.crudEnabled() && ann.enableUpdate());
        meta.setUpdateBatchEnabled(ann.crudEnabled() && ann.enableUpdateBatch());
        meta.setDeleteEnabled(ann.crudEnabled() && ann.enableDelete());
        meta.setDeleteBatchEnabled(ann.crudEnabled() && ann.enableDeleteBatch());
        meta.setExportEnabled(ann.crudEnabled() && ann.enableExport());
        String pathSeg = meta.getPathSegment();
        meta.setPermissionCreate(
                defaultPermission(ann.permissionCreate(), pathSeg, ann.crudEnabled() && ann.enableCreate(), "create"));
        meta.setPermissionRead(defaultPermission(
                ann.permissionRead(), pathSeg, (ann.crudEnabled() && ann.enableList())
                        || (ann.crudEnabled() && ann.enableGet()) || (ann.crudEnabled() && ann.enableExport()),
                "read"));
        meta.setPermissionUpdate(defaultPermission(ann.permissionUpdate(), pathSeg,
                ann.crudEnabled() && (ann.enableUpdate() || ann.enableUpdateBatch()), "update"));
        meta.setPermissionDelete(defaultPermission(ann.permissionDelete(), pathSeg,
                ann.crudEnabled() && (ann.enableDelete() || ann.enableDeleteBatch()), "delete"));
        Class<?> pkType = ann.primaryKeyType() != void.class ? ann.primaryKeyType() : inferPrimaryKeyType(clazz);
        meta.setPrimaryKeyType(pkType);
        
        // ==================== 新增生产级属性设置 ====================
        meta.setDescription(ann.description());
        meta.setDefaultSortField(ann.defaultSortField());
        meta.setDefaultSortDirection(ann.defaultSortDirection());
        meta.setDefaultPageSize(ann.defaultPageSize());
        meta.setPageSizeOptions(ann.pageSizeOptions());
        meta.setEnableVirtualScroll(ann.enableVirtualScroll());
        meta.setVirtualScrollRowHeight(ann.virtualScrollRowHeight());
        meta.setTreeEntity(ann.treeEntity());
        meta.setTreeParentField(ann.treeParentField());
        meta.setTreeChildrenField(ann.treeChildrenField());
        meta.setTreeNameField(ann.treeNameField());
        meta.setTreeCodeField(ann.treeCodeField());
        meta.setTreeMaxDepth(ann.treeMaxDepth());
        meta.setSoftDelete(ann.softDelete());
        meta.setDeleteFlagField(ann.deleteFlagField());
        meta.setDeleteTimeField(ann.deleteTimeField());
        meta.setDeleteFlagType(ann.deleteFlagType());
        meta.setEnableCreateAudit(ann.enableCreateAudit());
        meta.setEnableUpdateAudit(ann.enableUpdateAudit());
        meta.setEnableDeleteAudit(ann.enableDeleteAudit());
        meta.setCreateUserField(ann.createUserField());
        meta.setUpdateUserField(ann.updateUserField());
        meta.setEnableCache(ann.enableCache());
        meta.setCacheExpireSeconds(ann.cacheExpireSeconds());
        meta.setCacheRegion(ann.cacheRegion());
        meta.setEnableQueryOptimization(ann.enableQueryOptimization());
        meta.setMaxBatchSize(ann.maxBatchSize());
        meta.setTags(ann.tags());
        meta.setIcon(ann.icon());
        meta.setColor(ann.color());
        meta.setShowInMenu(ann.showInMenu());
        meta.setMenuOrder(ann.menuOrder());
        meta.setEnableOperationLog(ann.enableOperationLog());
        meta.setEnableVersionControl(ann.enableVersionControl());
        meta.setVersionField(ann.versionField());
        meta.setEnableDataPermission(ann.enableDataPermission());
        meta.setDataPermissionType(ann.dataPermissionType());
        meta.setEnableImport(ann.enableImport());
        meta.setImportTemplate(ann.importTemplate());
        meta.setExportTemplate(ann.exportTemplate());
        
        meta.setFields(buildFieldMetas(clazz));
        return meta;
    }

    /**
     * 从 BaseEntity&lt;ID&gt; 泛型参数推断主键类型；若泛型擦除则回退到 id 字段类型。
     */
    private static Class<?> inferPrimaryKeyType(Class<?> clazz) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            Type genericSuperclass = c.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType pt) {
                Type rawType = pt.getRawType();
                if (rawType == BaseEntity.class) {
                    Type idType = pt.getActualTypeArguments()[0];
                    if (idType instanceof Class<?> idClass) {
                        return idClass;
                    }
                }
            }
            c = c.getSuperclass();
        }
        return inferFromIdField(clazz);
    }

    /** 泛型擦除时的回退：遍历类层级，从 id 字段或 @Id 注解字段推断类型。 */
    private static Class<?> inferFromIdField(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if ("id".equals(f.getName()) || f.getAnnotation(jakarta.persistence.Id.class) != null) {
                    return f.getType();
                }
            }
        }
        return Long.class;
    }

    private List<FieldMeta> buildFieldMetas(Class<?> clazz) {
        List<FieldMeta> list = new ArrayList<>();
        List<String> annotated = new ArrayList<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.add(c);
        }
        Collections.reverse(hierarchy);
        
        // 第一步：收集所有被 @DataforgeField(searchable=true) 标注的字段名
        for (Class<?> c : hierarchy) {
            for (Field f : c.getDeclaredFields()) {
                DataforgeField dataforgeField = f.getAnnotation(DataforgeField.class);
                if (dataforgeField != null && dataforgeField.searchable()) {
                    annotated.add(f.getName());
                }
            }
        }
        
        // 第二步：处理每个字段
        for (Class<?> c : hierarchy) {
            for (Field f : c.getDeclaredFields()) {
                FieldMeta fm = new FieldMeta();
                fm.setName(f.getName());
                fm.setType(f.getType().getSimpleName());
                fm.setColumnName(toSnakeCase(f.getName()));
                Class<?> fieldType = f.getType();
                fm.setPrimaryKey(
                        "id".equalsIgnoreCase(f.getName()) && (fieldType == Long.class || fieldType == long.class
                                || fieldType == String.class || fieldType == UUID.class || fieldType == Integer.class
                                || fieldType == int.class));
                fm.setRequired(fm.isPrimaryKey());
                fm.setQueryable(annotated.contains(f.getName()));
                
                // ==================== 处理新注解 @DataforgeField ====================
                DataforgeField dataforgeField = f.getAnnotation(DataforgeField.class);
                if (dataforgeField != null) {
                    // 基础信息
                    fm.setLabel(StringUtils.hasText(dataforgeField.label()) ? dataforgeField.label() : f.getName());
                    fm.setDescription(dataforgeField.description());
                    fm.setOrder(dataforgeField.order());
                    fm.setGroup(dataforgeField.group());
                    fm.setGroupOrder(dataforgeField.groupOrder());
                    
                    // 表格列配置
                    fm.setColumnVisible(dataforgeField.columnVisible());
                    fm.setColumnResizable(dataforgeField.columnResizable());
                    fm.setColumnSortable(dataforgeField.columnSortable());  // 用户特别关注的属性
                    fm.setColumnFilterable(dataforgeField.columnFilterable());
                    fm.setColumnAlign(dataforgeField.columnAlign());
                    fm.setColumnWidth(dataforgeField.columnWidth());
                    fm.setColumnMinWidth(dataforgeField.columnMinWidth());
                    fm.setColumnFixed(dataforgeField.columnFixed());
                    fm.setColumnEllipsis(dataforgeField.columnEllipsis());
                    fm.setColumnClassName(dataforgeField.columnClassName());
                    
                    // 表单控件配置
                    fm.setComponent(dataforgeField.component());
                    fm.setPlaceholder(dataforgeField.placeholder());
                    fm.setTips(dataforgeField.tips());
                    fm.setUiRequired(dataforgeField.required());
                    fm.setReadonly(dataforgeField.readonly());
                    fm.setDisabled(dataforgeField.disabled());
                    fm.setHidden(dataforgeField.hidden());
                    
                    // 验证规则
                    fm.setRegex(dataforgeField.regex());
                    fm.setRegexMessage(dataforgeField.regexMessage());
                    fm.setMinLength(dataforgeField.minLength());
                    fm.setMaxLength(dataforgeField.maxLength());
                    fm.setMinValue(dataforgeField.minValue());
                    fm.setMaxValue(dataforgeField.maxValue());
                    fm.setAllowedValues(dataforgeField.allowedValues());
                    
                    // 数据字典/枚举
                    fm.setDictCode(dataforgeField.dictCode());
                    fm.setEnumOptions(dataforgeField.enumOptions());
                    fm.setEnumLabels(dataforgeField.enumLabels());
                    
                    // 搜索配置
                    fm.setSearchable(dataforgeField.searchable());
                    fm.setSearchType(dataforgeField.searchType());
                    fm.setSearchComponent(dataforgeField.searchComponent());
                    fm.setSearchDefaultValue(dataforgeField.searchDefaultValue());
                    fm.setSearchRequired(dataforgeField.searchRequired());
                    fm.setSearchPlaceholder(dataforgeField.searchPlaceholder());
                    fm.setSearchRangeFields(dataforgeField.searchRangeFields());
                    // 设置搜索标签和顺序（使用DataforgeField的label和order）
                    if (dataforgeField.searchable()) {
                        fm.setSearchLabel(StringUtils.hasText(dataforgeField.label()) ? dataforgeField.label() : f.getName());
                        fm.setSearchOrder(dataforgeField.order());
                    }
                    
                    // 数据转换与显示
                    fm.setFormat(dataforgeField.format());
                    fm.setMaskPattern(dataforgeField.maskPattern());
                    fm.setMaskType(dataforgeField.maskType());
                    fm.setSensitive(dataforgeField.sensitive());
                    fm.setDefaultValue(dataforgeField.defaultValue());
                    fm.setDefaultValueExpression(dataforgeField.defaultValueExpression());
                    
                    // 关联关系
                    fm.setForeignKey(dataforgeField.foreignKey());
                    fm.setReferencedEntity(dataforgeField.referencedEntity());
                    fm.setReferencedField(dataforgeField.referencedField());
                    fm.setDisplayField(dataforgeField.displayField());
                    fm.setValueField(dataforgeField.valueField());
                    fm.setLazyLoad(dataforgeField.lazyLoad());
                    
                    // 如果 @DataforgeField 设置了 searchable=true，更新查询相关属性
                    if (dataforgeField.searchable()) {
                        fm.setQueryable(true);
                        if (!StringUtils.hasText(fm.getSearchLabel())) {
                            fm.setSearchLabel(fm.getLabel());
                        }
                        if (fm.getSearchOrder() == 0) {
                            fm.setSearchOrder(fm.getOrder());
                        }
                    }
                }
                
                // ==================== 处理新注解 @DataforgeExport ====================
                DataforgeExport dataforgeExport = f.getAnnotation(DataforgeExport.class);
                if (dataforgeExport != null) {
                    fm.setExportEnabled(dataforgeExport.enabled());
                    fm.setExportHeader(dataforgeExport.header());
                    fm.setExportOrder(dataforgeExport.order());
                    fm.setExportFormat(dataforgeExport.format());
                    if (dataforgeExport.converter() != null && dataforgeExport.converter()
                            != com.lrenyi.template.dataforge.support.ExportValueConverter.class) {
                        fm.setExportConverterClassName(dataforgeExport.converter().getName());
                    }
                    fm.setExportWidth(dataforgeExport.width());
                    fm.setExportCellStyle(dataforgeExport.cellStyle());
                    fm.setExportWrapText(dataforgeExport.wrapText());
                    fm.setExportColumnType(dataforgeExport.columnType());
                    fm.setExportComment(dataforgeExport.comment());
                    fm.setExportHidden(dataforgeExport.hidden());
                    fm.setExportGroup(dataforgeExport.group());
                    fm.setExportFrozen(dataforgeExport.frozen());
                    fm.setExportDataValidation(dataforgeExport.dataValidation());
                    fm.setExportHyperlinkFormula(dataforgeExport.hyperlinkFormula());
                    
                    // 设置导出排除状态
                    fm.setExportExcluded(!dataforgeExport.enabled());
                }
                
                // ==================== 处理新注解 @DataforgeImport ====================
                DataforgeImport dataforgeImport = f.getAnnotation(DataforgeImport.class);
                if (dataforgeImport != null) {
                    fm.setImportEnabled(dataforgeImport.enabled());
                    fm.setImportRequired(dataforgeImport.required());
                    fm.setImportSample(dataforgeImport.sample());
                    if (dataforgeImport.converter() != null && dataforgeImport.converter()
                            != com.lrenyi.template.dataforge.support.ImportValueConverter.class) {
                        fm.setImportConverterClassName(dataforgeImport.converter().getName());
                    }
                    fm.setImportValidationRegex(dataforgeImport.validationRegex());
                    fm.setImportValidationMessage(dataforgeImport.validationMessage());
                    fm.setImportDefaultValue(dataforgeImport.defaultValue());
                    fm.setImportUnique(dataforgeImport.unique());
                    fm.setImportDuplicateMessage(dataforgeImport.duplicateMessage());
                    fm.setImportDictCode(dataforgeImport.dictCode());
                    fm.setImportAllowedValues(dataforgeImport.allowedValues());
                    fm.setImportMinValue(dataforgeImport.minValue());
                    fm.setImportMaxValue(dataforgeImport.maxValue());
                    fm.setImportMinLength(dataforgeImport.minLength());
                    fm.setImportMaxLength(dataforgeImport.maxLength());
                    fm.setImportDateFormat(dataforgeImport.dateFormat());
                    fm.setImportIgnoreCase(dataforgeImport.ignoreCase());
                    fm.setImportTrim(dataforgeImport.trim());
                    fm.setImportErrorPolicy(dataforgeImport.errorPolicy().name());
                }
                
                // ==================== 处理新注解 @DataforgeDto ====================
                DataforgeDto dataforgeDto = f.getAnnotation(DataforgeDto.class);
                if (dataforgeDto != null) {
                    // 转换 DtoType 枚举为字符串数组
                    if (dataforgeDto.include().length > 0) {
                        fm.setDtoIncludeTypes(Arrays.stream(dataforgeDto.include())
                                                    .map(DtoType::name)
                                                    .toArray(String[]::new));
                    }
                    if (dataforgeDto.exclude().length > 0) {
                        fm.setDtoExcludeTypes(Arrays.stream(dataforgeDto.exclude())
                                                    .map(DtoType::name)
                                                    .toArray(String[]::new));
                    }
                    fm.setDtoFieldName(dataforgeDto.fieldName());
                    fm.setDtoFieldType(dataforgeDto.fieldType());
                    if (dataforgeDto.converter() != null && dataforgeDto.converter() != void.class) {
                        fm.setDtoConverterClassName(dataforgeDto.converter().getName());
                    }
                    fm.setDtoFormat(dataforgeDto.format());
                    fm.setDtoValidationGroups(dataforgeDto.validationGroups());
                    fm.setDtoReadOnly(dataforgeDto.readOnly());
                    fm.setDtoWriteOnly(dataforgeDto.writeOnly());
                    fm.setDtoCreateOnly(dataforgeDto.createOnly());
                    fm.setDtoUpdateOnly(dataforgeDto.updateOnly());
                    fm.setDtoQueryOnly(dataforgeDto.queryOnly());
                }
                
                // ==================== 处理类级别的 @DataforgeDto parentOverrides ====================
                // 注意：这需要在后续步骤中处理，因为需要类级别的注解信息
                
                list.add(fm);
            }
        }
        return list;
    }

    private ActionMeta buildActionMeta(EntityAction ann) {
        ActionMeta meta = new ActionMeta();
        meta.setActionName(ann.actionName());
        meta.setEntityName(ann.entity().getSimpleName());
        meta.setMethod(ann.method());
        meta.setRequestType(ann.requestType());
        meta.setResponseType(ann.responseType());
        meta.setSummary(ann.summary());
        meta.setDescription(ann.description());
        meta.setDescription(ann.description());
        meta.setRequireId(ann.requireId());
        if (ann.permissions() != null && ann.permissions().length > 0) {
            meta.setPermissions(Arrays.stream(ann.permissions()).collect(Collectors.toList()));
        }
        return meta;
    }

    private static String defaultPermission(String fromAnnotation, String pathSegment, boolean operationEnabled,
            String action) {
        if (StringUtils.hasText(fromAnnotation)) {
            return fromAnnotation.trim();
        }
        return operationEnabled && StringUtils.hasText(pathSegment) ? pathSegment + ":" + action : "";
    }

    private String pathSegmentFor(Class<?> entityClass) {
        DataforgeEntity pe = entityClass.getAnnotation(DataforgeEntity.class);
        if (pe != null && StringUtils.hasText(pe.pathSegment())) {
            return pe.pathSegment();
        }
        return toPluralLower(entityClass.getSimpleName());
    }

    private static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String toPluralLower(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return simpleName;
        }
        String lower = simpleName.toLowerCase(Locale.ROOT);
        if (lower.endsWith("y") && lower.length() > 1 && !isVowel(lower.charAt(lower.length() - 2))) {
            return lower.substring(0, lower.length() - 1) + "ies";
        }
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("ch") || lower.endsWith("sh")) {
            return lower + "es";
        }
        return lower + "s";
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
}
