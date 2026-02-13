# Template Fastgen Example

本模块演示如何在项目中使用 **template-fastgen**：仅添加依赖并编写带 `@Domain` / `@Page` 的类，编译期自动生成元数据快照。

## 使用步骤

1. **依赖**（已在本模块 `pom.xml` 中配置）  
   - 仅需 `template-fastgen` 为 compile 依赖，无需配置 `annotationProcessorPaths`，处理器通过 SPI 自动发现。

2. **编写模型与页面**  
   - [User.java](src/main/java/com/lrenyi/template/fastgen/example/domain/User.java)：`@Domain(table = "sys_user")` 实体，含 `@FormField`、`@Id`、`@Column`。  
   - [LoginPage.java](src/main/java/com/lrenyi/template/fastgen/example/page/LoginPage.java)：`@Page(title = "登录页", ...)` 页面描述。

3. **编译**  
   ```bash
   mvn -pl template-fastgen-example compile
   ```  
   编译完成后，在 `target/classes/META-INF/fastgen/snapshot.json` 中可查看生成的元数据。

4. **代码生成（可选）**  
   在应用中或通过 Maven 插件调用 `CodeGenerator#generateFromClasspath(config)`，将 snapshot 渲染为后端/前端代码（见 template-fastgen 的 README）。

## 产物说明

- **snapshot.json**：由 APT 在编译期产出，描述 entities 与 pages，供 CodeGenerator 读取。
- **前端生成文件**：`mvn generate-sources` 会由 Fastgen 插件生成并覆盖：
  - `src/main/frontend/src/router/generated.ts`
  - `src/main/frontend/src/api/*.ts`、`src/main/frontend/src/types/*.ts`
  - `src/main/frontend/src/views/` 下实体列表/表单/详情页及 @Page 对应 Vue
  模板中仅保留占位或骨架，不包含上述生成文件，避免与生成器冲突。
