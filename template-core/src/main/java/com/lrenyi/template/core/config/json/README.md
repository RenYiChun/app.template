# 可扩展JSON处理器

这是一个可扩展的JSON处理器框架，支持多种JSON处理库（Jackson、Gson等），并提供统一的API接口。

## 特性

- 🔧 **可扩展架构**: 支持多种JSON处理库
- 🎯 **统一接口**: 提供一致的API，便于切换不同实现
- ⚙️ **自动配置**: Spring Boot自动配置支持
- 🏭 **工厂模式**: 支持运行时动态创建处理器
- 📝 **类型安全**: 支持泛型和TypeReference
- 🎨 **功能丰富**: 支持美化输出、Map/List转换等

## 核心组件

### 1. JsonProcessor 接口

核心抽象接口，定义了所有JSON处理操作：

```java
public interface JsonProcessor {
    // 基本序列化/反序列化
    String toJson(Object obj);
    <T> T fromJson(String json, Class<T> clazz);
    <T> T fromJson(String json, TypeReference<T> typeRef);
    
    // JsonNode操作
    JsonNode parse(String json);
    String prettyPrint(String json);
    
    // 便捷转换
    Map<String, Object> toMap(String json);
    List<Object> toList(String json);
    
    // 扩展功能
    void registerTypeAdapter(Class<?> type, Object adapter);
    String getProcessorName();
    Set<JsonFeature> getSupportedFeatures();
}
```

### 2. 实现类

#### JacksonJsonProcessor
基于Jackson库的实现，功能最完整：
- 支持所有JsonFeature
- 完整的JsonNode支持
- 丰富的类型适配器

#### GsonJsonProcessor
基于Gson库的实现：
- 轻量级实现
- 部分JsonFeature支持
- JsonNode通过ObjectMapper转换

### 3. JsonService

服务层封装，提供更高级的API：

```java
@Service
public class JsonService {
    // 所有JsonProcessor的方法
    // 额外的便捷方法
    public Object getUnderlyingProcessor();
}
```

### 4. JsonProcessorFactory

工厂类，支持动态创建和管理处理器：

```java
public class JsonProcessorFactory {
    public static JsonProcessor createProcessor(String type);
    public JsonProcessor getProcessor(String type);
    public void registerProcessor(String type, Supplier<JsonProcessor> supplier);
}
```

## 配置方式

### 1. 通过配置文件

在 `application.yml` 中配置：

```yaml
app:
  config:
    json:
      processor-type: jackson  # 可选: jackson, gson
```

### 2. 通过Spring Profile

```yaml
# application-jackson.yml
app:
  config:
    json:
      processor-type: jackson

# application-gson.yml
app:
  config:
    json:
      processor-type: gson
```

### 3. 自动配置优先级

1. `@ConditionalOnProperty` 指定的处理器
2. 存在对应Bean时的自动配置
3. 工厂模式创建的默认处理器

## 使用示例

### 基本使用

```java
@Autowired
private JsonService jsonService;

// 序列化
User user = new User("张三", 25);
String json = jsonService.serialize(user);

// 反序列化
User deserializedUser = jsonService.deserialize(json, User.class);

// 泛型支持
List<User> users = jsonService.deserialize(json, new TypeReference<List<User>>() {});
```

### 高级功能

```java
// JsonNode操作
JsonNode node = jsonService.parse(json);
String name = node.get("name").asText();

// Map/List转换
Map<String, Object> map = jsonService.toMap(json);
List<Object> list = jsonService.toList(arrayJson);

// 美化输出
String prettyJson = jsonService.prettyPrint(json);
```

### 动态处理器

```java
@Autowired
private JsonProcessorFactory factory;

// 获取特定处理器
JsonProcessor jacksonProcessor = factory.getProcessor("jackson");
JsonProcessor gsonProcessor = factory.getProcessor("gson");

// 比较处理器
log.info("Jackson特性: {}", jacksonProcessor.getSupportedFeatures());
log.info("Gson特性: {}", gsonProcessor.getSupportedFeatures());
```

## 扩展自定义处理器

### 1. 实现JsonProcessor接口

```java
public class CustomJsonProcessor implements JsonProcessor {
    @Override
    public String toJson(Object obj) {
        // 自定义实现
    }
    
    @Override
    public String getProcessorName() {
        return "custom";
    }
    
    // 实现其他方法...
}
```

### 2. 注册到工厂

```java
@Configuration
public class CustomJsonConfig {
    
    @Bean
    public JsonProcessorFactory customFactory() {
        JsonProcessorFactory factory = new JsonProcessorFactory();
        factory.registerProcessor("custom", CustomJsonProcessor::new);
        return factory;
    }
}
```

### 3. 配置使用

```yaml
app:
  config:
    json:
      processor-type: custom
```

## 依赖管理

### Jackson (默认)

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### Gson (可选)

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
</dependency>
```

## 最佳实践

1. **默认使用Jackson**: 功能最完整，Spring Boot默认支持
2. **性能要求高时使用Gson**: 在某些场景下性能更好
3. **统一使用JsonService**: 避免直接依赖具体实现
4. **合理配置特性**: 根据需要启用/禁用特定功能
5. **自定义适配器**: 为特殊类型注册适配器

## 注意事项

- JsonNode功能在Gson实现中通过ObjectMapper转换，可能有性能影响
- 不同处理器的序列化结果可能略有差异
- 自定义适配器的注册方式因处理器而异
- 确保添加相应的依赖库

## 示例代码

完整的使用示例请参考 `JsonProcessorExample.java` 文件。