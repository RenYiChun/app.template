# Fastgen 脚手架 CLI

从内置模板生成新项目，所有变量通过命令参数设置。模板在打包时放入 JAR，无需额外指定模板目录。

## 打包（模板会打进 JAR）

```bash
cd template-fastgen-parent
mvn package -pl template-fastgen-cli -DskipTests
```

产物：`template-fastgen-scaffold/target/template-fastgen-scaffold-2.4.2.1-SNAPSHOT.jar`  
其中 `src/main/resources/template/` 会作为 classpath 资源 `template/` 打进 JAR，CLI 运行时从这里解出模板并复制到输出目录。

## 使用

- **输出目录**：可选，不指定时使用当前目录。项目**始终**生成在 `输出目录/<artifactId>/` 下。
- 始终使用 JAR 内置模板。

```bash
# 在当前目录下生成 my-app/ 项目
java -jar template-fastgen-cli/target/template-fastgen-cli-2.4.2.1-SNAPSHOT.jar \
  --artifactId=my-app \
  --groupId=com.example \
  --appPackage=com.example.myapp

# 指定输出目录时，项目生成在 该目录/artifactId/
java -jar template-fastgen-cli-*.jar --outputDir=./projects --artifactId=my-app
```

## 可选参数（均有默认值）

groupId、artifactId、appPackage、generatedBasePackage、projectName、projectDescription、springAppName、frontendName、mainClass、parentGroupId、parentArtifactId、parentVersion（生成项目 version 固定为 1.0.0-SNAPSHOT）

## 修改界面效果

直接改 CLI 工程里的模板即可，改完重新打包 JAR：

- 模板路径：`template-fastgen-scaffold/src/main/resources/template/`
- 前端界面：改 `template/src/main/frontend/` 下 `App.vue`、`Home.vue`、`router/index.ts` 等
- 重新执行 `mvn package -pl template-fastgen-scaffold` 后，新 JAR 即带新模板
