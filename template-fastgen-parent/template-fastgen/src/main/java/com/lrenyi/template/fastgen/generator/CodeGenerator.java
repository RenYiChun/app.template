package com.lrenyi.template.fastgen.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.fastgen.model.MetadataSnapshot;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 代码生成入口：读取元数据快照，驱动 Freemarker 模板生成后端与前端代码。
 * 策略 C：生成到独立目录，用户代码与生成代码分离。
 */
public class CodeGenerator {

    private final Configuration freemarkerConfig;
    private final ObjectMapper objectMapper;

    public CodeGenerator() {
        this.freemarkerConfig = createFreemarkerConfig();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从 classpath 的 META-INF/fastgen/snapshot.json 读取快照并生成代码。
     */
    public void generateFromClasspath(GeneratorConfig config) throws IOException, TemplateException {
        MetadataSnapshot snapshot;
        try (InputStream in = getClass().getResourceAsStream("/META-INF/fastgen/snapshot.json")) {
            if (in == null) {
                throw new IOException("META-INF/fastgen/snapshot.json not found. Run compile with fastgen on annotationProcessorPaths first.");
            }
            snapshot = objectMapper.readValue(in, MetadataSnapshot.class);
        }
        generate(snapshot, config);
    }

    /**
     * 从指定路径读取 snapshot.json 并生成代码。
     */
    public void generateFromPath(Path snapshotPath, GeneratorConfig config) throws IOException, TemplateException {
        MetadataSnapshot snapshot = objectMapper.readValue(snapshotPath.toFile(), MetadataSnapshot.class);
        generate(snapshot, config);
    }

    public void generate(MetadataSnapshot snapshot, GeneratorConfig config) throws IOException, TemplateException {
        if (config.getBackendOutputDir() != null) {
            BackendGenerator backend = new BackendGenerator(freemarkerConfig, config);
            backend.generate(snapshot);
        }
        if (config.getFrontendOutputDir() != null) {
            FrontendGenerator frontend = new FrontendGenerator(freemarkerConfig, config);
            frontend.generate(snapshot);
        }
    }

    private static Configuration createFreemarkerConfig() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(CodeGenerator.class.getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        return cfg;
    }
}
