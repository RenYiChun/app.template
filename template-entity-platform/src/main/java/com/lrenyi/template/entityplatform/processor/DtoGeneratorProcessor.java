package com.lrenyi.template.entityplatform.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import com.lrenyi.template.entityplatform.annotation.DtoExcludeFrom;
import com.lrenyi.template.entityplatform.annotation.DtoType;
import com.lrenyi.template.entityplatform.annotation.PlatformEntity;

/**
 * 编译期根据 @PlatformEntity 生成 CRUD 用 DTO：CreateDTO（请求-创建）、UpdateDTO（请求-更新）、ResponseDTO（响应 data）。
 * 生成到实体所在包的 dto 子包下。
 */
@SupportedAnnotationTypes("com.lrenyi.template.entityplatform.annotation.PlatformEntity")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class DtoGeneratorProcessor extends AbstractProcessor {
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (TypeElement ann : annotations) {
            if (!"com.lrenyi.template.entityplatform.annotation.PlatformEntity".equals(ann.getQualifiedName()
                                                                                          .toString())) {
                continue;
            }
            for (Element el : roundEnv.getElementsAnnotatedWith(ann)) {
                if (el instanceof TypeElement type) {
                    PlatformEntity pe = type.getAnnotation(PlatformEntity.class);
                    if (pe != null && pe.generateDtos()) {
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
                                                .filter(f -> !"id".equals(f.name)
                                                        && !f.excludeFrom.contains(DtoType.CREATE))
                                                .collect(Collectors.toList());
        List<FieldSpec> updateFields = allFields.stream()
                                                .filter(f -> !"id".equals(f.name)
                                                        && !f.excludeFrom.contains(DtoType.UPDATE))
                                                .collect(Collectors.toList());
        List<FieldSpec> responseFields =
                allFields.stream().filter(f -> !f.excludeFrom.contains(DtoType.RESPONSE)).collect(Collectors.toList());
        
        String dtoPackage = pkg + ".dto";
        try {
            writeClass(dtoPackage, simpleName + "CreateDTO", createFields);
            writeClass(dtoPackage, simpleName + "UpdateDTO", updateFields);
            writeClass(dtoPackage, simpleName + "ResponseDTO", responseFields);
        } catch (IOException e) {
            processingEnv.getMessager()
                         .printMessage(javax.tools.Diagnostic.Kind.ERROR, "Failed to generate DTOs: " + e.getMessage());
        }
    }
    
    private List<FieldSpec> collectFields(TypeElement type) {
        List<FieldSpec> list = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member instanceof VariableElement field && member.getKind().isField()) {
                if (field.getModifiers().contains(Modifier.STATIC) || field.getModifiers().contains(Modifier.FINAL)) {
                    continue;
                }
                String typeName = toSourceTypeName(field.asType());
                String name = field.getSimpleName().toString();
                Set<DtoType> excludeFrom = new HashSet<>();
                DtoExcludeFrom ann = field.getAnnotation(DtoExcludeFrom.class);
                if (ann != null && ann.value() != null && ann.value().length > 0) {
                    Collections.addAll(excludeFrom, ann.value());
                }
                list.add(new FieldSpec(typeName, name, excludeFrom));
            }
        }
        return list;
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
                w.write("    private " + f.typeName + " " + f.name + ";\n\n");
                w.write("    public " + f.typeName + " get" + cap(f.name) + "() {\n        return " + f.name
                                + ";\n    }\n\n");
                w.write("    public void set" + cap(f.name) + "(" + f.typeName + " " + f.name + ") {\n        this."
                                + f.name + " = " + f.name + ";\n    }\n\n");
            }
            w.write("}\n");
        }
    }
    
    private static String cap(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    
    private record FieldSpec(String typeName, String name, Set<DtoType> excludeFrom) {
        private FieldSpec(String typeName, String name, Set<DtoType> excludeFrom) {
            this.typeName = typeName;
            this.name = name;
            this.excludeFrom = excludeFrom != null ? excludeFrom : Set.of();
        }
    }
}
