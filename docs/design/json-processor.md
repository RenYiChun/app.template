# JSON 处理器与框架切换能力

框架通过 `JsonProcessor` 抽象层提供**全局可切换的 JSON 处理能力**，业务代码统一依赖 `JsonService` 或 `JsonProcessor`，切换实现时无需修改业务逻辑。

---

## 一、设计概览

| 层级 | 说明 |
|------|------|
| **JsonProcessor** | 抽象接口，定义 toJson、fromJson、parse、prettyPrint、toMap、toList 等统一 API |
| **JacksonJsonProcessor** | 内置实现，基于 Jackson ObjectMapper（默认） |
| **JsonService** | 服务封装，注入 JsonProcessor，对外提供 serialize/deserialize 等，并统一异常处理 |

框架内部（JwtPublicKeyFilter、认证失败 Handler、安全链错误响应等）均通过 `JsonService` 输出 JSON，因此**切换 JsonProcessor 实现即实现全局 JSON 框架切换**。

---

## 二、切换方式

### 方式一：配置 + 内置实现

通过 `app.template.web.json-processor-type` 选择内置实现（当前支持 `jackson`）：

```yaml
app:
  template:
    web:
      json-processor-type: "jackson"  # 默认，Spring Boot 标配
```

### 方式二：自定义 Bean 覆盖

提供自己的 `JsonProcessor` Bean 即可全局接管，无需改配置：

```java
@Configuration
public class CustomJsonConfig {

    @Bean
    @Primary
    public JsonProcessor customJsonProcessor() {
        // 使用 Gson、Fastjson 等实现 JsonProcessor 接口
        return new GsonJsonProcessor();  // 需自行实现
    }
}
```

业务方只需注入 `JsonService`，框架会自动使用你注册的 `JsonProcessor`。

非 Spring 场景（如单元测试、批处理）可直接 `new JacksonJsonProcessor(new ObjectMapper())` 或 `new JsonService(processor)` 创建实例。

---

## 三、为何可全局切换

- **统一入口**：OAuth2、JWT 公钥、认证失败、401/403 等框架输出均经 `JsonService.serialize`，无硬编码 Jackson。
- **依赖注入**：`JsonProcessor` 由 Spring 管理，业务与测试可替换为 Mock 或其它实现。
- **按需扩展**：实现 `JsonProcessor` 接口并注册为 Bean，即可接入 Gson、Fastjson、JSON-B 等。

---

## 四、相关文档

- [配置参考 - JSON 处理器配置](../getting-started/config-reference.md#json-处理器配置) — 配置项与示例
- [框架收益 - 配置集中](framework-benefits.md) — JSON 框架可切换的收益说明
