package com.lrenyi.template.fastgen.scaffold;

import java.io.File;
import lombok.Getter;
import lombok.Setter;

/**
 * 脚手架生成参数，与 CLI 参数一一对应。
 * 各字段用于替换模板中的占位符，并参与生成目录/包名。
 */
@Setter
@Getter
public class ScaffoldConfig {
    
    /** 输出根目录（可选，未指定时用当前目录）；项目实际生成在 该目录/artifactId/ 下 */
    private File outputDir;
    
    /** 生成项目 pom 的 &lt;parent&gt; 的 groupId */
    private String parentGroupId = "com.lrenyi";
    /** 生成项目 pom 的 &lt;parent&gt; 的 artifactId */
    private String parentArtifactId = "template-dependencies";
    /** 生成项目 pom 的 &lt;parent&gt; 的 version */
    private String parentVersion = "2.4.2.1-SNAPSHOT";
    
    /** 生成项目自己的 Maven groupId，也用于替换模板中部分 &lt;groupId&gt; */
    private String groupId = "com.example";
    /** 生成项目自己的 Maven artifactId；同时作为输出子目录名（输出目录/artifactId/） */
    private String artifactId = "my-app";

    /** 应用包名：Application、handler 等业务代码所在包，替换模板中的 com.lrenyi.template.fastgen.example */
    private String appPackage = "com.example.myapp";
    /** 生成代码根包：Fastgen 生成的 domain/controller/service/mapper 的父包，替换模板中的 com.lrenyi.gen */
    private String generatedBasePackage = "com.example.myapp.generated";
    /** 项目显示名称，用于 pom name、启动日志等文案 */
    private String projectName = "My App";
    /** 项目描述，用于 pom description 及模板内描述文案 */
    private String projectDescription = "Fastgen 全栈示例项目";
    /** Spring Boot 的 spring.application.name */
    private String springAppName = "my-app";
    /** 前端工程/目录名（如 Vue 项目名） */
    private String frontendName = "my-app-frontend";
    /** 主类全限定名；不填则按 artifactId 推导（如 my-app → MyAppApplication） */
    private String mainClass;
}
