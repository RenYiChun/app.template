# 实体关联设计方案 - 实施注意事项

> **相关文档**：[主设计文档](entity-association-design.md)、[API 接口规范](entity-association-api-specification.md)

## 一、关键问题修正

### 1.1 MongoDB $lookup 字段名问题

**问题：** MongoDB 中使用 `@Field("department_id")` 时，实际存储的字段名是 `department_id`，但代码中使用 `field.getName()` 会得到 `departmentId`。

**解决方案：**

#### 方案 A：在 FieldMeta 中维护存储层字段名（推荐）

```java
@Getter
@Setter
public class FieldMeta {
    private String name;           // Java 字段名：departmentId
    private String columnName;     // 存储层字段名：department_id
    // ...
}
```

在 MetaScanner 中扫描时：

```java
private void scanField(Field field, FieldMeta fm) {
    fm.setName(field.getName());  // departmentId

    // JPA: 从 @Column 获取
    Column column = field.getAnnotation(Column.class);
    if (column != null && !column.name().isEmpty()) {
        fm.setColumnName(column.name());  // department_id
    }

    // MongoDB: 从 @Field 获取
    org.springframework.data.mongodb.core.mapping.Field mongoField =
        field.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
    if (mongoField != null && !mongoField.value().isEmpty()) {
        fm.setColumnName(mongoField.value());  // department_id
    }

    // 默认：使用 Java 字段名
    if (fm.getColumnName() == null || fm.getColumnName().isEmpty()) {
        fm.setColumnName(field.getName());
    }
}
```

在 MongoDB $lookup 中使用：

```java
private Page<Object> listWithLookup(EntityMeta entityMeta, Pageable pageable,
                                     ListCriteria criteria, List<FieldMeta> foreignKeyFields) {
    List<AggregationOperation> operations = new ArrayList<>();

    // 1. 匹配条件
    operations.add(Aggregation.match(buildCriteria(criteria)));

    // 2. 关联查询
    for (FieldMeta field : foreignKeyFields) {
        operations.add(Aggregation.lookup(
            getCollectionName(field.getReferencedEntity()),  // 关联集合
            field.getColumnName(),                           // 使用 columnName 而非 name
            "_id",                                           // 外部字段
            field.getName() + "_obj"                         // 结果字段
        ));
    }

    // ...
}
```

#### 方案 B：约定统一命名（不推荐）

强制要求 Java 字段名和数据库字段名一致（都使用驼峰或都使用下划线），但这会限制灵活性。

### 1.2 @DataforgeField 注解扩展

**问题：** 文档中使用了 `cascadeDelete` 属性，但当前 `@DataforgeField` 注解中没有这个属性。

**解决方案：** 扩展 `@DataforgeField` 注解

```java
@Documented
@Repeatable(DataforgeField.List.class)
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataforgeField {

    // ... 现有属性 ...

    // ==================== 关联关系 ====================

    /**
     * 是否为外键关联字段。
     */
    boolean foreignKey() default false;

    /**
     * 关联实体类名。
     */
    String referencedEntity() default "";

    /**
     * 关联字段名。
     */
    String referencedField() default "id";

    /**
     * 显示字段名（用于下拉框显示）。
     */
    String displayField() default "name";

    /**
     * 值字段名（用于下拉框值）。
     */
    String valueField() default "id";

    /**
     * 是否懒加载关联数据。
     */
    boolean lazyLoad() default true;

    // ==================== 新增：级联删除策略 ====================

    /**
     * 级联删除策略。
     * <ul>
     *   <li>RESTRICT: 限制删除，如果有关联数据则禁止删除（默认）</li>
     *   <li>SET_NULL: 删除时将关联字段设为 NULL</li>
     *   <li>CASCADE: 级联删除关联数据（危险，需谨慎使用）</li>
     * </ul>
     */
    CascadeStrategy cascadeDelete() default CascadeStrategy.RESTRICT;

    /**
     * 导出格式。
     * <ul>
     *   <li>id: 导出 ID 值（默认）</li>
     *   <li>display: 导出显示字段值</li>
     * </ul>
     */
    String exportFormat() default "id";
}

/**
 * 级联删除策略枚举
 */
public enum CascadeStrategy {
    /**
     * 限制删除：如果有关联数据，禁止删除
     */
    RESTRICT,

    /**
     * 置空：删除时将关联字段设为 NULL
     */
    SET_NULL,

    /**
     * 级联删除：删除时同时删除关联数据（危险）
     */
    CASCADE
}
```

同步更新 `FieldMeta`：

```java
@Getter
@Setter
public class FieldMeta {
    // ... 现有字段 ...

    // 关联关系
    private boolean foreignKey = false;
    private String referencedEntity = "";
    private String referencedField = "id";
    private String displayField = "name";
    private String valueField = "id";
    private boolean lazyLoad = true;

    // 新增：级联删除策略
    private CascadeStrategy cascadeDelete = CascadeStrategy.RESTRICT;

    // 新增：导出格式
    private String exportFormat = "id";
}
```

更新 `MetaScanner`：

```java
private void applyDataforgeField(Field field, FieldMeta fm, DataforgeField df) {
    // ... 现有代码 ...

    // 关联关系
    fm.setForeignKey(df.foreignKey());
    fm.setReferencedEntity(df.referencedEntity());
    fm.setReferencedField(df.referencedField());
    fm.setDisplayField(df.displayField());
    fm.setValueField(df.valueField());
    fm.setLazyLoad(df.lazyLoad());

    // 新增：级联删除策略
    fm.setCascadeDelete(df.cascadeDelete());

    // 新增：导出格式
    fm.setExportFormat(df.exportFormat());
}
```

### 1.3 章节编号与 EntityRegistry 方法

**章节编号：** 设计文档第八章已修正，`### 8.1 核心特性` 正确，并补充了 `### 8.0 不支持的场景`。

**EntityRegistry 方法：** 级联删除的 `findReferences` 中应使用 `entityMetaRegistry.getAll()`，而非 `getAllEntities()`（EntityRegistry 实际提供的是 `getAll()` 方法）。

## 二、实施清单补充

### 2.1 注解扩展任务

- [ ] 在 `@DataforgeField` 中添加 `cascadeDelete` 属性
- [ ] 在 `@DataforgeField` 中添加 `exportFormat` 属性
- [ ] 创建 `CascadeStrategy` 枚举
- [ ] 在 `FieldMeta` 中添加对应字段
- [ ] 更新 `MetaScanner` 扫描逻辑

### 2.2 字段名映射任务

- [ ] 确保 `FieldMeta.columnName` 正确存储数据库字段名
- [ ] 更新 `MetaScanner` 同时扫描 `@Column` 和 `@Field` 注解
- [ ] 在 MongoDB $lookup 中使用 `columnName`
- [ ] 在 JPA 查询中使用 `columnName`
- [ ] 添加单元测试验证字段名映射

### 2.3 MongoDB 特殊处理

- [ ] 实现 MongoDB 的 $lookup 聚合查询
- [ ] 处理 MongoDB ObjectId 和 String 的转换
- [ ] 为 MongoDB 关联字段创建索引
- [ ] 测试 MongoDB 的关联查询性能

### 2.4 导出格式支持

- [ ] 在 `ExcelExportService` 中根据 `exportFormat` 决定导出内容
- [ ] 实现批量加载关联数据的逻辑
- [ ] 添加导出格式的配置文档
- [ ] 测试导出功能

## 三、最佳实践建议

### 3.1 字段命名约定

**推荐做法：**

```java
// JPA 实体
@Entity
@Table(name = "users")
public class User extends BaseEntity<Long> {

    @Column(name = "department_id")  // 数据库字段名
    @DataforgeField(
        label = "部门",
        foreignKey = true,
        referencedEntity = "Department"
    )
    private Long departmentId;  // Java 字段名（驼峰）
}

// MongoDB 实体
@Document(collection = "users")
public class UserMongo extends MongoBaseDocument<String> {

    @Field("department_id")  // MongoDB 字段名
    @DataforgeField(
        label = "部门",
        foreignKey = true,
        referencedEntity = "DepartmentMongo"
    )
    private String departmentId;  // Java 字段名（驼峰）
}
```

**关键点：**
- Java 字段名统一使用驼峰命名（departmentId）
- 数据库字段名使用下划线命名（department_id）
- 通过注解明确指定数据库字段名
- `FieldMeta.columnName` 存储数据库字段名

### 3.2 级联删除使用建议

```java
// 1. 默认使用 RESTRICT（最安全）
@DataforgeField(
    foreignKey = true,
    referencedEntity = "Department",
    cascadeDelete = CascadeStrategy.RESTRICT  // 默认值，可省略
)
private Long departmentId;

// 2. 可空字段使用 SET_NULL
@Column(name = "manager_id", nullable = true)
@DataforgeField(
    foreignKey = true,
    referencedEntity = "User",
    cascadeDelete = CascadeStrategy.SET_NULL  // 删除经理时，将字段置空
)
private Long managerId;

// 3. 谨慎使用 CASCADE（需要明确业务需求）
@DataforgeField(
    foreignKey = true,
    referencedEntity = "OrderItem",
    cascadeDelete = CascadeStrategy.CASCADE  // 删除订单时，级联删除订单项
)
private Long orderId;
```

**使用原则：**
- 默认使用 `RESTRICT`，保护数据安全
- 只在字段可空时使用 `SET_NULL`
- 只在明确的父子关系中使用 `CASCADE`（如订单-订单项）
- 使用 `CASCADE` 时必须记录审计日志

### 3.3 导出格式选择

**exportFormat 来源约定：** 优先使用 `@DataforgeExport(exportFormat)`，若未配置则回退到 `@DataforgeField(exportFormat)`，避免两者重复配置时冲突。

```java
// 1. 用户友好的导出（推荐）
@DataforgeField(
    foreignKey = true,
    referencedEntity = "Department",
    exportFormat = "display"  // 导出部门名称
)
@DataforgeExport(header = "部门名称")
private Long departmentId;

// 2. 技术导出（用于数据迁移）
@DataforgeField(
    foreignKey = true,
    referencedEntity = "Department",
    exportFormat = "id"  // 导出部门 ID
)
@DataforgeExport(header = "部门ID")
private Long departmentId;
```

**选择建议：**
- 面向用户的报表：使用 `display` 格式
- 数据迁移/备份：使用 `id` 格式
- 可以提供两种导出模板供用户选择

## 四、测试用例建议

### 4.1 字段名映射测试

```java
@Test
public void testColumnNameMapping() {
    // JPA 实体
    EntityMeta userMeta = entityMetaRegistry.getByEntityName("User");
    FieldMeta deptField = userMeta.getField("departmentId");

    assertEquals("departmentId", deptField.getName());        // Java 字段名
    assertEquals("department_id", deptField.getColumnName()); // 数据库字段名

    // MongoDB 实体
    EntityMeta userMongoMeta = entityMetaRegistry.getByEntityName("UserMongo");
    FieldMeta deptFieldMongo = userMongoMeta.getField("departmentId");

    assertEquals("departmentId", deptFieldMongo.getName());
    assertEquals("department_id", deptFieldMongo.getColumnName());
}
```

### 4.2 级联删除测试

```java
@Test
public void testCascadeDeleteRestrict() {
    // 创建部门和用户
    Department dept = createDepartment("研发部");
    User user = createUser("张三", dept.getId());

    // 尝试删除部门（应该失败）
    assertThrows(CascadeConstraintException.class, () -> {
        entityCrudService.delete(departmentMeta, dept.getId());
    });
}

@Test
public void testCascadeDeleteSetNull() {
    // 创建用户和经理
    User manager = createUser("经理", null);
    User employee = createUser("员工", null);
    employee.setManagerId(manager.getId());
    userRepository.save(employee);

    // 删除经理（员工的 managerId 应该被置空）
    entityCrudService.delete(userMeta, manager.getId());

    User updatedEmployee = userRepository.findById(employee.getId()).get();
    assertNull(updatedEmployee.getManagerId());
}
```

### 4.3 导出格式测试

```java
@Test
public void testExportWithDisplayFormat() {
    // 创建测试数据
    Department dept = createDepartment("研发部");
    User user = createUser("张三", dept.getId());

    // 导出
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    excelExportService.exportToExcel(userMeta, List.of(user), out);

    // 验证导出内容
    Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
    Sheet sheet = workbook.getSheetAt(0);
    Row dataRow = sheet.getRow(1);

    // 部门列应该显示 "研发部" 而不是 ID
    assertEquals("研发部", dataRow.getCell(getDeptColumnIndex()).getStringCellValue());
}
```

## 五、文档修正清单

- [x] 第八章结构已修正（8.0 不支持的场景、8.1 核心特性等）
- [x] 设计文档 3.2.2 节已补充 MongoDB $lookup 使用 columnName 的说明
- [x] 设计文档 7.5.2 节 findReferences 已改为使用 `entityMetaRegistry.getAll()`
- [ ] 在 7.5.1 节补充注解扩展的说明
- [x] 添加字段名映射的最佳实践章节（见 3.1 节）
- [x] 添加测试用例示例章节（见第四节）

## 六、风险提示

### 6.1 MongoDB $lookup 性能风险

**风险：** MongoDB 的 $lookup 性能较差，特别是在大数据量场景。

**缓解措施：**
1. 为关联字段创建索引
2. 限制 $lookup 的使用场景（只在必要时使用）
3. 考虑数据冗余（在 User 中直接存储 departmentName）
4. 使用应用层批量查询代替 $lookup

### 6.2 级联删除风险

**风险：** `CASCADE` 策略可能导致意外的大量数据删除。

**缓解措施：**
1. 默认使用 `RESTRICT`
2. 使用 `CASCADE` 时必须记录审计日志
3. 删除前显示将要删除的关联数据数量
4. 提供"软删除"选项作为替代方案

### 6.3 导入性能风险

**风险：** 导入时通过 displayField 查找 ID，可能导致大量数据库查询。

**缓解措施：**
1. 导入前批量加载所有关联数据到内存
2. 使用 Map 缓存 displayField -> ID 的映射
3. 为 displayField 创建索引
4. 限制单次导入的数据量

## 七、总结

这些修正和补充确保了设计方案的完整性和可实施性：

1. **字段名映射**：通过 `columnName` 统一处理 JPA 和 MongoDB 的字段名差异
2. **注解扩展**：添加 `cascadeDelete` 和 `exportFormat` 属性
3. **最佳实践**：提供清晰的命名约定和使用建议
4. **测试用例**：确保功能正确性
5. **风险提示**：识别潜在问题并提供缓解措施

实施时严格按照这些规范，可以避免大部分问题。
