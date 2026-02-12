# Template Fastgen

声明即应用：基于注解的 Java 全栈 CRUD 代码生成（APT + Freemarker），遵循白皮书「策略 C」——生成与手写分离。

## 模块结构

- **annotation** — `@Domain`、`@Page`、`@FormField`、`@Id`、`@GeneratedValue`、`@Column`
- **processor** — APT 处理器，编译期产出 `META-INF/fastgen/snapshot.json`
- **model** — 元数据 POJO（EntityMetadata、PageMetadata、FieldMetadata、MetadataSnapshot）
- **generator** — 读取快照，Freemarker 渲染后端（Controller/Service/Mapper/Entity/SQL）与前端（Vue/TS/路由/API）

## 使用方式（用户项目）

1. 添加依赖即可，**无需单独配置注解处理器**。处理器已通过 JAR 内 `META-INF/services/javax.annotation.processing.Processor` 的 SPI 注册，javac 在编译时会自动发现并执行。

```xml
<dependency>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-fastgen</artifactId>
    <version>${project.version}</version>
</dependency>
```

2. 编写带 `@Domain` / `@Page` 的实体与页面类，执行 `mvn compile`，生成 `target/classes/META-INF/fastgen/snapshot.json`。
3. 通过 Maven 插件或自定义 Mojo 调用 `CodeGenerator#generateFromClasspath(config)`，将代码生成到配置的 backend/frontend 目录（策略 C：建议生成到独立模块或 `generated/` 目录）。

> 若希望处理器仅出现在编译期、不传递到运行时，可使用 `annotationProcessorPaths` 单独引入本模块，效果等价。

## 示例

同仓库下的 **template-fastgen-example** 模块演示了完整用法：`@Domain` 实体（User）、`@Page` 页面（LoginPage），编译后生成 `target/classes/META-INF/fastgen/snapshot.json`。可执行 `mvn -pl template-fastgen-example compile` 验证。

## 文档

- 完整需求与技术方案：[.cursor/plans/Java全栈快速开发框架-需求与技术方案白皮书.md](../.cursor/plans/Java全栈快速开发框架-需求与技术方案白皮书.md)
