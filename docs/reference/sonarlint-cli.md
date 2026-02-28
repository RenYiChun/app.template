# SonarLint CLI 本地扫描

使用 [code-freak/sonarlint-cli](https://github.com/code-freak/sonarlint-cli) 进行本地代码分析，**无需 SonarQube 服务器**。

## 前置要求

- Python 3.7+
- pip（`apt install python3-pip`）
- Java 11+（SonarLint 分析器依赖）

若未安装 pip，执行 `mvn verify -Psonarlint` 将跳过扫描且构建通过。

## 使用方式

```bash
./mvnw verify -Psonarlint
```

首次运行会自动创建虚拟环境并安装 sonarlint-cli，之后直接执行扫描。

## 输出

- 报告路径：`target/sonarlint-report.json`
- 仅分析 `src/main/java/**/*.java`，已排除测试类

## 说明

- sonarlint-cli 为社区原型，非 SonarSource 官方 CLI
- 支持 Java、Kotlin、JS、TS 等语言
