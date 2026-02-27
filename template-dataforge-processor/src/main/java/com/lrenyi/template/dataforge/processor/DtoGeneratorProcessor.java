package com.lrenyi.template.dataforge.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
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
import javax.tools.JavaFileObject;

/**
 * 编译期根据 @DataforgeEntity 生成 CRUD 用 DTO：CreateDTO、UpdateDTO、ResponseDTO、PageResponseDTO。
 * 生成到实体所在包的 dto 子包下。不依赖 template-dataforge 类，仅通过注解镜像按名读取，便于 IDE/Maven 加载。
 */
@SupportedAnnotationTypes("com.lrenyi.template.dataforge.annotation.DataforgeEntity")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class DtoGeneratorProcessor extends AbstractProcessor {
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (TypeElement ann : annotations) {
            if (!"com.lrenyi.template.dataforge.annotation.DataforgeEntity".equals(ann.getQualifiedName()
                                                                                          .toString())) {
                continue;
            }
            for (Element el : roundEnv.getElementsAnnotatedWith(ann)) {
                if (el instanceof TypeElement type) {
                    // 通过反射读取 DataforgeEntity 注解的 generateDtos 属性
                    try {
                        Object generateDtos = type.getAnnotationMirrors().stream()
                            .filter(am -> am.getAnnotationType().toString().equals("com.lrenyi.template.dataforge.annotation.DataforgeEntity"))
                            .flatMap(am -> am.getElementValues().entrySet().stream())
                            .filter(e -> e.getKey().getSimpleName().toString().equals("generateDtos"))
                            .map(e -> e.getValue().getValue())
                            .findFirst()
                            .orElse(true); // 默认为 true
                        
                        if (Boolean.TRUE.equals(generateDtos)) {
                            generateDtos(type);
                        }
                    } catch (Exception e) {
                        // 如果读取失败，默认生成
                        generateDtos(type);
                    }
                }
            }
        }
        return false;
    }
    
    private void generateDtos(TypeElement entityType) {
        String pkg = processingEnv.getElementUtils().getPackageOf(entityType).getQualifiedName().toString();
        String simpleName = entityType.getSimpleName().toString();
        List<FieldSpec> allFields = collectFields(entityType);
        List<FieldSpec> createFields = allFields.stream()
                                                .filter(f -> !"id".equals(f.name) && shouldInclude(f, "CREATE"))
                                                .collect(Collectors.toList());
        List<FieldSpec> updateFields = allFields.stream()
                                                .filter(f -> !"id".equals(f.name) && shouldInclude(f, "UPDATE"))
                                                .collect(Collectors.toList());
        List<FieldSpec> responseFields = allFields.stream()
                                                .filter(f -> shouldInclude(f, "RESPONSE"))
                                                .collect(Collectors.toList());
        List<FieldSpec> pageResponseFields = allFields.stream()
                                                .filter(f -> "id".equals(f.name) || f.includeTypes().contains("PAGE_RESPONSE"))
                                                .collect(Collectors.toList());
        
        String dtoPackage = pkg + ".dto";
        try {
            writeClass(dtoPackage, simpleName + "CreateDTO", createFields);
            writeClass(dtoPackage, simpleName + "UpdateDTO", updateFields);
            writeClass(dtoPackage, simpleName + "ResponseDTO", responseFields);
            writeClass(dtoPackage, simpleName + "PageResponseDTO", pageResponseFields);
        } catch (IOException e) {
            processingEnv.getMessager()
                         .printMessage(javax.tools.Diagnostic.Kind.ERROR, "Failed to generate DTOs: " + e.getMessage());
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
        TypeMirror superType = type.getSuperclass();
        if (superType.getKind() == TypeKind.DECLARED) {
            DeclaredType superDeclared = (DeclaredType) superType;
            Element superEl = superDeclared.asElement();
            if (superEl instanceof TypeElement superTe) {
                String superName = superTe.getQualifiedName().toString();
                if (!"java.lang.Object".equals(superName)) {
                    collectFieldsRecursive(superTe, superDeclared, list);
                }
            }
        }
        Types typeUtils = processingEnv.getTypeUtils();
        for (Element member : type.getEnclosedElements()) {
            if (member instanceof VariableElement field && member.getKind().isField()) {
                if (field.getModifiers().contains(Modifier.STATIC) || field.getModifiers().contains(Modifier.FINAL)) {
                    continue;
                }
                TypeMirror fieldType = typeInContext != null
                    ? typeUtils.asMemberOf(typeInContext, field)
                    : field.asType();
                String typeName = toSourceTypeName(fieldType);
                String name = field.getSimpleName().toString();
                Set<String> includeTypes = getDtoTypeNamesFromMirrors(field, "com.lrenyi.template.dataforge.annotation.DataforgeDto", "include");
                Set<String> excludeTypes = getDtoTypeNamesFromMirrors(field, "com.lrenyi.template.dataforge.annotation.DataforgeDto", "exclude");
                Set<String> legacyExcludeFrom = getDtoTypeNamesFromMirrors(field, "com.lrenyi.template.dataforge.annotation.DtoExcludeFrom", "value");
                Set<String> finalExcludeTypes = excludeTypes.isEmpty() ? legacyExcludeFrom : excludeTypes;
                boolean notNull = hasColumnNullableFalse(field);
                int maxSize = getColumnLength(field);
                list.add(new FieldSpec(typeName, name, includeTypes, finalExcludeTypes, notNull, maxSize));
            }
        }
    }

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
    
    private String toSourceTypeName(TypeMirror mirror) {
        if (mirror.getKind() == TypeKind.LONG) {
            return "Long";
        }
        if (mirror.getKind() == TypeKind.INT) {
            return "Integer";
        }
        if (mirror.getKind() == TypeKind.BOOLEAN) {
            return "Boolean";
        }
        if (mirror.getKind() == TypeKind.TYPEVAR) {
            TypeMirror upper = ((TypeVariable) mirror).getUpperBound();
            if (upper.getKind() == TypeKind.DECLARED
                && "java.io.Serializable".equals(upper.toString())) {
                return "Long";
            }
            return toSourceTypeName(upper);
        }
        if (mirror.getKind() == TypeKind.DECLARED) {
            String q = mirror.toString();
            if (q.startsWith("java.lang.")) {
                return q.substring("java.lang.".length());
            }
            return q;
        }
        return mirror.toString();
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
    
    /** 从字段的注解镜像中读取某注解的某属性（枚举数组），返回枚举常量名集合，如 "CREATE","PAGE_RESPONSE"。 */
    private static Set<String> getDtoTypeNamesFromMirrors(VariableElement field, String annotationFqn, String elementName) {
        Set<String> out = new HashSet<>();
        for (var mirror : field.getAnnotationMirrors()) {
            if (!annotationFqn.equals(mirror.getAnnotationType().toString())) {
                continue;
            }
            for (var entry : mirror.getElementValues().entrySet()) {
                if (!elementName.equals(entry.getKey().getSimpleName().toString())) {
                    continue;
                }
                Object val = entry.getValue().getValue();
                if (val instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof AnnotationValue av) {
                            Object ev = av.getValue();
                            if (ev instanceof Element el) {
                                out.add(el.getSimpleName().toString());
                            }
                        }
                    }
                }
                break;
            }
            break;
        }
        return out;
    }

    private record FieldSpec(String typeName, String name, Set<String> includeTypes, Set<String> excludeTypes, boolean notNull, int maxSize) {
        private FieldSpec(String typeName, String name, Set<String> includeTypes, Set<String> excludeTypes, boolean notNull, int maxSize) {
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
