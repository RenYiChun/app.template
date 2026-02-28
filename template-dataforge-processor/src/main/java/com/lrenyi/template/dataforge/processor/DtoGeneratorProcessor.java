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
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationMirror;
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
 * 编译期根据 @DataforgeEntity 生成 CRUD 用 DTO：CreateDTO、UpdateDTO、ResponseDTO、PageResponseDTO。
 * 生成到实体所在包的 dto 子包下。不依赖 template-dataforge 类，仅通过注解镜像按名读取，便于 IDE/Maven 加载。
 */
@SupportedAnnotationTypes("com.lrenyi.template.dataforge.annotation.DataforgeEntity")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class DtoGeneratorProcessor extends AbstractProcessor {
    
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
        if (f.notNull()) {
            sb.append("    @jakarta.validation.constraints.NotNull\n");
        }
        if (f.maxSize() > 0 && "String".equals(f.typeName())) {
            sb.append("    @jakarta.validation.constraints.Size(max = ").append(f.maxSize()).append(")\n");
        }
        return sb.toString();
    }
    
    private static String cap(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    
    private static Set<String> getDtoTypeNamesFromMirrors(VariableElement field, String annotationFqn, String elementName) {
        AnnotationMirror mirror = findAnnotationMirror(field, annotationFqn);
        if (mirror == null) {
            return Set.of();
        }
        List<? extends AnnotationValue> values = getAnnotationValues(mirror, elementName);
        Set<String> out = new HashSet<>();
        for (AnnotationValue av : values) {
            Object ev = av.getValue();
            if (ev instanceof Element el) {
                out.add(el.getSimpleName().toString());
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
        List<FieldSpec> createFields =
                allFields.stream().filter(f -> !"id".equals(f.name) && shouldInclude(f, "CREATE")).toList();
        List<FieldSpec> updateFields =
                allFields.stream().filter(f -> !"id".equals(f.name) && shouldInclude(f, "UPDATE")).toList();
        List<FieldSpec> responseFields = allFields.stream().filter(f -> shouldInclude(f, "RESPONSE")).toList();
        List<FieldSpec> pageResponseFields = allFields.stream()
                                                      .filter(f -> "id".equals(f.name) || f.includeTypes()
                                                                                           .contains("PAGE_RESPONSE"))
                                                      .toList();
        
        String dtoPackage = pkg + ".dto";
        try {
            writeClass(dtoPackage, simpleName + "CreateDTO", createFields);
            writeClass(dtoPackage, simpleName + "UpdateDTO", updateFields);
            writeClass(dtoPackage, simpleName + "ResponseDTO", responseFields);
            writeClass(dtoPackage, simpleName + "PageResponseDTO", pageResponseFields);
        } catch (IOException e) {
            processingEnv.getMessager()
                         .printMessage(Diagnostic.Kind.ERROR, "Failed to generate DTOs: " + e.getMessage());
        }
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
    
    /**
     * 在 typeInContext 上下文中收集字段，使基类泛型（如 BaseEntity 的 ID）解析为实际类型（如 Long）。
     */
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
    
    private void addFieldSpec(VariableElement field, DeclaredType typeInContext, Types typeUtils, List<FieldSpec> list) {
        TypeMirror fieldType = typeInContext != null ? typeUtils.asMemberOf(typeInContext, field) : field.asType();
        String typeName = toSourceTypeName(fieldType);
        String name = field.getSimpleName().toString();
        Set<String> includeTypes = getDtoTypeNamesFromMirrors(field,
                "com.lrenyi.template.dataforge.annotation.DataforgeDto",
                "include");
        Set<String> excludeTypes = getDtoTypeNamesFromMirrors(field,
                "com.lrenyi.template.dataforge.annotation.DataforgeDto",
                "exclude");
        Set<String> legacyExcludeFrom = getDtoTypeNamesFromMirrors(field,
                "com.lrenyi.template.dataforge.annotation.DtoExcludeFrom",
                "value");
        Set<String> finalExcludeTypes = excludeTypes.isEmpty() ? legacyExcludeFrom : excludeTypes;
        boolean notNull = hasColumnNullableFalse(field);
        int maxSize = getColumnLength(field);
        list.add(new FieldSpec(typeName, name, includeTypes, finalExcludeTypes, notNull, maxSize));
    }
    
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
    
    private void writeClass(String pkg, String className, List<FieldSpec> fields) throws IOException {
        JavaFileObject fo = processingEnv.getFiler().createSourceFile(pkg + "." + className);
        try (Writer w = fo.openWriter()) {
            w.write("package " + pkg + ";\n\n");
            w.write("/**\n * 自动生成，请勿手改。\n */\n");
            w.write("public class " + className + " {\n\n");
            for (FieldSpec f : fields) {
                w.write(fieldAnnotations(f));
                w.write("    private " + f.typeName + " " + f.name + ";\n\n");
                w.write("    public " + f.typeName + " get" + cap(f.name) + "() {\n        return " + f.name
                                + ";\n    }\n\n");
                w.write("    public void set" + cap(f.name) + "(" + f.typeName + " " + f.name + ") {\n        this."
                                + f.name + " = " + f.name + ";\n    }\n\n");
            }
            w.write("}\n");
        }
    }
    
    private record FieldSpec(String typeName, String name, Set<String> includeTypes, Set<String> excludeTypes,
                             boolean notNull, int maxSize) {
        private FieldSpec(String typeName,
                String name,
                Set<String> includeTypes,
                Set<String> excludeTypes,
                boolean notNull,
                int maxSize) {
            this.typeName = typeName;
            this.name = name;
            this.includeTypes = includeTypes != null ? includeTypes : Set.of();
            this.excludeTypes = excludeTypes != null ? excludeTypes : Set.of();
            this.notNull = notNull;
            this.maxSize = Math.max(maxSize, 0);
        }
        
        Set<String> excludeFrom() {
            return excludeTypes;
        }
    }
}
