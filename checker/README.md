# 代码检查配置说明

本目录存放 SpotBugs 与 Checkstyle 的配置文件，用于构建时的静态检查。

## SpotBugs（缺陷与安全）

### 策略（方案 B：全量 + 排除）

- **运行方式**：全量运行 SpotBugs 与 FindSecBugs，不做 include 限制。
- **排除**：仅通过 [spotbugs-exclude.xml](spotbugs-exclude.xml) 排除误报或已接受的项。
- **POM**：根 [pom.xml](../pom.xml) 中只配置 `excludeFilterFile`，不配置 `includeFilterFile`。

### 如何运行

- 构建时自动执行：`mvn compile` 会触发 `spotbugs:check`。
- 仅跑 SpotBugs：`mvn spotbugs:check`
- 查看报告：`mvn spotbugs:gui`（需图形环境）

### 如何新增排除

1. 打开 [spotbugs-exclude.xml](spotbugs-exclude.xml)。
2. 在 `<FindBugsFilter>` 内增加 `<Match>...</Match>`。
3. 常用写法示例：
    - 按 bug 类型排除：`<Match><Bug code="EI"/></Match>`（EI 为暴露可变引用等）
    - 按类排除：`<Match><Class name="com.example.MyClass"/><Bug pattern="NP_NULL_ON_SOME_PATH"/></Match>`
    - 按包正则排除：`<Match><Package name="~com\.example\.gen\.*"/></Match>`
    - 按置信度排除：`<Match><Confidence value="3"/></Match>`（3=low）
4. 语法详见：<https://spotbugs.readthedocs.io/en/stable/filter.html>

### 与 Checkstyle 的关系

- **Checkstyle**（[checkStyle.xml](checkStyle.xml)）：代码风格与格式（缩进、命名、导入、括号等）。
- **SpotBugs**：潜在缺陷（空指针、资源未关、并发问题等）及 FindSecBugs 的安全漏洞（SQL 注入、命令注入等）。
- 二者互补，均在构建中执行；Checkstyle 在 `process-sources`，SpotBugs 在 `compile`。

## JaCoCo（分支覆盖率）

### 目标与策略

- **目标**：各模块分支覆盖率（branch coverage）达到 100%（排除配置类等后）。
- **排除**：不计入覆盖率分母的类（在根 [pom.xml](../pom.xml) 的 jacoco-maven-plugin 中配置）：
    - `**/*AutoConfiguration*.class`、`**/*Configuration.class`
    - `**/package-info.class`、`**/*Application*.class`

### 如何运行

- 覆盖率由 **coverage**  profile 提供（避免在无 JaCoCo 仓库时阻塞构建）。
- 运行测试并生成报告：`mvn clean test -Pcoverage`（report 在 test 阶段自动执行）。
- 查看报告：各模块 `target/site/jacoco/index.html`，关注 **Branch** 列。
- 强制检查 100% 分支：在根 pom 中将 `jacoco.check.skip` 设为 `false`，然后执行 `mvn verify -Pcoverage`；未达标则构建失败。
