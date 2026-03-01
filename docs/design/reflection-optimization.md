# Dataforge 高性能反射优化方案 (VarHandle)

本文档详细说明了 `template-dataforge` 模块中引入的高性能属性访问机制，旨在解决传统 Java 反射在高频调用场景下的性能瓶颈。

## 1. 背景与问题

在 Dataforge 框架中，**实体属性的动态读写**是核心操作，广泛应用于：

1. **CRUD 操作**：`InMemoryEntityCrudService` 在列表过滤、排序、更新时频繁读取/写入字段。
2. **Excel 导出**：`ExcelExportSupport` 需要遍历大量实体并读取指定字段值。

### 痛点

* **性能开销**：传统 `Field.get()` / `Field.set()` 在每次调用时需进行权限检查（即便 `setAccessible(true)` 也有开销），且难以被
  JIT 编译器深度优化（内联）。
* **内存浪费**：原 Excel 导出逻辑使用 Jackson 将实体转换为 `Map` 再读取，导致大量临时对象创建，增加 GC 压力。

## 2. 技术选型：JDK 9+ VarHandle

我们采用 **`java.lang.invoke.VarHandle`** 替代传统反射。

| 特性         | 传统反射 (`Field`)    | VarHandle (JDK 9+)              | 优势                |
|:-----------|:------------------|:--------------------------------|:------------------|
| **访问方式**   | JNI 动态调用          | 模拟字节码指令 (`getfield`/`putfield`) | **接近原生代码性能**      |
| **JIT 优化** | 难以内联              | 极强内联支持                          | **零开销抽象**         |
| **安全性**    | 需 `setAccessible` | 标准化 API，类型安全                    | **符合 Java 模块化规范** |

## 3. 架构设计

### 3.1 核心接口 `BeanAccessor`

定义统一的属性访问契约，屏蔽底层实现细节。

```java
public interface BeanAccessor {
    Object get(Object bean, String propertyName);
    void set(Object bean, String propertyName, Object value);
    <T> T newInstance();
}
```

### 3.2 实现类 `VarHandleBeanAccessor`

* **初始化 (Warm-up)**：在构造时通过 `MethodHandles.privateLookupIn` 获取类的私有访问权限，遍历所有字段并预编译
  `VarHandle`，缓存到 `Map<String, VarHandle>`。
* **运行时 (Runtime)**：直接从 Map 获取句柄并调用，无反射权限检查开销。
* **降级策略**：若因模块化限制无法访问私有字段，会记录警告并优雅处理（可通过 `--add-opens` 解决）。

### 3.3 集成点

1. **`EntityMeta`**：持有 `BeanAccessor` 实例，随实体元数据在全系统流转。
2. **`MetaScanner`**：在应用启动扫描实体时，同步构建 `BeanAccessor` 并注入 `EntityMeta`。

## 4. 性能优化成果

### 4.1 CRUD 读写

`InMemoryEntityCrudService` 中的列表过滤和排序逻辑现已全量使用 `VarHandle`。

* **旧**：`field.setAccessible(true); return field.get(entity);`
* **新**：`return meta.getAccessor().get(entity, fieldName);`

### 4.2 Excel 导出 (Zero-Copy)

彻底重构了 `ExcelExportSupport`，移除了中间层的 `Map` 转换。

* **旧流程**：`List<Entity>` -> `Jackson` -> `List<Map>` -> `POI` -> Excel
* **新流程**：`List<Entity>` -> `VarHandle Accessor` -> `POI` -> Excel
* **收益**：
    * **CPU**：减少了 Jackson 序列化/反序列化的 CPU 消耗。
    * **内存**：消除了 `HashMap` 等临时对象的分配，大幅降低 Young GC 频率。

## 5. 最佳实践与注意事项

1. **模块化支持**：若实体类位于未 `open` 的 Java 模块中，`privateLookupIn` 可能失败。建议在 `module-info.java` 中对
   `template.dataforge` 开放实体包，或使用非模块化运行。
2. **继承支持**：`VarHandleBeanAccessor` 会自动向上遍历父类字段，支持继承结构。
3. **类型转换**：`Accessor.get()` 返回 `Object`，基本类型（int, long）会被自动装箱。这在大多数业务场景下是可以接受的。

## 6. 代码参考

* [BeanAccessor.java](src/main/java/com/lrenyi/template/dataforge/support/BeanAccessor.java)
* [VarHandleBeanAccessor.java](src/main/java/com/lrenyi/template/dataforge/support/VarHandleBeanAccessor.java)
* [EntityMeta.java](src/main/java/com/lrenyi/template/dataforge/meta/EntityMeta.java)
