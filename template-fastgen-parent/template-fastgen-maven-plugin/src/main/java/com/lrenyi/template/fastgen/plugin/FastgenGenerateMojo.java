package com.lrenyi.template.fastgen.plugin;

import com.lrenyi.template.fastgen.generator.CodeGenerator;
import com.lrenyi.template.fastgen.generator.GeneratorConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 从 META-INF/fastgen/snapshot.json 生成后端/前端代码。
 * 支持参数校验、详细日志输出和文件统计。
 */
@Mojo(name = "generate", threadSafe = true)
public class FastgenGenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * 后端代码输出目录（相对项目根），不填则不生成后端。
     */
    @Parameter(property = "fastgen.backendOutputDir", defaultValue = "target/generated-sources/fastgen")
    private String backendOutputDir;

    /**
     * 前端代码输出目录（相对项目根），不填则不生成前端。
     */
    @Parameter(property = "fastgen.frontendOutputDir")
    private String frontendOutputDir;

    /**
     * 生成代码的 basePackage。
     */
    @Parameter(property = "fastgen.basePackage", defaultValue = "${project.groupId}")
    private String basePackage;

    /**
     * 是否为 dryRun 模式（仅预览文件列表，不实际生成）。
     */
    @Parameter(property = "fastgen.dryRun", defaultValue = "false")
    private boolean dryRun;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        long startTime = System.currentTimeMillis();
        getLog().info("========================================");
        getLog().info("Fastgen Code Generator Starting...");
        getLog().info("========================================");

        // 参数校验
        if (!validateParameters()) {
            return;
        }

        // 检查快照文件
        Path snapshotPath = locateSnapshotFile();
        if (snapshotPath == null) {
            getLog().warn("META-INF/fastgen/snapshot.json not found. Run 'mvn compile' first (with @Domain/@Page sources).");
            return;
        }

        getLog().info("Snapshot file: " + snapshotPath);
        getLog().info("Base package: " + basePackage);

        // 构建配置
        GeneratorConfig config = buildConfig();
        logConfiguration(config);

        if (dryRun) {
            getLog().info("DRY-RUN mode enabled. No files will be generated.");
        }

        try {
            if (!dryRun) {
                // 实际生成代码
                CodeGenerator generator = new CodeGenerator();
                generator.generateFromPath(snapshotPath, config);
                
                // 统计生成文件
                reportGeneratedFiles(config);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            getLog().info("========================================");
            getLog().info("Fastgen code generation completed successfully in " + elapsed + "ms");
            getLog().info("========================================");
        } catch (Exception e) {
            getLog().error("========================================");
            getLog().error("Fastgen generation FAILED: " + e.getMessage(), e);
            getLog().error("========================================");
            throw new MojoFailureException(e, "Fastgen generate failed", e.getMessage());
        }
    }

    /**
     * 参数校验。
     */
    private boolean validateParameters() {
        if (basePackage == null || basePackage.trim().isEmpty()) {
            getLog().error("Parameter 'basePackage' is required but was empty or null.");
            return false;
        }

        if ((backendOutputDir == null || backendOutputDir.isEmpty()) 
            && (frontendOutputDir == null || frontendOutputDir.isEmpty())) {
            getLog().warn("Neither 'backendOutputDir' nor 'frontendOutputDir' is set. Nothing will be generated.");
            return false;
        }

        return true;
    }

    /**
     * 定位快照文件。
     */
    private Path locateSnapshotFile() {
        if (project.getBuild().getOutputDirectory() == null) {
            return null;
        }
        Path snapshotPath = Paths.get(project.getBuild().getOutputDirectory())
            .resolve("META-INF/fastgen/snapshot.json");
        return snapshotPath.toFile().exists() ? snapshotPath : null;
    }

    /**
     * 构建生成器配置。
     */
    private GeneratorConfig buildConfig() {
        GeneratorConfig config = new GeneratorConfig();
        config.setBasePackage(basePackage);
        
        if (backendOutputDir != null && !backendOutputDir.isEmpty()) {
            Path backendPath = project.getBasedir().toPath().resolve(backendOutputDir);
            config.setBackendOutputDir(backendPath);
        }
        
        if (frontendOutputDir != null && !frontendOutputDir.isEmpty()) {
            Path frontendPath = project.getBasedir().toPath().resolve(frontendOutputDir);
            config.setFrontendOutputDir(frontendPath);
        }
        
        return config;
    }

    /**
     * 输出配置信息。
     */
    private void logConfiguration(GeneratorConfig config) {
        if (config.getBackendOutputDir() != null) {
            getLog().info("Backend output: " + config.getBackendOutputDir());
        } else {
            getLog().info("Backend output: [SKIP]");
        }
        
        if (config.getFrontendOutputDir() != null) {
            getLog().info("Frontend output: " + config.getFrontendOutputDir());
        } else {
            getLog().info("Frontend output: [SKIP]");
        }
    }

    /**
     * 统计并报告生成的文件。
     */
    private void reportGeneratedFiles(GeneratorConfig config) {
        AtomicInteger backendCount = new AtomicInteger(0);
        AtomicInteger frontendCount = new AtomicInteger(0);

        if (config.getBackendOutputDir() != null && Files.exists(config.getBackendOutputDir())) {
            backendCount.set(countFiles(config.getBackendOutputDir()));
            getLog().info("Backend: " + backendCount.get() + " files generated");
        }

        if (config.getFrontendOutputDir() != null && Files.exists(config.getFrontendOutputDir())) {
            frontendCount.set(countFiles(config.getFrontendOutputDir()));
            getLog().info("Frontend: " + frontendCount.get() + " files generated");
        }

        int total = backendCount.get() + frontendCount.get();
        getLog().info("Total: " + total + " files generated");
    }

    /**
     * 递归统计目录下的文件数量。
     */
    private int countFiles(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return (int) paths.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            getLog().warn("Failed to count files in " + directory + ": " + e.getMessage());
            return 0;
        }
    }
}
