package com.lrenyi.template.dataforge.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * 编译期根据 @DataforgeEntity 生成 CRUD 用 DTO 和 Mapper。
 * DTO 为 Java Record，Mapper 为 MapStruct 接口。
 */
@SupportedAnnotationTypes("com.lrenyi.template.dataforge.annotation.DataforgeEntity")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class DtoGeneratorProcessor extends AbstractProcessor {
    
    private static final String DTO_TYPE_CREATE = "CREATE";
    private static final String ATTR_INCLUDE = "include";
    private static final String ATTR_PARENT_FIELD_NAME = "parentFieldName";
    private static final String ATTR_VALUE = "value";
    private static final String ANN_DATAFORGE_DTO = "com.lrenyi.template.dataforge.annotation.DataforgeDto";
    private static final String ANN_DATAFORGE_DTO_LIST = "com.lrenyi.template.dataforge.annotation.DataforgeDto$List";
    private static final String ANN_DATAFORGE_FIELD = "com.lrenyi.template.dataforge.annotation.DataforgeField";

    private static boolean hasColumnNullableFalse(VariableElement field) {
        for (var mirror : field.getAnnotationMirrors()) {
            if ("jakarta.persistence.Column".equals(mirror.getAnnotationType().toString())) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    if ("nullable".equals(entry.getKey().getSimpleName().toString())) {
                        Object v = entry.getValue().getValue();
                        return Boolean.FALSE.equals(v);
                    }
                }
                return false;
            }
        }
        return false;
    }
    
    private static int getColumnLength(VariableElement field) {
        for (var mirror : field.getAnnotationMirrors()) {
            if ("jakarta.persistence.Column".equals(mirror.getAnnotationType().toString())) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    if ("length".equals(entry.getKey().getSimpleName().toString())) {
                        Object v = entry.getValue().getValue();
                        return v instanceof Integer i ? i : 0;
                    }
                }
            }
        }
        return 0;
    }
    
    private static String fieldAnnotations(FieldSpec f) {
        StringBuilder sb = new StringBuilder();
        String groups = "";
        if (!f.validationGroups.isEmpty()) {
            groups = ", groups = {" + String.join(", ", f.validationGroups) + ".class}";
        }
        
        if (f.notNull()) {
            // Remove leading comma if present in groups when appending to NotNull
            String groupParam = groups.isEmpty() ? "" : "(" + groups.substring(2) + ")";
            sb.append("    @jakarta.validation.constraints.NotNull").append(groupParam).append("\n");
        }
        if (f.maxSize() > 0 && "String".equals(f.typeName())) {
            sb.append("    @jakarta.validation.constraints.Size(max = ")
              .append(f.maxSize())
              .append(groups)
              .append(")\n");
        }
        if (f.format() != null && !f.format().isEmpty()) {
            sb.append("    @com.fasterxml.jackson.annotation.JsonFormat(pattern = \"")
              .append(f.format())
              .append("\", timezone = \"GMT+8\")\n");
        }
        return sb.toString();
    }
    
    private static Set<String> getDtoTypeNamesFromMirrors(VariableElement field) {
        AnnotationMirror mirror = findAnnotationMirror(field);
        if (mirror == null) {
            return new HashSet<>();
        }
        
        List<? extends AnnotationValue> values = getAnnotationValues(mirror);
        Set<String> out = new HashSet<>();
        for (AnnotationValue av : values) {
            Object ev = av.getValue();
            if (ev instanceof Element el) {
                out.add(el.getSimpleName().toString());
            } else if (ev instanceof String s) {
                out.add(s);
            }
        }
        return out;
    }
    
    private static AnnotationMirror findAnnotationMirror(VariableElement field) {
        for (var mirror : field.getAnnotationMirrors()) {
            if (ANN_DATAFORGE_DTO.equals(mirror.getAnnotationType().toString())) {
                return mirror;
            }
        }
        return null;
    }
    
    private static List<? extends AnnotationValue> getAnnotationValues(AnnotationMirror mirror) {
        for (var entry : mirror.getElementValues().entrySet()) {
            if (ATTR_INCLUDE.equals(entry.getKey().getSimpleName().toString())) {
                Object val = entry.getValue().getValue();
                if (val instanceof List<?> list) {
                    List<AnnotationValue> out = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof AnnotationValue av) {
                            out.add(av);
                        }
                    }
                    return out;
                }
                return List.of();
            }
        }
        return List.of();
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        boolean claimed = false;
        for (TypeElement ann : annotations) {
            if (!"com.lrenyi.template.dataforge.annotation.DataforgeEntity".equals(ann.getQualifiedName().toString())) {
                continue;
            }
            for (Element el : roundEnv.getElementsAnnotatedWith(ann)) {
                if (el instanceof TypeElement type && shouldGenerateDtos(type)) {
                    generateDtos(type);
                    claimed = true;
                }
            }
        }
        return claimed;
    }
    
    private boolean shouldGenerateDtos(TypeElement type) {
        for (var am : type.getAnnotationMirrors()) {
            if (!"com.lrenyi.template.dataforge.annotation.DataforgeEntity".equals(am.getAnnotationType().toString())) {
                continue;
            }
            for (var entry : am.getElementValues().entrySet()) {
                if ("generateDtos".equals(entry.getKey().getSimpleName().toString())) {
                    Object v = entry.getValue().getValue();
                    if (v instanceof Boolean b) {
                        return b;
                    }
                }
            }
            return true;
        }
        return true;
    }
    
    private void generateDtos(TypeElement entityType) {
        String pkg = processingEnv.getElementUtils().getPackageOf(entityType).getQualifiedName().toString();
        String simpleName = entityType.getSimpleName().toString();
        List<FieldSpec> allFields = collectFields(entityType);
        
        Set<String> strictTypes = new HashSet<>();
        for (FieldSpec f : allFields) {
            strictTypes.addAll(f.includeTypes());
        }
        
        List<FieldSpec> createFields = allFields.stream()
                                                .filter(f -> !"id".equals(f.name) && shouldInclude(f,
                                                                                                   DTO_TYPE_CREATE,
                                                                                                   strictTypes
                                                ))
                                                .map(f -> withGroup(f,
                                                                    "com.lrenyi.template.dataforge.validation.Create"
                                                ))
                                                .toList();
        List<FieldSpec> updateFields = allFields.stream()
                                                .filter(f -> !"id".equals(f.name) && shouldInclude(f,
                                                                                                   "UPDATE",
                                                                                                   strictTypes
                                                ))
                                                .map(f -> withGroup(f,
                                                                    "com.lrenyi.template.dataforge.validation.Update"
                                                ))
                                                .toList();
        List<FieldSpec> responseFields = allFields.stream()
                                                  .filter(f -> "id".equals(f.name) || shouldInclude(f,
                                                                                                    "RESPONSE",
                                                                                                    strictTypes
                                                  ))
                                                  .toList();
        List<FieldSpec> pageResponseFields = allFields.stream()
                                                      .filter(f -> "id".equals(f.name) || shouldInclude(f,
                                                                                                        "PAGE_RESPONSE",
                                                                                                        strictTypes
                                                      ))
                                                      .toList();
        
        String dtoPackage = pkg + ".dto";
        String mapperPackage = pkg + ".mapper";
        
        String createDto = simpleName + "CreateDTO";
        String updateDto = simpleName + "UpdateDTO";
        String responseDto = simpleName + "ResponseDTO";
        String pageResponseDto = simpleName + "PageResponseDTO";
        String mapperName = simpleName + "Mapper";
        
        try {
            writeRecord(dtoPackage, createDto, createFields);
            writeRecord(dtoPackage, updateDto, updateFields);
            writeRecord(dtoPackage, responseDto, responseFields);
            writeRecord(dtoPackage, pageResponseDto, pageResponseFields);
            
            MapperParams params = new MapperParams(mapperPackage,
                                                   mapperName,
                                                   entityType.getQualifiedName().toString(),
                                                   dtoPackage,
                                                   createDto,
                                                   updateDto,
                                                   responseDto,
                                                   pageResponseDto
            );
            writeMapper(params);
        } catch (IOException e) {
            processingEnv.getMessager()
                         .printMessage(Diagnostic.Kind.ERROR, "Failed to generate DTOs: " + e.getMessage());
        }
    }
    
    private FieldSpec withGroup(FieldSpec f, String group) {
        Set<String> newGroups = new HashSet<>(f.validationGroups);
        newGroups.add(group);
        return new FieldSpec(f.typeName, f.name, f.includeTypes, f.notNull, f.maxSize, f.format, newGroups);
    }
    
    private boolean shouldInclude(FieldSpec f, String dtoTypeName, Set<String> strictTypes) {
        if (!f.includeTypes().isEmpty()) {
            return f.includeTypes().contains(dtoTypeName);
        }
        
        if (strictTypes.contains(dtoTypeName)) {
            return false;
        }
        
        if ("PAGE_RESPONSE".equals(dtoTypeName)) {
            Set<String> excludedByDefault =
                    Set.of("createTime", "updateTime", "createBy", "updateBy", "deleted", "version", "remark");
            return !excludedByDefault.contains(f.name);
        }
        return true;
    }
    
    private List<FieldSpec> collectFields(TypeElement entityType) {
        List<FieldSpec> list = new ArrayList<>();
        TypeMirror entityTypeMirror = entityType.asType();
        if (entityTypeMirror.getKind() == TypeKind.DECLARED) {
            collectFieldsRecursive(entityType, entityType, (DeclaredType) entityTypeMirror, list);
        } else {
            collectFieldsRecursive(entityType, entityType, null, list);
        }
        return list;
    }
    
    private void collectFieldsRecursive(TypeElement entityType,
            TypeElement type,
            DeclaredType typeInContext,
            List<FieldSpec> list) {
        TypeElement superTe = getSuperTypeElement(type);
        if (superTe != null && !"java.lang.Object".equals(superTe.getQualifiedName().toString())) {
            collectFieldsRecursive(entityType, superTe, (DeclaredType) superTe.asType(), list);
        }
        Types typeUtils = processingEnv.getTypeUtils();
        for (Element member : type.getEnclosedElements()) {
            if (member instanceof VariableElement field && member.getKind().isField()) {
                if (field.getModifiers().contains(Modifier.STATIC) || field.getModifiers().contains(Modifier.FINAL)) {
                    continue;
                }
                addFieldSpec(entityType, field, typeInContext, typeUtils, list);
            }
        }
    }
    
    private TypeElement getSuperTypeElement(TypeElement type) {
        TypeMirror superType = type.getSuperclass();
        if (superType.getKind() == TypeKind.DECLARED) {
            Element superEl = ((DeclaredType) superType).asElement();
            if (superEl instanceof TypeElement te) {
                return te;
            }
        }
        return null;
    }
    
    private void addFieldSpec(TypeElement entityType,
            VariableElement field,
            DeclaredType typeInContext,
            Types typeUtils,
            List<FieldSpec> list) {
        TypeMirror fieldType = typeInContext != null ? typeUtils.asMemberOf(typeInContext, field) : field.asType();
        String typeName = toSourceTypeName(fieldType);
        String name = field.getSimpleName().toString();
        Set<String> includeTypes = getDtoTypeNamesFromMirrors(field);
        Set<String> parentOverride = getParentFieldOverrideIncludeTypes(entityType, name);
        if (!parentOverride.isEmpty()) {
            includeTypes = parentOverride;
        }
        
        Set<String> validationGroups = new HashSet<>();
        
        boolean notNull = hasColumnNullableFalse(field);
        int maxSize = getColumnLength(field);
        String format = getFormat(field);
        list.add(new FieldSpec(typeName, name, includeTypes, notNull, maxSize, format, validationGroups));
    }
    
    private static String getFormat(VariableElement field) {
        String fromField = findFormatInAnnotation(field, ANN_DATAFORGE_FIELD);
        if (fromField != null) {
            return fromField;
        }
        return findFormatInAnnotation(field, ANN_DATAFORGE_DTO);
    }
    
    private static String findFormatInAnnotation(VariableElement field, String annotationType) {
        for (var mirror : field.getAnnotationMirrors()) {
            if (!annotationType.equals(mirror.getAnnotationType().toString())) {
                continue;
            }
            String format = extractFormatFromMirror(mirror);
            if (format != null) {
                return format;
            }
        }
        return null;
    }
    
    private static String extractFormatFromMirror(AnnotationMirror mirror) {
        for (var entry : mirror.getElementValues().entrySet()) {
            if (!"format".equals(entry.getKey().getSimpleName().toString())) {
                continue;
            }
            Object v = entry.getValue().getValue();
            return (v instanceof String s && !s.isEmpty()) ? s : null;
        }
        return null;
    }
    
    /** 解析 @DataforgeDto 的 parentFieldName 与 include 属性 */
    private static DataforgeDtoAttrs parseDataforgeDtoAttrs(AnnotationMirror mirror) {
        String parentFieldName = null;
        List<String> includeNames = new ArrayList<>();
        for (var entry : mirror.getElementValues().entrySet()) {
            String key = entry.getKey().getSimpleName().toString();
            if (ATTR_PARENT_FIELD_NAME.equals(key)) {
                Object v = entry.getValue().getValue();
                parentFieldName = v instanceof String s ? s : null;
            } else if (ATTR_INCLUDE.equals(key)) {
                includeNames.addAll(extractIncludeNamesFromValue(entry.getValue()));
            }
        }
        return new DataforgeDtoAttrs(parentFieldName, includeNames);
    }
    
    private static List<String> extractIncludeNamesFromValue(AnnotationValue annotationValue) {
        List<String> names = new ArrayList<>();
        Object val = annotationValue.getValue();
        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof AnnotationValue av) {
                    Object ev = av.getValue();
                    if (ev instanceof Element el) {
                        names.add(el.getSimpleName().toString());
                    }
                }
            }
        }
        return names;
    }
    
    /**
     * 从实体类上的 {@code @DataforgeDto(parentFieldName="xxx", include=...)} 获取父类字段的 DTO 覆盖配置。
     * 仅当字段来自父类（type != entityType）时由调用方传入的 entityType 用于查找覆盖。
     */
    private Set<String> getParentFieldOverrideIncludeTypes(TypeElement entityType, String fieldName) {
        for (var mirror : entityType.getAnnotationMirrors()) {
            String annType = mirror.getAnnotationType().toString();
            Set<String> found = switch (annType) {
                case ANN_DATAFORGE_DTO -> tryMatchFromDataforgeDtoMirror(mirror, fieldName);
                case ANN_DATAFORGE_DTO_LIST -> findOverrideFromDataforgeDtoList(mirror, fieldName);
                default -> Set.of();
            };
            if (!found.isEmpty()) {
                return found;
            }
        }
        return Set.of();
    }
    
    private Set<String> tryMatchFromDataforgeDtoMirror(AnnotationMirror mirror, String fieldName) {
        DataforgeDtoAttrs attrs = parseDataforgeDtoAttrs(mirror);
        if (fieldName.equals(attrs.parentFieldName()) && !attrs.includeNames().isEmpty()) {
            return new HashSet<>(attrs.includeNames());
        }
        return Set.of();
    }
    
    private Set<String> findOverrideFromDataforgeDtoList(AnnotationMirror listMirror, String fieldName) {
        List<?> valueList = extractValueList(listMirror);
        return valueList != null ? findFirstMatchInNestedMirrors(valueList, fieldName) : Set.of();
    }
    
    private List<?> extractValueList(AnnotationMirror listMirror) {
        for (var entry : listMirror.getElementValues().entrySet()) {
            if (ATTR_VALUE.equals(entry.getKey().getSimpleName().toString())) {
                Object val = entry.getValue().getValue();
                return val instanceof List<?> list ? list : null;
            }
        }
        return List.of();
    }
    
    private Set<String> findFirstMatchInNestedMirrors(List<?> valueList, String fieldName) {
        for (Object item : valueList) {
            if (item instanceof AnnotationValue av) {
                Object ev = av.getValue();
                if (ev instanceof AnnotationMirror nestedMirror) {
                    Set<String> fromNested = tryMatchFromDataforgeDtoMirror(nestedMirror, fieldName);
                    if (!fromNested.isEmpty()) {
                        return fromNested;
                    }
                }
            }
        }
        return Set.of();
    }
    
    private record DataforgeDtoAttrs(String parentFieldName, List<String> includeNames) {}
    
    private String toSourceTypeName(TypeMirror mirror) {
        TypeKind kind = mirror.getKind();
        switch (kind) {
            case LONG:
                return "Long";
            case INT:
                return "Integer";
            case BOOLEAN:
                return "Boolean";
            case TYPEVAR: {
                TypeMirror upper = ((TypeVariable) mirror).getUpperBound();
                if (upper.getKind() == TypeKind.DECLARED && "java.io.Serializable".equals(upper.toString())) {
                    return "Long";
                }
                return toSourceTypeName(upper);
            }
            case DECLARED: {
                String q = mirror.toString();
                if (q.startsWith("java.lang.")) {
                    return q.substring("java.lang.".length());
                }
                return q;
            }
            default:
                return mirror.toString();
        }
    }
    
    private void writeRecord(String pkg, String className, List<FieldSpec> fields) throws IOException {
        JavaFileObject fo = processingEnv.getFiler().createSourceFile(pkg + "." + className);
        try (Writer w = fo.openWriter()) {
            w.write("package " + pkg + ";\n\n");
            w.write("/**\n * 自动生成的 DTO Record，请勿手改。\n */\n");
            w.write("public record " + className + "(\n");
            for (int i = 0; i < fields.size(); i++) {
                FieldSpec f = fields.get(i);
                String annotations = fieldAnnotations(f);
                if (!annotations.isBlank()) {
                    w.write(annotations);
                }
                w.write("    " + f.typeName + " " + f.name);
                if (i < fields.size() - 1) {
                    w.write(",\n\n");
                } else {
                    w.write("\n");
                }
            }
            w.write(") {}\n");
        }
    }
    
    private void writeMapper(MapperParams params) throws IOException {
        JavaFileObject fo = processingEnv.getFiler().createSourceFile(params.pkg + "." + params.className);
        try (Writer w = fo.openWriter()) {
            w.write("package " + params.pkg + ";\n\n");
            w.write("import " + params.entityType + ";\n");
            w.write("import " + params.dtoPkg + ".*;\n");
            w.write("import com.lrenyi.template.dataforge.mapper.BaseMapper;\n");
            w.write("import org.mapstruct.Mapper;\n");
            w.write("import org.mapstruct.ReportingPolicy;\n\n");
            
            w.write("/**\n * 自动生成的 MapStruct Mapper，请勿手改。\n */\n");
            w.write("@Mapper(componentModel = \"spring\", unmappedTargetPolicy = ReportingPolicy.IGNORE)\n");
            w.write("public interface " + params.className + " extends BaseMapper<" + simpleName(params.entityType)
                            + ", " + params.createDto + ", " + params.updateDto + ", " + params.responseDto + ", "
                            + params.pageResponseDto + "> {\n");
            w.write("}\n");
        }
    }
    
    private record MapperParams(String pkg, String className, String entityType, String dtoPkg, String createDto,
                                String updateDto, String responseDto, String pageResponseDto) {}
    
    private String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }
    
    private record FieldSpec(String typeName, String name, Set<String> includeTypes, boolean notNull, int maxSize,
                             String format,
                             Set<String> validationGroups) {
        private FieldSpec(String typeName,
                String name,
                Set<String> includeTypes,
                boolean notNull,
                int maxSize, String format,
                Set<String> validationGroups) {
            this.typeName = typeName;
            this.name = name;
            this.includeTypes = includeTypes != null ? includeTypes : Set.of();
            this.notNull = notNull;
            this.maxSize = Math.max(maxSize, 0);
            this.format = format;
            this.validationGroups = validationGroups != null ? validationGroups : Set.of();
        }
    }
}
