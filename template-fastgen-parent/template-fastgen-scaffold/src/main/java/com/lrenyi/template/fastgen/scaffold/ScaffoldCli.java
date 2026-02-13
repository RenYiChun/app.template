package com.lrenyi.template.fastgen.scaffold;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可执行 CLI：始终使用 JAR 内置模板生成项目。
 * 输出目录可选，未指定时用当前目录；项目生成在 输出目录/artifactId/ 下。
 * <code>java -jar template-fastgen-scaffold.jar [--outputDir=./out] --artifactId=my-app</code>
 */
public class ScaffoldCli {

    private static final String PREFIX = "scaffold.";
    private static final String PREFIX_DASH = "--";

    public static void main(String[] args) {
        Consumer<String> log = System.out::println;
        ScaffoldConfig config = new ScaffoldConfig();

        // 1) 先读 -Dscaffold.xxx（方便 java -Dscaffold.outputDir=./out -jar ...）
        for (String key : allKeys()) {
            String val = System.getProperty(PREFIX + key);
            if (val != null && !val.isEmpty()) {
                set(config, key, val);
            }
        }

        // 2) 再解析命令行 --key=value 或 --key value
        List<String> argList = Arrays.asList(args);
        for (int i = 0; i < argList.size(); i++) {
            String a = argList.get(i);
            if (a.startsWith(PREFIX_DASH)) {
                String rest = a.substring(PREFIX_DASH.length());
                String k = "", v = "";
                if (rest.contains("=")) {
                    int eq = rest.indexOf('=');
                    k = rest.substring(0, eq);
                    v = rest.substring(eq + 1);
                } else if (i + 1 < argList.size()) {
                    k = rest;
                    v = argList.get(++i);
                } else {
                    log.accept("缺少参数值: " + a);
                    printUsage(log);
                    System.exit(1);
                }
                set(config, k, v);
            }
        }

        // 未指定输出目录时使用当前目录；输出路径 = 输出目录/artifactId
        Path baseOut = config.getOutputDir() != null
            ? config.getOutputDir().toPath()
            : Paths.get(System.getProperty("user.dir", "."));
        Path outputPath = baseOut.toAbsolutePath().normalize().resolve(config.getArtifactId());
        Path templatePath;
        try {
            templatePath = BundledTemplate.extractToTemp(log);
        } catch (IOException e) {
            log.accept("无法加载内置模板: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            new ScaffoldEngine().run(config, templatePath, outputPath, log);
        } catch (Exception e) {
            log.accept("错误: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void set(ScaffoldConfig config, String key, String value) {
        switch (key) {
            case "outputDir" -> config.setOutputDir(new File(value));
            case "groupId" -> config.setGroupId(value);
            case "artifactId" -> config.setArtifactId(value);
            case "appPackage" -> config.setAppPackage(value);
            case "generatedBasePackage" -> config.setGeneratedBasePackage(value);
            case "projectName" -> config.setProjectName(value);
            case "projectDescription" -> config.setProjectDescription(value);
            case "springAppName" -> config.setSpringAppName(value);
            case "frontendName" -> config.setFrontendName(value);
            case "mainClass" -> config.setMainClass(value);
            case "parentGroupId" -> config.setParentGroupId(value);
            case "parentArtifactId" -> config.setParentArtifactId(value);
            case "parentVersion" -> config.setParentVersion(value);
            default -> { /* 忽略未知 key */ }
        }
    }

    private static String[] allKeys() {
        return new String[] {
            "outputDir", "groupId", "artifactId",
            "appPackage", "generatedBasePackage", "projectName", "projectDescription",
            "springAppName", "frontendName", "mainClass",
            "parentGroupId", "parentArtifactId", "parentVersion"
        };
    }

    private static void printUsage(Consumer<String> log) {
        log.accept("");
        log.accept("用法: java -jar template-fastgen-scaffold.jar [--outputDir=<输出目录>] [选项]");
        log.accept("  未指定 --outputDir 时使用当前目录；项目始终生成在 输出目录/<artifactId>/ 下。");
        log.accept("");
        log.accept("可选: --outputDir, --groupId, --artifactId, --appPackage, --generatedBasePackage,");
        log.accept("      --projectName, --projectDescription, --springAppName, --frontendName, --mainClass,");
        log.accept("      --parentGroupId, --parentArtifactId, --parentVersion");
    }
}
