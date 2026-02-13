package com.lrenyi.template.fastgen.generator;

import com.lrenyi.template.fastgen.model.EntityMetadata;
import com.lrenyi.template.fastgen.model.MetadataSnapshot;
import com.lrenyi.template.fastgen.model.PageMetadata;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 前端代码生成：Vue 列表/表单/详情、路由、API、TypeScript 类型。
 */
public class FrontendGenerator {

    private final Configuration cfg;
    private final GeneratorConfig config;

    public FrontendGenerator(Configuration cfg, GeneratorConfig config) {
        this.cfg = cfg;
        this.config = config;
    }

    public void generate(MetadataSnapshot snapshot) throws IOException, TemplateException {
        Path out = config.getFrontendOutputDir();
        if (out == null) {
            return;
        }

        for (EntityMetadata entity : snapshot.getEntities()) {
            Map<String, Object> data = new HashMap<>();
            data.put("entity", entity);

            Path viewsDir = out.resolve("src/views");
            Path apiDir = out.resolve("src/api");
            Path typesDir = out.resolve("src/types");
            Files.createDirectories(viewsDir);
            Files.createDirectories(apiDir);
            Files.createDirectories(typesDir);

            String name = entity.getSimpleName();
            generateIfTemplateExists("frontend/EntityList.vue.ftl", viewsDir.resolve(name + "List.vue"), data);
            generateIfTemplateExists("frontend/EntityForm.vue.ftl", viewsDir.resolve(name + "Form.vue"), data);
            generateIfTemplateExists("frontend/EntityDetail.vue.ftl", viewsDir.resolve(name + "Detail.vue"), data);
            generateIfTemplateExists("frontend/api.ts.ftl", apiDir.resolve(entity.getSimpleName().toLowerCase() + ".ts"), data);
            generateIfTemplateExists("frontend/types.ts.ftl", typesDir.resolve(entity.getSimpleName().toLowerCase() + ".ts"), data);
        }

        for (PageMetadata page : snapshot.getPages()) {
            Map<String, Object> data = new HashMap<>();
            data.put("page", page);
            Path viewsDir = out.resolve("src/views");
            Files.createDirectories(viewsDir);
            // 按页面名优先：Page_LoginPage.vue.ftl，否则回退 Page.vue.ftl
            generatePageVue(viewsDir, page, data);
        }

        if (!snapshot.getEntities().isEmpty() || !snapshot.getPages().isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("entities", snapshot.getEntities());
            data.put("pages", snapshot.getPages());
            generateIfTemplateExists("frontend/router.ts.ftl", out.resolve("src/router/generated.ts"), data);
        }
    }

    /**
     * 生成单页 Vue：优先使用 Page_{simpleName}.vue.ftl，不存在则使用 Page.vue.ftl。
     */
    private void generatePageVue(Path viewsDir, PageMetadata page, Map<String, Object> data)
        throws IOException, TemplateException {
        Path outputPath = viewsDir.resolve(page.getSimpleName() + ".vue");
        String specificName = "frontend/Page_" + page.getSimpleName() + ".vue.ftl";
        if (generateIfTemplateExists(specificName, outputPath, data)) {
            return;
        }
        generateIfTemplateExists("frontend/Page.vue.ftl", outputPath, data);
    }

    /**
     * @return true 表示模板存在且已写入，false 表示模板不存在
     */
    private boolean generateIfTemplateExists(String templateName, Path outputPath, Map<String, Object> data)
        throws IOException, TemplateException {
        try {
            Template tpl = cfg.getTemplate(templateName);
            Files.createDirectories(outputPath.getParent());
            try (var w = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                tpl.process(data, w);
            }
            return true;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return false;
            }
            throw e;
        }
    }
}
