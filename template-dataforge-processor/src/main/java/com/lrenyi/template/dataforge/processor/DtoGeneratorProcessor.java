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
            sb.append("    @jakarta.validation.constraints.Size(max = ").append(f.maxSize()).append(groups).append(")\n");
        }
        return sb.toString();
    }
    
    private static Object getAnnotationValue(AnnotationMirror mirror, String elementName) {
        for (var entry : mirror.getElementValues().entrySet()) {
            if (elementName.equals(entry.getKey().getSimpleName().toString())) {
                return entry.getValue().getValue();
            }
        }
        return null;
    }
    
    private static Set<String> getDtoTypeNamesFromMirrors(VariableElement field,
            String annotationFqn,
            String elementName) {
        AnnotationMirror mirror = findAnnotationMirror(field, annotationFqn);
        if (mirror == null) {
            return new HashSet<>();
        }
        // Handle boolean shortcuts
        Set<String> excludes = new HashSet<>();
        if ("exclude".equals(elementName)) {
            if (Boolean.TRUE.equals(getAnnotationValue(mirror, "readOnly"))) {
                excludes.add(DTO_TYPE_CREATE);
                excludes.add("UPDATE");
                excludes.add("BATCH_CREATE");
                excludes.add("BATCH_UPDATE");
            }
            if (Boolean.TRUE.equals(getAnnotationValue(mirror, "writeOnly"))) {
                excludes.add("RESPONSE");
            }
        }
        if ("include".equals(elementName) && Boolean.TRUE.equals(getAnnotationValue(mirror, "createOnly"))) {
            excludes.add(DTO_TYPE_CREATE);
        }

        List<? extends AnnotationValue> values = getAnnotationValues(mirror, elementName);
        Set<String> out = new HashSet<>(excludes);
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
    
    private static AnnotationMirror findAnnotationMirror(VariableElement field, String annotationFqn) {
        for (var mirror : field.getAnnotationMirrors()) {
            if (annotationFqn.equals(mirror.getAnnotationType().toString())) {
                return mirror;
            }
        }
        return null;
    }
    
    private static List<? extends AnnotationValue> getAnnotationValues(AnnotationMirror mirror, String elementName) {
        for (var entry : mirror.getElementValues().entrySet()) {
            if (elementName.equals(entry.getKey().getSimpleName().toString())) {
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
        
        List<FieldSpec> createFields = allFields.stream().filter(f -> !"id".equals(f.name) && shouldInclude(f, DTO_TYPE_CREATE)).map(f -> withGroup(f, "com.lrenyi.template.dataforge.validation.Create")).toList();
        List<FieldSpec> updateFields = allFields.stream().filter(f -> !"id".equals(f.name) && shouldInclude(f, "UPDATE")).map(f -> withGroup(f, "com.lrenyi.template.dataforge.validation.Update")).toList();
        List<FieldSpec> responseFields = allFields.stream().filter(f -> shouldInclude(f, "RESPONSE")).toList();
        List<FieldSpec> pageResponseFields = allFields.stream().filter(f -> "id".equals(f.name) || f.includeTypes().contains("PAGE_RESPONSE")).toList();
        
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
            
            MapperParams params = new MapperParams(mapperPackage, mapperName, entityType.getQualifiedName().toString(),
                    dtoPackage, createDto, updateDto, responseDto, pageResponseDto);
            writeMapper(params);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate DTOs: " + e.getMessage());
        }
    }
    
    private FieldSpec withGroup(FieldSpec f, String group) {
        Set<String> newGroups = new HashSet<>(f.validationGroups);
        newGroups.add(group);
        return new FieldSpec(f.typeName, f.name, f.includeTypes, f.excludeTypes, f.notNull, f.maxSize, newGroups);
    }
    
    private boolean shouldInclude(FieldSpec f, String dtoTypeName) {
        if (!f.includeTypes().isEmpty()) {
            return f.includeTypes().contains(dtoTypeName);
        }
        return !f.excludeFrom().contains(dtoTypeName);
    }
    
    private List<FieldSpec> collectFields(TypeElement entityType) {
        List<FieldSpec> list = new ArrayList<>();
        TypeMirror entityTypeMirror = entityType.asType();
        if (entityTypeMirror.getKind() == TypeKind.DECLARED) {
            collectFieldsRecursive(entityType, (DeclaredType) entityTypeMirror, list);
        } else {
            collectFieldsRecursive(entityType, null, list);
        }
        return list;
    }
    
    private void collectFieldsRecursive(TypeElement type, DeclaredType typeInContext, List<FieldSpec> list) {
        TypeElement superTe = getSuperTypeElement(type);
        if (superTe != null && !"java.lang.Object".equals(superTe.getQualifiedName().toString())) {
            collectFieldsRecursive(superTe, (DeclaredType) superTe.asType(), list);
        }
        Types typeUtils = processingEnv.getTypeUtils();
        for (Element member : type.getEnclosedElements()) {
            if (member instanceof VariableElement field && member.getKind().isField()) {
                if (field.getModifiers().contains(Modifier.STATIC) || field.getModifiers().contains(Modifier.FINAL)) {
                    continue;
                }
                addFieldSpec(field, typeInContext, typeUtils, list);
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
    
    private void addFieldSpec(VariableElement field,
            DeclaredType typeInContext,
            Types typeUtils,
            List<FieldSpec> list) {
        TypeMirror fieldType = typeInContext != null ? typeUtils.asMemberOf(typeInContext, field) : field.asType();
        String typeName = toSourceTypeName(fieldType);
        String name = field.getSimpleName().toString();
        Set<String> includeTypes = getDtoTypeNamesFromMirrors(field, "com.lrenyi.template.dataforge.annotation.DataforgeDto", "include");
        Set<String> excludeTypes = getDtoTypeNamesFromMirrors(field, "com.lrenyi.template.dataforge.annotation.DataforgeDto", "exclude");
        Set<String> legacyExcludeFrom = getDtoTypeNamesFromMirrors(field, "com.lrenyi.template.dataforge.annotation.DtoExcludeFrom", "value");
        Set<String> finalExcludeTypes = excludeTypes.isEmpty() ? legacyExcludeFrom : excludeTypes;
        
        Set<String> validationGroups = new HashSet<>(); 
        
        boolean notNull = hasColumnNullableFalse(field);
        int maxSize = getColumnLength(field);
        list.add(new FieldSpec(typeName, name, includeTypes, finalExcludeTypes, notNull, maxSize, validationGroups));
    }
    
    private String toSourceTypeName(TypeMirror mirror) {
        TypeKind kind = mirror.getKind();
        switch (kind) {
            case LONG: return "Long";
            case INT: return "Integer";
            case BOOLEAN: return "Boolean";
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
            default: return mirror.toString();
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
            w.write("public interface " + params.className + " extends BaseMapper<"
                    + simpleName(params.entityType) + ", " + params.createDto + ", " + params.updateDto
                    + ", " + params.responseDto + ", " + params.pageResponseDto + "> {\n");
            w.write("}\n");
        }
    }

    private record MapperParams(String pkg, String className, String entityType, String dtoPkg,
                               String createDto, String updateDto, String responseDto, String pageResponseDto) {
    }
    
    private String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }
    
    private record FieldSpec(String typeName, String name, Set<String> includeTypes, Set<String> excludeTypes,
                             boolean notNull, int maxSize, Set<String> validationGroups) {
        private FieldSpec(String typeName,
                String name,
                Set<String> includeTypes,
                Set<String> excludeTypes,
                boolean notNull,
                int maxSize,
                Set<String> validationGroups) {
            this.typeName = typeName;
            this.name = name;
            this.includeTypes = includeTypes != null ? includeTypes : Set.of();
            this.excludeTypes = excludeTypes != null ? excludeTypes : Set.of();
            this.notNull = notNull;
            this.maxSize = Math.max(maxSize, 0);
            this.validationGroups = validationGroups != null ? validationGroups : Set.of();
        }
        
        Set<String> excludeFrom() {
            return excludeTypes;
        }
    }
}
