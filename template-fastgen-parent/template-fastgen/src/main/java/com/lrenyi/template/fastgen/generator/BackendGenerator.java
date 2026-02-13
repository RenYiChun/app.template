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

        String pkgPath = basePackage.replace('.', '/');
        Path domainDir = out.resolve("src/main/java").resolve(pkgPath).resolve("domain");
        Path controllerDir = out.resolve("src/main/java").resolve(pkgPath).resolve("controller");
        Path serviceDir = out.resolve("src/main/java").resolve(pkgPath).resolve("service");
        Path mapperDir = out.resolve("src/main/java").resolve(pkgPath).resolve("mapper");

        for (EntityMetadata entity : snapshot.getEntities()) {
            Map<String, Object> data = new HashMap<>();
            data.put("entity", entity);
            data.put("basePackage", basePackage);

            try {
                Files.createDirectories(domainDir);
                Files.createDirectories(controllerDir);
                Files.createDirectories(serviceDir);
                Files.createDirectories(mapperDir);
            } catch (IOException e) {
                // 忽略已存在
            }

            generateIfTemplateExists("backend/Entity.java.ftl", domainDir.resolve(entity.getSimpleName() + ".java"), data);
            generateIfTemplateExists("backend/Controller.java.ftl", controllerDir.resolve(entity.getSimpleName() + "Controller.java"), data);
            generateIfTemplateExists("backend/Service.java.ftl", serviceDir.resolve(entity.getSimpleName() + "Service.java"), data);
            generateIfTemplateExists("backend/ServiceImpl.java.ftl", serviceDir.resolve(entity.getSimpleName() + "ServiceImpl.java"), data);
            generateIfTemplateExists("backend/Mapper.java.ftl", mapperDir.resolve(entity.getSimpleName() + "Mapper.java"), data);
        }

        Path resourcesDir = out.resolve("src/main/resources");
        if (snapshot.getEntities() != null && !snapshot.getEntities().isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("entities", snapshot.getEntities());
            generateIfTemplateExists("backend/schema.sql.ftl", resourcesDir.resolve("schema.sql"), data);
        }

        // @Page 后端：apiPath 非空时生成 PageController、PageRequest(domain)、PageService，一域一 Controller 一 Service
        if (snapshot.getPages() != null) {
            for (PageMetadata page : snapshot.getPages()) {
                if (page.getApiPath() == null || page.getApiPath().isEmpty()) {
                    continue;
                }
                try {
                    Files.createDirectories(domainDir);
                    Files.createDirectories(controllerDir);
                    Files.createDirectories(serviceDir);
                } catch (IOException e) {
                    // 忽略已存在
                }
                Map<String, Object> data = new HashMap<>();
                data.put("page", page);
                data.put("basePackage", basePackage);
                generateIfTemplateExists("backend/PageRequest.java.ftl", domainDir.resolve(page.getSimpleName() + "Request.java"), data);
                generateIfTemplateExists("backend/PageService.java.ftl", serviceDir.resolve(page.getSimpleName() + "Service.java"), data);
                generateIfTemplateExists("backend/PageController.java.ftl", controllerDir.resolve(page.getSimpleName() + "Controller.java"), data);
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
