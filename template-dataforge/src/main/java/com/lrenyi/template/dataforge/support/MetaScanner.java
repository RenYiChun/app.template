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
import com.lrenyi.template.dataforge.domain.DataforgePersistable;
import com.lrenyi.template.dataforge.meta.ActionMeta;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.ManagedType;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class MetaScanner {
    
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
    
    private static void validateImplementsDataforgePersistable(Class<?> clazz) {
        if (!DataforgePersistable.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException(
                    "@DataforgeEntity 实体 " + clazz.getName() + " 必须实现 DataforgePersistable<ID>");
        }
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
                // BaseEntity<ID>、MongoBaseDocument<ID> 等实现 DataforgePersistable 的基类
                if (rawType instanceof Class<?> rawClass && DataforgePersistable.class.isAssignableFrom(rawClass)) {
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
    
    /** 泛型擦除时的回退：遍历类层级，从 id 字段推断类型（存储无关）。 */
    private static Class<?> inferFromIdField(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if ("id".equals(f.getName())) {
                    return f.getType();
                }
            }
        }
        return Long.class;
    }
    
    private static String defaultPermission(String fromAnnotation,
            String pathSegment,
            boolean operationEnabled,
            String action) {
        if (StringUtils.hasText(fromAnnotation)) {
            return fromAnnotation.trim();
        }
        return operationEnabled && StringUtils.hasText(pathSegment) ? pathSegment + ":" + action : "";
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
        if (shouldUseIesSuffix(lower)) {
            return lower.substring(0, lower.length() - 1) + "ies";
        }
        if (shouldUseEsSuffix(lower)) {
            return lower + "es";
        }
        return lower + "s";
    }
    
    private static boolean shouldUseIesSuffix(String lower) {
        return lower.endsWith("y") && lower.length() > 1 && !isVowel(lower.charAt(lower.length() - 2));
    }
    
    private static boolean shouldUseEsSuffix(String lower) {
        return lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("ch") || lower.endsWith("sh");
    }
    
    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
    
    private static void applyPermissionDefaults(EntityMeta meta, DataforgeEntity ann, String pathSeg) {
        meta.setPermissionCreate(defaultPermission(ann.permissionCreate(),
                                                   pathSeg,
                                                   ann.crudEnabled() && ann.enableCreate(),
                                                   "create"
        ));
        meta.setPermissionRead(defaultPermission(ann.permissionRead(), pathSeg, isReadEnabled(ann), "read"));
        meta.setPermissionUpdate(defaultPermission(ann.permissionUpdate(),
                                                   pathSeg,
                                                   ann.crudEnabled() && (ann.enableUpdate() || ann.enableUpdateBatch()),
                                                   "update"
        ));
        meta.setPermissionDelete(defaultPermission(ann.permissionDelete(),
                                                   pathSeg,
                                                   ann.crudEnabled() && (ann.enableDelete() || ann.enableDeleteBatch()),
                                                   "delete"
        ));
    }
    
    private static boolean isReadEnabled(DataforgeEntity ann) {
        return ann.crudEnabled() && (ann.enableList() || ann.enableGet() || ann.enableExport());
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
        Set<String> packagePrefixes = basePackage.isEmpty() ? Set.of() : Arrays.stream(basePackage.split(","))
                                                                               .map(String::trim)
                                                                               .filter(s -> !s.isEmpty())
                                                                               .collect(Collectors.toSet());
        for (ManagedType<?> managedType : entityManagerFactory.getMetamodel().getManagedTypes()) {
            Class<?> clazz = managedType.getJavaType();
            registerEntityIfValid(clazz, packagePrefixes);
        }
    }
    
    private void registerEntityIfValid(Class<?> clazz, Set<String> packagePrefixes) {
        if (clazz == null) {
            return;
        }
        if (!packagePrefixes.isEmpty()) {
            String pkg = clazz.getPackageName();
            boolean inScope = packagePrefixes.stream().anyMatch(p -> pkg.equals(p) || pkg.startsWith(p + "."));
            if (!inScope) {
                return;
            }
        }
        registerEntity(clazz);
    }
    
    private void registerEntity(Class<?> clazz) {
        DataforgeEntity ann = clazz.getAnnotation(DataforgeEntity.class);
        if (ann == null) {
            return;
        }
        validateImplementsDataforgePersistable(clazz);
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
        applyPermissionDefaults(meta, ann, pathSeg);
        Class<?> pkType = ann.primaryKeyType() != void.class ? ann.primaryKeyType() : inferPrimaryKeyType(clazz);
        meta.setPrimaryKeyType(pkType);
        meta.setStorageType(ann.storage());
        
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
    
    private List<FieldMeta> buildFieldMetas(Class<?> clazz) {
        List<Class<?>> hierarchy = buildClassHierarchy(clazz);
        List<String> searchableFields = collectSearchableFieldNames(hierarchy);
        
        List<FieldMeta> list = new ArrayList<>();
        for (Class<?> c : hierarchy) {
            for (Field f : c.getDeclaredFields()) {
                FieldMeta fm = createBaseFieldMeta(f, searchableFields);
                applyDataforgeField(f, fm);
                applyDataforgeExport(f, fm);
                applyDataforgeImport(f, fm);
                applyDataforgeDto(f, fm);
                list.add(fm);
            }
        }
        return list;
    }
    
    private static List<Class<?>> buildClassHierarchy(Class<?> clazz) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.add(c);
        }
        Collections.reverse(hierarchy);
        return hierarchy;
    }
    
    private static List<String> collectSearchableFieldNames(List<Class<?>> hierarchy) {
        List<String> annotated = new ArrayList<>();
        for (Class<?> c : hierarchy) {
            for (Field f : c.getDeclaredFields()) {
                DataforgeField df = f.getAnnotation(DataforgeField.class);
                if (df != null && df.searchable()) {
                    annotated.add(f.getName());
                }
            }
        }
        return annotated;
    }
    
    private FieldMeta createBaseFieldMeta(Field f, List<String> searchableFields) {
        FieldMeta fm = new FieldMeta();
        fm.setName(f.getName());
        fm.setType(f.getType().getSimpleName());
        fm.setColumnName(toSnakeCase(f.getName()));
        fm.setPrimaryKey(isPrimaryKeyField(f));
        fm.setRequired(fm.isPrimaryKey());
        fm.setQueryable(searchableFields.contains(f.getName()));
        return fm;
    }
    
    private static boolean isPrimaryKeyField(Field f) {
        if (!"id".equalsIgnoreCase(f.getName())) {
            return false;
        }
        Class<?> t = f.getType();
        return t == Long.class || t == long.class || t == String.class || t == UUID.class || t == Integer.class
                || t == int.class;
    }
    
    private void applyDataforgeField(Field f, FieldMeta fm) {
        DataforgeField df = f.getAnnotation(DataforgeField.class);
        if (df == null) {
            return;
        }
        fm.setLabel(StringUtils.hasText(df.label()) ? df.label() : f.getName());
        fm.setDescription(df.description());
        fm.setOrder(df.order());
        fm.setGroup(df.group());
        fm.setGroupOrder(df.groupOrder());
        fm.setColumnVisible(df.columnVisible());
        fm.setColumnResizable(df.columnResizable());
        fm.setColumnSortable(df.columnSortable());
        fm.setColumnFilterable(df.columnFilterable());
        fm.setColumnAlign(df.columnAlign());
        fm.setColumnWidth(df.columnWidth());
        fm.setColumnMinWidth(df.columnMinWidth());
        fm.setColumnFixed(df.columnFixed());
        fm.setColumnEllipsis(df.columnEllipsis());
        fm.setColumnClassName(df.columnClassName());
        fm.setComponent(df.component());
        fm.setPlaceholder(df.placeholder());
        fm.setTips(df.tips());
        fm.setUiRequired(df.required());
        fm.setReadonly(df.readonly());
        fm.setDisabled(df.disabled());
        fm.setHidden(df.hidden());
        fm.setRegex(df.regex());
        fm.setRegexMessage(df.regexMessage());
        fm.setMinLength(df.minLength());
        fm.setMaxLength(df.maxLength());
        fm.setMinValue(df.minValue());
        fm.setMaxValue(df.maxValue());
        fm.setAllowedValues(df.allowedValues());
        fm.setDictCode(df.dictCode());
        fm.setEnumOptions(df.enumOptions());
        fm.setEnumLabels(df.enumLabels());
        fm.setSearchable(df.searchable());
        fm.setSearchType(df.searchType());
        fm.setSearchComponent(df.searchComponent());
        fm.setSearchDefaultValue(df.searchDefaultValue());
        fm.setSearchRequired(df.searchRequired());
        fm.setSearchPlaceholder(df.searchPlaceholder());
        fm.setSearchRangeFields(df.searchRangeFields());
        fm.setFormat(df.format());
        fm.setMaskPattern(df.maskPattern());
        fm.setMaskType(df.maskType());
        fm.setSensitive(df.sensitive());
        fm.setDefaultValue(df.defaultValue());
        fm.setDefaultValueExpression(df.defaultValueExpression());
        fm.setForeignKey(df.foreignKey());
        fm.setReferencedEntity(df.referencedEntity());
        fm.setReferencedField(df.referencedField());
        fm.setDisplayField(df.displayField());
        fm.setValueField(df.valueField());
        fm.setLazyLoad(df.lazyLoad());
        
        if (df.searchable()) {
            fm.setQueryable(true);
            fm.setSearchOrder(fm.getSearchOrder() == 0 ? fm.getOrder() : fm.getSearchOrder());
        }
    }
    
    private void applyDataforgeExport(Field f, FieldMeta fm) {
        DataforgeExport exp = f.getAnnotation(DataforgeExport.class);
        if (exp == null) {
            return;
        }
        fm.setExportEnabled(exp.enabled());
        fm.setExportHeader(exp.header());
        fm.setExportOrder(exp.order());
        fm.setExportFormat(exp.format());
        if (exp.converter() != null && exp.converter() != ExportValueConverter.class) {
            fm.setExportConverterClassName(exp.converter().getName());
        }
        fm.setExportWidth(exp.width());
        fm.setExportCellStyle(exp.cellStyle());
        fm.setExportWrapText(exp.wrapText());
        fm.setExportColumnType(exp.columnType());
        fm.setExportComment(exp.comment());
        fm.setExportHidden(exp.hidden());
        fm.setExportGroup(exp.group());
        fm.setExportFrozen(exp.frozen());
        fm.setExportDataValidation(exp.dataValidation());
        fm.setExportHyperlinkFormula(exp.hyperlinkFormula());
        fm.setExportExcluded(!exp.enabled());
    }
    
    private void applyDataforgeImport(Field f, FieldMeta fm) {
        DataforgeImport imp = f.getAnnotation(DataforgeImport.class);
        if (imp == null) {
            return;
        }
        fm.setImportEnabled(imp.enabled());
        fm.setImportRequired(imp.required());
        fm.setImportSample(imp.sample());
        if (imp.converter() != null && imp.converter() != ImportValueConverter.class) {
            fm.setImportConverterClassName(imp.converter().getName());
        }
        fm.setImportValidationRegex(imp.validationRegex());
        fm.setImportValidationMessage(imp.validationMessage());
        fm.setImportDefaultValue(imp.defaultValue());
        fm.setImportUnique(imp.unique());
        fm.setImportDuplicateMessage(imp.duplicateMessage());
        fm.setImportDictCode(imp.dictCode());
        fm.setImportAllowedValues(imp.allowedValues());
        fm.setImportMinValue(imp.minValue());
        fm.setImportMaxValue(imp.maxValue());
        fm.setImportMinLength(imp.minLength());
        fm.setImportMaxLength(imp.maxLength());
        fm.setImportDateFormat(imp.dateFormat());
        fm.setImportIgnoreCase(imp.ignoreCase());
        fm.setImportTrim(imp.trim());
        fm.setImportErrorPolicy(imp.errorPolicy().name());
    }
    
    private void applyDataforgeDto(Field f, FieldMeta fm) {
        DataforgeDto dto = f.getAnnotation(DataforgeDto.class);
        if (dto == null) {
            return;
        }
        if (dto.include().length > 0) {
            fm.setDtoIncludeTypes(Arrays.stream(dto.include()).map(DtoType::name).toArray(String[]::new));
        }
        if (dto.exclude().length > 0) {
            fm.setDtoExcludeTypes(Arrays.stream(dto.exclude()).map(DtoType::name).toArray(String[]::new));
        }
        fm.setDtoFieldName(dto.fieldName());
        fm.setDtoFieldType(dto.fieldType());
        if (dto.converter() != null && dto.converter() != void.class) {
            fm.setDtoConverterClassName(dto.converter().getName());
        }
        fm.setDtoFormat(dto.format());
        fm.setDtoValidationGroups(dto.validationGroups());
        fm.setDtoReadOnly(dto.readOnly());
        fm.setDtoWriteOnly(dto.writeOnly());
        fm.setDtoCreateOnly(dto.createOnly());
        fm.setDtoUpdateOnly(dto.updateOnly());
        fm.setDtoQueryOnly(dto.queryOnly());
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
        meta.setRequireId(ann.requireId());
        if (ann.permissions() != null && ann.permissions().length > 0) {
            meta.setPermissions(Arrays.stream(ann.permissions()).collect(Collectors.toList()));
        }
        return meta;
    }
    
    private String pathSegmentFor(Class<?> entityClass) {
        DataforgeEntity pe = entityClass.getAnnotation(DataforgeEntity.class);
        if (pe != null && StringUtils.hasText(pe.pathSegment())) {
            return pe.pathSegment();
        }
        return toPluralLower(entityClass.getSimpleName());
    }
}
