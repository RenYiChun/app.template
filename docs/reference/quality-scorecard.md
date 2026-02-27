# App Template 框架质量评分卡

本文档记录当前框架在各设计标准下的评分结果，供后续优化与迭代参考。评分基准：10 分制，基于代码审计与设计原则（KISS、SOLID、可观测性、安全性等）综合评定。

---

## 一、综合评分卡

| 维度 | 评分 | 说明 |
|------|:----:|------|
| **模块化与职责分离** | 9/10 | 模块边界清晰，按需引入，自动配置完善 |
| **异常处理体系** | 8/10 | 三层分级（领域/API/模块），统一 Result&lt;T&gt; 响应 |
| **缓存策略** | 8/10 | 统一 Caffeine，全部有容量上限和 TTL |
| **可观测性（指标）** | 8/10 | Micrometer 全链路覆盖，Grafana 仪表板就绪 |
| **可观测性（日志）** | 7/10 | 统一 @Slf4j，有 MDC 追踪；未集成分布式 Tracing |
| **安全体系** | 7/10 | JWT/Opaque Token 双模、RBAC 完备；CSRF 全局禁用、无限流 |
| **Flow 引擎设计** | 9/10 | 背压、公平调度、虚拟线程、可插拔存储和数据源 |
| **线程与资源管理** | 8/10 | 有命名、有 shutdown hook；DefaultSecurityFilterChainBuilder 仍用 setter 注入 |
| **配置治理** | 8/10 | @ConfigurationProperties 类型安全，启动校验+摘要日志 |
| **韧性能力** | 5/10 | 仅有 Feign 可选重试，无熔断器、无降级、无限流 |
| **API 文档** | 7/10 | Dataforge 有自动 OpenAPI 生成；工具类 Javadoc 覆盖不足 |
| **输入校验** | 6/10 | 支持 Bean Validation，缺少集中式 XSS 防护和输入清理 |
| **测试质量** | 5/10 | 核心模块覆盖尚可，2 个模块零测试，无 E2E，集成测试比例低 |
| **构建工程化** | 6/10 | 有 Checkstyle + SpotBugs，无 CI/CD、JaCoCo 默认跳过 |
| **国际化** | 2/10 | 前端有 i18n，后端错误消息全部硬编码中文 |
| **API 版本控制** | 2/10 | 无版本化策略 |
| **文档覆盖** | 7/10 | 核心文档完善，部分模块无 README |

**综合评分：6.6 / 10**

---

## 二、按优先级列出的不足

### 高优先级（影响生产稳定性和安全性）

| # | 不足项 | 位置/说明 |
|---|--------|-----------|
| 1 | **CSRF 全局禁用** | `DefaultSecurityFilterChainBuilder` 第 45 行 `http.csrf(AbstractHttpConfigurer::disable)`；对浏览器交互场景有风险，应改为仅对纯 API 端点禁用、对表单端点启用 |
| 2 | **无 CI/CD** | 无 GitHub Actions / Jenkins / GitLab CI 配置，质量依赖本地手动 |
| 3 | **2 个模块零测试** | `template-dataforge-processor`、`template-dataforge-model` 无任何测试 |
| 4 | **无限流机制** | API 层无限流保护，高并发易被打穿 |

### 中优先级（影响可维护性和工程质量）

| # | 不足项 | 位置/说明 |
|---|--------|-----------|
| 5 | **DefaultSecurityFilterChainBuilder 使用 setter 注入** | 8 个依赖通过 @Autowired setter 注入，不利于不可变性和单测 |
| 6 | **JPA 潜在 N+1** | `JpaEntityCrudService.list()` 对有关联的实体可能 N+1，缺少 @EntityGraph 或 JOIN FETCH |
| 7 | **后端无 i18n** | 错误消息硬编码中文，无法多语言 |
| 8 | **JaCoCo 默认跳过** | 配置了覆盖率检查但 `jacoco.check.skip=true` |
| 9 | **缺少 .editorconfig** | 多人协作格式易不一致 |
| 10 | **无 API 版本控制** | 无路径/请求头版本化，破坏性变更影响所有消费方 |

### 低优先级（可渐进改善）

| # | 不足项 |
|---|--------|
| 11 | 工具类 Javadoc 不足（Result、SpringContextUtil 等） |
| 12 | 无熔断器（仅有重试，无 Circuit Breaker / Fallback） |
| 13 | 部分模块无 README（template-api、template-cloud、template-oauth2-service） |
| 14 | 无参数化测试（@ParameterizedTest），边界覆盖不足 |
| 15 | 示例项目 CORS 过于宽松（允许所有 localhost 端口） |

---

## 三、做得好的方面（保持现状）

- 模块化设计与依赖管理（Enforcer 依赖收敛）
- Flow 引擎的背压、公平调度、虚拟线程设计
- 统一配置属性与启动校验
- Micrometer 指标全链路覆盖
- 异常处理三层分级
- SQL 注入防护（参数化查询）
- 代码重复度低

---

## 四、后续优化建议顺序

1. 引入 CI/CD（如 GitHub Actions），并启用 JaCoCo 检查
2. 为 CSRF 提供可配置策略（纯 API 可禁用，表单需启用）
3. 为零测试模块补充基础单元测试
4. 将 DefaultSecurityFilterChainBuilder 改为构造函数注入
5. 评估并引入限流（如 Bucket4j 或 Spring Cloud Gateway）
6. 为 JpaEntityCrudService 增加 @EntityGraph 或 JOIN FETCH 支持
7. 制定 API 版本化策略并配置化
8. 后端引入 MessageSource 支持 i18n
9. 添加 .editorconfig，统一编辑器格式
10. 按需引入熔断器（Resilience4j）与降级

---

*文档生成日期：2026-02-25。优化后请更新本文档中的评分与不足项。*
