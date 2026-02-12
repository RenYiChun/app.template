package com.lrenyi.template.fastgen.generator;

import com.lrenyi.template.fastgen.model.EntityMetadata;
import com.lrenyi.template.fastgen.model.MetadataSnapshot;
import com.lrenyi.template.fastgen.model.PageMetadata;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 后端代码生成：Controller、Service 接口与默认实现、Mapper、Entity、建表 SQL。
 */
public class BackendGenerator {

    private final Configuration cfg;
    private final GeneratorConfig config;

    public BackendGenerator(Configuration cfg, GeneratorConfig config) {
        this.cfg = cfg;
        this.config = config;
    }

    public void generate(MetadataSnapshot snapshot) throws IOException, TemplateException {
        Path out = config.getBackendOutputDir();
        if (out == null) {
            return;
        }
        String basePackage = config.getBasePackage();

        for (EntityMetadata entity : snapshot.getEntities()) {
            Map<String, Object> data = new HashMap<>();
            data.put("entity", entity);
            data.put("basePackage", basePackage);

            String pkgPath = basePackage.replace('.', '/');
            Path entityDir = out.resolve("src/main/java").resolve(pkgPath).resolve(entity.getSimpleName().toLowerCase());

            try {
                Files.createDirectories(entityDir);
            } catch (IOException e) {
                // 忽略已存在
            }

            generateIfTemplateExists("backend/Entity.java.ftl", entityDir.resolve(entity.getSimpleName() + ".java"), data);
            generateIfTemplateExists("backend/Controller.java.ftl", entityDir.resolve(entity.getSimpleName() + "Controller.java"), data);
            generateIfTemplateExists("backend/Service.java.ftl", entityDir.resolve(entity.getSimpleName() + "Service.java"), data);
            generateIfTemplateExists("backend/ServiceImpl.java.ftl", entityDir.resolve(entity.getSimpleName() + "ServiceImpl.java"), data);
            generateIfTemplateExists("backend/Mapper.java.ftl", entityDir.resolve(entity.getSimpleName() + "Mapper.java"), data);
        }

        Path resourcesDir = out.resolve("src/main/resources");
        if (snapshot.getEntities() != null && !snapshot.getEntities().isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("entities", snapshot.getEntities());
            generateIfTemplateExists("backend/schema.sql.ftl", resourcesDir.resolve("schema.sql"), data);
        }

        // @Page 后端：apiPath 非空时生成 PageController 与 PageRequest
        String pkgPath = basePackage.replace('.', '/');
        Path pageDir = out.resolve("src/main/java").resolve(pkgPath).resolve("page");
        if (snapshot.getPages() != null) {
            for (PageMetadata page : snapshot.getPages()) {
                if (page.getApiPath() == null || page.getApiPath().isEmpty()) {
                    continue;
                }
                try {
                    Files.createDirectories(pageDir);
                } catch (IOException e) {
                    // 忽略已存在
                }
                Map<String, Object> data = new HashMap<>();
                data.put("page", page);
                data.put("basePackage", basePackage);
                generateIfTemplateExists("backend/PageRequest.java.ftl", pageDir.resolve(page.getSimpleName() + "Request.java"), data);
                generateIfTemplateExists("backend/PageHandler.java.ftl", pageDir.resolve(page.getSimpleName() + "Handler.java"), data);
                generateIfTemplateExists("backend/PageController.java.ftl", pageDir.resolve(page.getSimpleName() + "Controller.java"), data);
            }
        }
    }

    private void generateIfTemplateExists(String templateName, Path outputPath, Map<String, Object> data)
        throws IOException, TemplateException {
        try {
            Template tpl = cfg.getTemplate(templateName);
            Files.createDirectories(outputPath.getParent());
            try (Writer w = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                tpl.process(data, w);
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                // 模板尚未提供，跳过
                return;
            }
            throw e;
        }
    }
}
