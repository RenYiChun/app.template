# Template Fastgen Maven Plugin

根据编译期生成的 `META-INF/fastgen/snapshot.json` 生成后端/前端代码（策略 C：生成到指定目录）。

## 使用步骤

### 1. 在业务项目中添加依赖与插件

```xml
<dependencies>
    <dependency>
        <groupId>com.lrenyi</groupId>
        <artifactId>template-fastgen</artifactId>
        <version>2.4.2.1-SNAPSHOT</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>com.lrenyi</groupId>
            <artifactId>template-fastgen-maven-plugin</artifactId>
            <version>2.4.2.1-SNAPSHOT</version>
        </plugin>
    </plugins>
</build>
```

### 2. 编写带 @Domain / @Page 的类并编译

项目里要有至少一个 `@Domain` 或 `@Page` 的类，执行：

```bash
mvn compile
```

编译后会在 `target/classes/META-INF/fastgen/snapshot.json` 生成元数据快照。

### 3. 执行代码生成

```bash
mvn fastgen:generate
```

- **后端**：默认生成到 `target/generated-sources/fastgen`（可在插件中改）。
- **前端**：不配置则不生成；配置 `frontendOutputDir` 后生成到该目录。

### 4. 可选：绑定到生命周期

若希望 `compile` 后自动生成，可把插件绑到 `process-classes`：

```xml
<plugin>
    <groupId>com.lrenyi</groupId>
    <artifactId>template-fastgen-maven-plugin</artifactId>
    <version>2.4.2.1-SNAPSHOT</version>
    <executions>
        <execution>
            <id>generate</id>
            <phase>process-classes</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

则执行 `mvn compile` 时会先编译、再生成代码。

## 插件参数

| 参数 | 属性 | 默认值 | 说明 |
|------|------|--------|------|
| backendOutputDir | fastgen.backendOutputDir | target/generated-sources/fastgen | 后端代码输出目录（相对项目根） |
| frontendOutputDir | fastgen.frontendOutputDir | 无 | 前端代码输出目录，不填则不生成前端 |
| basePackage | fastgen.basePackage | \${project.groupId} | 生成 Java 代码的包名 |

命令行覆盖示例：

```bash
mvn fastgen:generate -Dfastgen.backendOutputDir=generated/backend -Dfastgen.frontendOutputDir=frontend/src
```

## 示例项目

同仓库下的 `template-fastgen-example` 为使用示例；在 example 目录执行 `mvn compile fastgen:generate` 即可看到生成结果（需先 `mvn install` 安装父模块与插件到本地仓库）。
