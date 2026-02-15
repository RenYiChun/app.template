package com.lrenyi.template.platform.support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import com.lrenyi.template.platform.action.EntityActionExecutor;
import com.lrenyi.template.platform.annotation.EntityAction;
import com.lrenyi.template.platform.annotation.ExportConverter;
import com.lrenyi.template.platform.annotation.ExportExclude;
import com.lrenyi.template.platform.annotation.PlatformEntity;
import com.lrenyi.template.platform.meta.ActionMeta;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.meta.FieldMeta;
import com.lrenyi.template.platform.registry.ActionRegistry;
import com.lrenyi.template.platform.registry.EntityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 启动时扫描 @PlatformEntity 与 @EntityAction，构建 EntityMeta/ActionMeta 并注册。
 */
public class MetaScanner {
    
    private static final Logger log = LoggerFactory.getLogger(MetaScanner.class);
    
    private final EntityRegistry entityRegistry;
    private final ActionRegistry actionRegistry;
    private final String basePackage;
    
    public MetaScanner(EntityRegistry entityRegistry, ActionRegistry actionRegistry, String basePackage) {
        this.entityRegistry = entityRegistry;
        this.actionRegistry = actionRegistry;
        this.basePackage = basePackage == null || basePackage.isEmpty() ? "" : basePackage;
    }
    
    /**
     * 扫描 classpath 中带 @PlatformEntity 的类并注册 EntityMeta；注册带 @EntityAction 的 Action 执行器。
     * basePackage 可为逗号分隔的多个包。
     */
    public void scanAndRegister(List<Object> actionExecutorBeans) {
        String[] packages = basePackage.isEmpty() ? new String[0] : basePackage.split(",");
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(PlatformEntity.class));
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
                    PlatformEntity ann = clazz.getAnnotation(PlatformEntity.class);
                    if (ann == null) {
                        continue;
                    }
                    EntityMeta meta = buildEntityMeta(clazz, ann);
                    meta.setEntityClass(clazz);
                    entityRegistry.register(meta);
                    log.debug("Registered entity: {}", meta.getPathSegment());
                } catch (ClassNotFoundException e) {
                    log.warn("Cannot load entity class: {}", className, e);
                }
            }
        }
        
        if (actionExecutorBeans != null) {
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
                ActionMeta actionMeta = buildActionMeta(ann, entityPathSegment);
                actionRegistry.register(entityPathSegment, ann.actionName(), actionMeta, executor);
                EntityMeta entityMeta = entityRegistry.getByEntityName(ann.entity().getSimpleName());
                if (entityMeta != null) {
                    entityMeta.getActions().add(actionMeta);
                }
                log.debug("Registered action: {}:{}", entityPathSegment, ann.actionName());
            }
        }
    }
    
    private EntityMeta buildEntityMeta(Class<?> clazz, PlatformEntity ann) {
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
        meta.setPermissionCreate(ann.permissionCreate());
        meta.setPermissionRead(ann.permissionRead());
        meta.setPermissionUpdate(ann.permissionUpdate());
        meta.setPermissionDelete(ann.permissionDelete());
        meta.setFields(buildFieldMetas(clazz));
        return meta;
    }
    
    private List<FieldMeta> buildFieldMetas(Class<?> clazz) {
        List<FieldMeta> list = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            FieldMeta fm = new FieldMeta();
            fm.setName(f.getName());
            fm.setType(f.getType().getSimpleName());
            fm.setColumnName(toSnakeCase(f.getName()));
            fm.setPrimaryKey(
                    "id".equalsIgnoreCase(f.getName()) && (f.getType() == Long.class || f.getType() == long.class));
            fm.setRequired(fm.isPrimaryKey());
            fm.setExportExcluded(f.getAnnotation(ExportExclude.class) != null);
            ExportConverter exportConverter = f.getAnnotation(ExportConverter.class);
            if (exportConverter != null) {
                fm.setExportConverterClassName(exportConverter.value().getName());
            }
            list.add(fm);
        }
        return list;
    }
    
    private ActionMeta buildActionMeta(EntityAction ann, String entityPathSegment) {
        ActionMeta meta = new ActionMeta();
        meta.setActionName(ann.actionName());
        meta.setEntityName(ann.entity().getSimpleName());
        meta.setRequestType(ann.requestType());
        meta.setResponseType(ann.responseType());
        meta.setSummary(ann.summary());
        meta.setDescription(ann.description());
        if (ann.permissions() != null && ann.permissions().length > 0) {
            meta.setPermissions(Arrays.stream(ann.permissions()).collect(Collectors.toList()));
        }
        return meta;
    }
    
    private String pathSegmentFor(Class<?> entityClass) {
        PlatformEntity pe = entityClass.getAnnotation(PlatformEntity.class);
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
