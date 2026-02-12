package com.lrenyi.template.fastgen.plugin;

import com.lrenyi.template.fastgen.generator.CodeGenerator;
import com.lrenyi.template.fastgen.generator.GeneratorConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 从 META-INF/fastgen/snapshot.json 生成后端/前端代码。
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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path snapshotPath = project.getBuild().getOutputDirectory() != null
            ? Paths.get(project.getBuild().getOutputDirectory()).resolve("META-INF/fastgen/snapshot.json")
            : null;
        if (snapshotPath == null || !snapshotPath.toFile().exists()) {
            getLog().warn("META-INF/fastgen/snapshot.json not found. Run compile first (with @Domain/@Page sources).");
            return;
        }

        GeneratorConfig config = new GeneratorConfig();
        config.setBasePackage(basePackage);
        if (backendOutputDir != null && !backendOutputDir.isEmpty()) {
            config.setBackendOutputDir(project.getBasedir().toPath().resolve(backendOutputDir));
        }
        if (frontendOutputDir != null && !frontendOutputDir.isEmpty()) {
            config.setFrontendOutputDir(project.getBasedir().toPath().resolve(frontendOutputDir));
        }

        try {
            CodeGenerator generator = new CodeGenerator();
            generator.generateFromPath(snapshotPath, config);
            getLog().info("Fastgen code generation done.");
        } catch (Exception e) {
            throw new MojoFailureException(e, "Fastgen generate failed", e.getMessage());
        }
    }
}
