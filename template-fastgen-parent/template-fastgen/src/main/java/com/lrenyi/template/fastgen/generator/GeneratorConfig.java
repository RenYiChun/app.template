package com.lrenyi.template.fastgen.generator;

import java.nio.file.Path;

/**
 * 生成器配置：输出目录、包名等。
 */
public class GeneratorConfig {

    private Path backendOutputDir;
    private Path frontendOutputDir;
    private String basePackage = "com.example";

    public Path getBackendOutputDir() {
        return backendOutputDir;
    }

    public void setBackendOutputDir(Path backendOutputDir) {
        this.backendOutputDir = backendOutputDir;
    }

    public Path getFrontendOutputDir() {
        return frontendOutputDir;
    }

    public void setFrontendOutputDir(Path frontendOutputDir) {
        this.frontendOutputDir = frontendOutputDir;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }
}
