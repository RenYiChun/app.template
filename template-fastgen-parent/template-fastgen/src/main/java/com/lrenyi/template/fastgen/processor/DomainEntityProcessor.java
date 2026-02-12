package com.lrenyi.template.fastgen.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.fastgen.annotation.Column;
import com.lrenyi.template.fastgen.annotation.Domain;
import com.lrenyi.template.fastgen.annotation.FormField;
import com.lrenyi.template.fastgen.annotation.GeneratedValue;
import com.lrenyi.template.fastgen.annotation.Id;
import com.lrenyi.template.fastgen.annotation.Page;
import com.lrenyi.template.fastgen.model.EntityMetadata;
import com.lrenyi.template.fastgen.model.FieldMetadata;
import com.lrenyi.template.fastgen.model.MetadataSnapshot;
import com.lrenyi.template.fastgen.model.PageMetadata;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * APT 处理器：收集 @Domain 与 @Page，输出 META-INF/fastgen/snapshot.json。
 */
@SupportedAnnotationTypes({
    "com.lrenyi.template.fastgen.annotation.Domain",
    "com.lrenyi.template.fastgen.annotation.Page"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class DomainEntityProcessor extends AbstractProcessor {

    private final List<EntityMetadata> entities = new ArrayList<>();
    private final List<PageMetadata> pages = new ArrayList<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writeSnapshot();
            return false;
        }

        for (TypeElement ann : annotations) {
            String name = ann.getQualifiedName().toString();
            if ("com.lrenyi.template.fastgen.annotation.Domain".equals(name)) {
                for (Element el : roundEnv.getElementsAnnotatedWith(ann)) {
                    if (el instanceof TypeElement type) {
                        entities.add(buildEntityMetadata(type));
                    }
                }
            } else if ("com.lrenyi.template.fastgen.annotation.Page".equals(name)) {
                for (Element el : roundEnv.getElementsAnnotatedWith(ann)) {
                    if (el instanceof TypeElement type) {
                        pages.add(buildPageMetadata(type));
                    }
                }
            }
        }
        return false;
    }

    private EntityMetadata buildEntityMetadata(TypeElement type) {
        EntityMetadata meta = new EntityMetadata();
        Domain ann = type.getAnnotation(Domain.class);
        if (ann == null) {
            return meta;
        }
        String simpleName = type.getSimpleName().toString();
        String pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        meta.setSimpleName(simpleName);
        meta.setPackageName(pkg);
        meta.setTableName(ann.table().isEmpty() ? camelToSnake(simpleName) : ann.table());
        meta.setDisplayName(ann.displayName().isEmpty() ? simpleName : ann.displayName());

        List<FieldMetadata> fields = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member instanceof VariableElement field) {
                fields.add(buildFieldMetadata(field, true));
            }
        }
        meta.setFields(fields);
        return meta;
    }

    private PageMetadata buildPageMetadata(TypeElement type) {
        PageMetadata meta = new PageMetadata();
        Page ann = type.getAnnotation(Page.class);
        if (ann == null) {
            return meta;
        }
        String simpleName = type.getSimpleName().toString();
        String pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        meta.setSimpleName(simpleName);
        meta.setPackageName(pkg);
        meta.setTitle(ann.title());
        meta.setLayout(ann.layout());
        meta.setPath(ann.path().isEmpty() ? "/" + simpleName : ann.path());

        List<FieldMetadata> fields = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member instanceof VariableElement field) {
                fields.add(buildFieldMetadata(field, false));
            }
        }
        meta.setFields(fields);
        return meta;
    }

    private FieldMetadata buildFieldMetadata(VariableElement field, boolean isEntity) {
        FieldMetadata meta = new FieldMetadata();
        String name = field.getSimpleName().toString();
        meta.setName(name);
        meta.setType(field.asType().getKind() == TypeKind.DECLARED
            ? field.asType().toString().replaceAll(".*\\.", "")
            : field.asType().getKind().name().toLowerCase());
        meta.setColumnName(camelToSnake(name));
        meta.setListable(true);
        meta.setEditable(true);

        FormField form = field.getAnnotation(FormField.class);
        if (form != null) {
            meta.setLabel(form.label().isEmpty() ? name : form.label());
            meta.setFormType(form.type().isEmpty() ? "text" : form.type());
            meta.setRequired(form.required());
            meta.setListable(form.listable());
            meta.setEditable(form.editable());
        } else {
            meta.setLabel(name);
            meta.setFormType("text");
        }

        if (isEntity) {
            meta.setId(field.getAnnotation(Id.class) != null);
            meta.setGeneratedValue(field.getAnnotation(GeneratedValue.class) != null);
            Column col = field.getAnnotation(Column.class);
            if (col != null) {
                if (!col.name().isEmpty()) {
                    meta.setColumnName(col.name());
                }
                meta.setLength(col.length() == 255 ? null : col.length());
                meta.setNullable(col.nullable());
            }
        }
        return meta;
    }

    private static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    private void writeSnapshot() {
        if (entities.isEmpty() && pages.isEmpty()) {
            return;
        }
        try {
            MetadataSnapshot snapshot = new MetadataSnapshot();
            snapshot.setEntities(entities);
            snapshot.setPages(pages);
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);

            var filer = processingEnv.getFiler();
            var resource = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "META-INF/fastgen/snapshot.json"
            );
            try (Writer w = resource.openWriter()) {
                w.write(json);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Failed to write fastgen snapshot: " + e.getMessage());
        }
    }
}
