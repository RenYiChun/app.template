package com.lrenyi.template.fastgen.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.quote;

/**
 * 脚手架核心：按配置从模板目录复制并替换变量。
 */
public class ScaffoldEngine {
    
    private static final String SOURCE_APP_PACKAGE = "com.lrenyi.template.fastgen.example";
    private static final String SOURCE_APP_PACKAGE_PATH = "com/lrenyi/template/fastgen/example";
    private static final String SOURCE_GEN_PACKAGE_PATH = "com/lrenyi/gen";
    private static final String SOURCE_MAIN_CLASS = "FastgenExampleApplication";
    private static final String SOURCE_ARTIFACT_ID = "fastgen-example";
    private static final String SOURCE_PARENT_ARTIFACT_ID = "template-dependencies";
    private static final String SOURCE_PROJECT_NAME = "Template Fastgen Example";
    private static final String SOURCE_FRONTEND_NAME = "fastgen-example-frontend";
    
    /** 复制时跳过的目录（与 .gitignore / 常见忽略一致） */
    private static final Set<String> SKIP_DIRS = Set.of("target",
                                                        "node_modules",
                                                        ".git",
                                                        "dist",
                                                        "dist-ssr",
                                                        ".idea",
                                                        ".cursor",
                                                        ".vscode",
                                                        ".apt_generated",
                                                        "build"
    );
    /** 复制时跳过的文件名（精确匹配） */
    private static final Set<String> SKIP_FILES =
            Set.of(".flattened-pom.xml", "package-lock.json", ".DS_Store", ".env", ".env.local");
    private static final Pattern BINARY_EXT =
            Pattern.compile("(?i).*\\.(jar|class|png|jpg|jpeg|gif|ico|woff2?|ttf|eot)$");
    
    public static String artifactIdToMainClass(String artifactId) {
        StringBuilder sb = new StringBuilder();
        for (String part : artifactId.split("[-_]")) {
            if (part.isEmpty()) {continue;}
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb + "Application";
    }
    
    /**
     * 执行生成。templateDir 与 outputDir 必须已解析为绝对路径。
     */
    public void run(ScaffoldConfig config,
            Path templateDir,
            Path outputDir,
            java.util.function.Consumer<String> log) throws IOException {
        String mainClassName =
                config.getMainClass() != null && !config.getMainClass().isEmpty() ? config.getMainClass() :
                        artifactIdToMainClass(config.getArtifactId());
        
        Map<String, String> replacements = buildReplacements(config, mainClassName);
        
        if (Files.exists(outputDir)) {
            log.accept("输出目录已存在，将覆盖: " + outputDir);
        }
        copyAndReplace(templateDir, outputDir, replacements, config);
        log.accept("Scaffold 生成完成: " + outputDir);
    }
    
    private Map<String, String> buildReplacements(ScaffoldConfig c, String mainClassName) {
        String appPackagePath = c.getAppPackage().replace('.', '/');
        Map<String, String> m = new LinkedHashMap<>();
        m.put(SOURCE_APP_PACKAGE, c.getAppPackage());
        m.put(SOURCE_APP_PACKAGE_PATH, appPackagePath);
        m.put(SOURCE_GEN_PACKAGE_PATH, c.getGeneratedBasePackage().replace('.', '/'));
        m.put(SOURCE_MAIN_CLASS, mainClassName);
        m.put(SOURCE_ARTIFACT_ID, c.getArtifactId());
        m.put(SOURCE_PARENT_ARTIFACT_ID, c.getParentArtifactId());
        m.put(SOURCE_PROJECT_NAME, c.getProjectName());
        m.put(SOURCE_FRONTEND_NAME, c.getFrontendName());
        // 生成层包占位：domain/controller/service/mapper 统一在 generatedBasePackage 下
        String gen = c.getGeneratedBasePackage();
        m.put("com.lrenyi.gen.mapper", gen + ".mapper");
        m.put("com.lrenyi.gen.service.LoginPageService", gen + ".service.LoginPageService");
        m.put("com.lrenyi.gen.domain.LoginPageRequest", gen + ".domain.LoginPageRequest");
        m.put("com.lrenyi.gen", gen);
        // 不在此处替换 "com.lrenyi" -> groupId，避免误伤包名；pom 内 parent groupId 在 replacePomContent 中单独处理
        m.put("template-fastgen 使用示例：@Domain / @Page 注解驱动编译期元数据与代码生成（集成 Vue 前端）",
              c.getProjectDescription()
        );
        m.put("Fastgen 示例应用启动类。", c.getProjectName() + " 启动类。");
        m.put("Fastgen Example 启动成功！", c.getProjectName() + " 启动成功！");
        return m;
    }
    
    private static final String PARENT_VERSION_PLACEHOLDER = "__SCAFFOLD_PARENT_VERSION__";
    
    private String replacePomContent(String content, ScaffoldConfig c) {
        String s = content;
        s = s.replaceFirst(quote("<groupId>com.lrenyi</groupId>"), "<groupId>" + c.getParentGroupId() + "</groupId>");
        s = s.replace(quote("<artifactId>template-dependencies</artifactId>"),
                      "<artifactId>" + c.getParentArtifactId() + "</artifactId>"
        );
        // 先用占位符保护 parent 内的版本，避免被后面的全局 SOURCE_VERSION 替换覆盖
        s = s.replaceFirst(quote("<version>2.4.2.1-SNAPSHOT</version>"),
                           "<version>" + PARENT_VERSION_PLACEHOLDER + "</version>"
        );
        s = s.replace(quote("</parent>"),
                      "</parent>\n    <groupId>" + c.getGroupId() + "</groupId>\n    <version>1.0.0-SNAPSHOT</version>"
        );
        s = s.replace(SOURCE_ARTIFACT_ID, c.getArtifactId());
        s = s.replace(SOURCE_PROJECT_NAME, c.getProjectName());
        s = s.replace("template-fastgen 使用示例：@Domain / @Page 注解驱动编译期元数据与代码生成（集成 Vue 前端）",
                      c.getProjectDescription()
        );
        s = s.replace(PARENT_VERSION_PLACEHOLDER, c.getParentVersion());
        // 与 Service 等 import 的生成包一致：Fastgen 插件按此包生成 Controller/Service/Mapper 等
        s = s.replace("com.lrenyi.gen</fastgen.basePackage>", c.getGeneratedBasePackage() + "</fastgen.basePackage>");
        return s;
    }
    
    private void copyAndReplace(Path template,
            Path output,
            Map<String, String> replacements,
            ScaffoldConfig config) throws IOException {
        Files.walkFileTree(template, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = template.relativize(dir);
                for (int i = 0; i < rel.getNameCount(); i++) {
                    if (SKIP_DIRS.contains(rel.getName(i).toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                // 不在此处创建目录，仅在有文件写入时创建父目录，避免产生空目录
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = template.relativize(file);
                String name = rel.getFileName().toString();
                if (shouldSkipFile(name)) {return FileVisitResult.CONTINUE;}
                
                Path destPath = output.resolve(replaceInPath(rel.toString(), replacements));
                Files.createDirectories(destPath.getParent());
                
                if (isTextFile(name)) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    if ("pom.xml".equals(name)) {
                        content = replacePomContent(content, config);
                    } else {
                        // 按键长从长到短替换，避免 "com.lrenyi.gen" 或 "com.lrenyi" 先于 "com.lrenyi.template.fastgen.example" 匹配
                        Comparator<Map.Entry<String, String>> reversed =
                                Comparator.comparingInt((Map.Entry<String, String> x) -> x.getKey().length())
                                          .reversed();
                        for (Map.Entry<String, String> e : replacements.entrySet().stream().sorted(reversed).toList()) {
                            content = content.replace(e.getKey(), e.getValue());
                        }
                    }
                    Files.writeString(destPath, content, StandardCharsets.UTF_8);
                } else {
                    Files.copy(file, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private String replaceInPath(String path, Map<String, String> replacements) {
        String s = path.replace('\\', '/');
        s = s.replace(SOURCE_APP_PACKAGE_PATH, replacements.get(SOURCE_APP_PACKAGE_PATH));
        s = s.replace(SOURCE_GEN_PACKAGE_PATH, replacements.get(SOURCE_GEN_PACKAGE_PATH));
        s = s.replace(SOURCE_MAIN_CLASS + ".java", replacements.get(SOURCE_MAIN_CLASS) + ".java");
        return s;
    }
    
    /** 是否应忽略、不复制该文件（与 .gitignore 常见规则一致） */
    private static boolean shouldSkipFile(String name) {
        if (SKIP_FILES.contains(name)) {return true;}
        if (name.endsWith(".log") || name.startsWith("npm-debug.log")) {return true;}
        if (name.startsWith(".env.") && name.endsWith(".local")) {return true;}
        return name.endsWith(".suo") || name.startsWith(".ntvs") || name.endsWith(".njsproj") || name.endsWith(".sln")
                || name.endsWith(".swp") || name.endsWith(".swo");
    }
    
    private static boolean isTextFile(String fileName) {
        if (fileName.endsWith(".java") || fileName.endsWith(".xml") || fileName.endsWith(".yml") || fileName.endsWith(
                ".yaml") || fileName.endsWith(".json") || fileName.endsWith(".ts") || fileName.endsWith(".vue")
                || fileName.endsWith(".html") || fileName.endsWith(".css") || fileName.endsWith(".md")
                || fileName.endsWith(".ftl") || fileName.endsWith(".sql") || fileName.endsWith(".gitignore")
                || fileName.endsWith(".env")) {
            return true;
        }
        return !BINARY_EXT.matcher(fileName).matches();
    }
}
