# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

App Template 是一个基于 Spring Boot 的快速开发模板库，提供 OAuth2 安全认证、实体驱动 CRUD（Dataforge）、流聚合引擎和微服务能力，设计目标是零侵入接入、快速开发。

**环境要求：**

- Java 21+
- Maven 3.6+
- Node.js（前端模块需要）

## 构建命令

### 后端（Maven）

```bash
# 完整构建并安装
./mvnw clean install

# 运行测试
./mvnw test

# 运行测试并生成覆盖率报告（JaCoCo）
./mvnw test -Pcoverage

# 跳过测试
./mvnw clean install -DskipTests

# 运行单个测试
./mvnw test -Dtest=ClassName#methodName -pl module-name
```

**注意：** Windows 上使用 `mvnw.cmd` 代替 `./mvnw`（虽然此环境中优先使用 bash 语法）。

### 前端（npm + Vite）

```bash
# 按正确顺序构建所有前端模块
./build-frontend.ps1  # PowerShell
# 或
./build-frontend.cmd  # Command Prompt

# 单个模块命令（在模块目录中执行）
npm install
npm run dev      # 开发服务器
npm run build    # 生产构建
npm run lint     # ESLint 自动修复
npm run format   # Prettier 格式化
```

**前端构建顺序：** template-dataforge-headless → template-dataforge-ui → template-dataforge-sample-frontend（由于本地文件依赖）。

## 模块架构

### 核心后端模块

- **template-api**: 安全配置、RBAC、WebSocket、全局异常处理、审计配置
- **template-core**: 工具类、加密服务、JSON 抽象、配置属性
- **template-flow**: 流聚合引擎，支持背压控制、虚拟线程、可插拔存储（Caffeine/Queue）
- **template-flow-sources**: 数据源适配器（Kafka、NATS、分页 API）
- **template-cloud**: Feign 客户端、OAuth2 Token 获取、凭证透传
- **template-oauth2-service**: OAuth2 授权服务器实现（Spring Authorization Server）

### Dataforge 模块（实体驱动 CRUD）

- **template-dataforge**: 核心 CRUD 引擎、权限、OpenAPI 生成
- **template-dataforge-jpa**: JPA 实现，支持审计
- **template-dataforge-mongodb**: MongoDB 实现
- **template-dataforge-processor**: 编译期注解处理器（生成元数据）
- **template-dataforge-sample-backend**: 示例后端应用

### 前端模块

- **template-dataforge-headless**: 框架无关的核心库
- **template-dataforge-ui**: Vue 3 + Element Plus 组件库
- **template-dataforge-sample-frontend**: 示例前端应用（Vue 3 + Vite）

### 依赖管理模块

- **template-dependencies**: 父 POM，管理所有依赖版本

## 核心架构模式

### Flow 流聚合引擎

Flow 模块提供按 joinKey 的多路数据流聚合，具备以下特性：

- 背压控制，防止 OOM
- 有界缓冲区，可配置限制
- 虚拟线程支持，高并发场景
- 可插拔存储后端（Caffeine 缓存或 Queue）
- 健康检查和指标集成
- 双重关闭保障（资源清理）

**适用场景：** 订单匹配、消息对齐、多源数据汇聚。

### Dataforge（实体驱动 CRUD）

注解驱动的 CRUD 生成：

- 在 JPA 实体上使用 `@DataEntity` 自动生成 REST API
- 编译期注解处理生成元数据
- 内置 Excel 导出、OpenAPI 文档
- Action 扩展支持自定义操作
- 通过注解实现字段分组和排序

### OAuth2 安全

- 双 Token 支持：JWT 和 Opaque Token
- RBAC 权限模型，声明式白名单
- WebSocket 认证复用 OAuth2 Token
- Feign 客户端凭证透传
- 审计日志，自动填充 @CreatedBy/@LastModifiedBy

## 代码质量与规范

### Checkstyle

- 配置文件：`checker/checkStyle.xml`
- 在 `process-sources` 阶段运行
- 所有模块强制执行（违规会导致构建失败）
- 代码风格参考：`CodeStyleV4.xml`（可导入 IDE）

### Spotbugs

- 配置文件：`checker/spotbugs-exclude.xml`
- 包含 FindSecBugs 插件进行安全检查
- 在 `compile` 阶段运行
- 可跳过：`-Dspotbugs.check.skip=true`

### JaCoCo 覆盖率

- 使用 `-Pcoverage` profile 激活
- 严格的 100% 分支覆盖率要求（当 `jacoco.check.skip=false` 时）
- 排除：`*AutoConfiguration*.class`、`*Configuration.class`、`*Application*.class`
- 报告位置：`target/site/jacoco/index.html`

### 前端代码检查

- ESLint 配合 Vue 插件
- Prettier 格式化
- 配置文件：`eslint.config.js`、`.prettierrc.json`

## 提交信息规范

**重要：** 所有提交信息必须使用中文（由 cursor rules 强制执行）。

格式：

```
<type>: <中文描述>

[可选的中文正文]
```

常用类型：

- `feat`: 新增功能
- `fix`: 修复问题
- `refactor`: 重构代码
- `docs`: 文档更新
- `style`: 代码格式
- `test`: 测试相关
- `chore`: 构建/工具相关

**示例：**

- ✅ `fix: 修复 FlowManager 与 ActiveLauncherLookup 的方法签名冲突`
- ✅ `feat: 新增 FlowLauncherFactory 工厂类，抽离 Launcher 创建逻辑`
- ❌ `fix: Resolve method signature conflict`（禁止使用英文）

## 包结构

- 后端：`com.lrenyi.template.*`（主要模块）、`com.lrenyi.oauth2.*`（OAuth2 服务）
- 前端：`@lrenyi/dataforge-headless`、`@lrenyi/dataforge-ui`

## 测试

### 后端测试

- 位置：`src/test/java/**/*Test.java`
- 框架：JUnit 5 + Spring Boot Test
- 运行单个测试：`./mvnw test -Dtest=ClassName#methodName -pl module-name`
- 集成测试：命名为 `*IntegrationTest.java`（如 `FlowJoinerEngineIntegrationTest`）

### 前端测试

- 在各模块目录中通过 npm scripts 运行
- 测试文件通常位于 `src/__tests__/` 或与组件同目录

## 文档

完整文档位于 `docs/` 目录：

- [快速开始](docs/getting-started/quick-start.md)
- [配置参考](docs/getting-started/config-reference.md)
- [Flow 流聚合使用指导](docs/guides/flow-usage-guide.md)
- [指标监控指南](docs/guides/metrics-guide.md)
- [架构优势](docs/design/architecture-advantages.md)

## 重要注意事项

1. **多模块构建：** 修改 `template-dependencies` 需要重新构建所有模块
2. **前端依赖：** 前端模块使用 `file:` 协议的本地依赖，必须按顺序构建
3. **注解处理：** 修改 `@DataEntity` 注解需要完整重新构建以重新生成元数据
4. **虚拟线程：** Flow 引擎使用虚拟线程（Java 21+），确保 JVM 兼容性
5. **Git 分支：** 主分支为 `main`，功能分支通常为 `feature/*`
