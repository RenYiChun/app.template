package com.lrenyi.template.fastgen.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * 从 CLI 所在 JAR 的 classpath 中取出内置模板（template/），解压到临时目录供 ScaffoldEngine 使用。
 */
public final class BundledTemplate {
    
    private static final String TEMPLATE_PREFIX = "template/";
    
    /**
     * 将内置模板解压到临时目录，返回该目录的 Path。
     * 若从 JAR 运行则解压 JAR 内 template/ 条目；若从 classpath 目录运行则直接使用对应目录。
     */
    public static Path extractToTemp(Consumer<String> log) throws IOException {
        URL resource = BundledTemplate.class.getResource("/" + TEMPLATE_PREFIX);
        if (resource == null) {
            throw new IOException("内置模板不存在: classpath:/template/");
        }
        String protocol = resource.getProtocol();
        if ("file".equals(protocol)) {
            // IDE 或 target/classes 运行：直接使用目录
            try {
                Path dir = Paths.get(resource.toURI());
                if (Files.isDirectory(dir)) {
                    log.accept("使用内置模板: " + dir);
                    return dir;
                }
            } catch (Exception e) {
                throw new IOException("无法访问模板目录: " + resource, e);
            }
        }
        if ("jar".equals(protocol)) {
            return extractFromJar(resource, log);
        }
        throw new IOException("不支持的模板资源: " + resource);
    }
    
    private static Path extractFromJar(URL jarUrl, Consumer<String> log) throws IOException {
        Path tempDir = Files.createTempDirectory("fastgen-cli-template-");
        tempDir.toFile().deleteOnExit();
        // jarUrl 形如 jar:file:/path/to.jar!/template/，取 jar:file:/path/to.jar 打开
        String s = jarUrl.toString();
        int sep = s.indexOf("!/");
        String jarUri = sep > 0 ? s.substring(0, sep) : s;
        try (FileSystem fs = FileSystems.newFileSystem(URI.create(jarUri), Collections.emptyMap())) {
            // Zip/JAR 内路径无前导 /，且用 /
            Path templateRoot = fs.getPath("template");
            if (!Files.exists(templateRoot)) {
                throw new IOException("JAR 中未找到 template/");
            }
            copyRecursive(templateRoot, tempDir);
        }
        log.accept("已解压内置模板到: " + tempDir);
        return tempDir;
    }
    
    private static void copyRecursive(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(dir);
                Path target = dest.resolve(rel.toString());
                if (rel.getNameCount() > 0) {
                    Files.createDirectories(target);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(file);
                Path target = dest.resolve(rel.toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}
