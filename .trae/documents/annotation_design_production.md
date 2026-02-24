// 数据字典/枚举
String dictCode() default ""; // 数据字典编码
Class<? extends Enum<?>> enumClass() default Enum.class; // 枚举类
String[] enumOptions() default {}; // 枚举选项（当没有Enum类时）
String[] enumLabels() default {}; // 枚举标签（与options对应）
```

#### 4. 搜索配置（整合原@Searchable逻辑）
```java
// 搜索行为
boolean searchable() default false; // 是否作为搜索条件（原@Searchable核心逻辑）
SearchType searchType() default SearchType.EQUALS; // 搜索类型
SearchComponent searchComponent() default SearchComponent.INPUT; // 搜索组件
String searchDefaultValue() default ""; // 搜索默认值
boolean searchRequired() default false; // 搜索是否必填
String searchPlaceholder() default ""; // 搜索占位符
String[] searchRangeFields() default {}; // 范围搜索字段对（如[startTime,endTime]）
```

#### 5. 数据转换与显示
```java
// 格式化
String format() default ""; // 格式化模式（日期、数字等）
String maskPattern() default ""; // 脱敏规则（如手机号：*******1234）
MaskType maskType() default MaskType.NONE; // 脱敏类型
boolean sensitive() default false; // 是否敏感数据（需要加密存储）

// 默认值
String defaultValue() default ""; // 默认值（字符串形式）
String defaultValueExpression() default ""; // 默认值表达式（SpEL）
```

#### 6. 关联关系
```java
// 外键关联
boolean foreignKey() default false;
String referencedEntity() default ""; // 关联实体
String referencedField() default "id"; // 关联字段
String displayField() default "name"; // 显示字段（用于下拉框显示）
String valueField() default "id"; // 值字段（用于下拉框值）
boolean lazyLoad() default true; // 是否懒加载
```

### 相关枚举定义
```java
// 对齐方式
enum ColumnAlign { LEFT, CENTER, RIGHT }
// 固定列
enum ColumnFixed { NONE, LEFT, RIGHT }
// 表单组件类型
enum FormComponent { 
    TEXT, TEXTAREA, NUMBER, PASSWORD, EMAIL, URL, 
    SELECT, MULTI_SELECT, RADIO, CHECKBOX, 
    DATE, DATETIME, TIME, DATE_RANGE,
    SWITCH, SLIDER, RATE, 
    UPLOAD, RICH_TEXT, CODE_EDITOR
}
// 搜索类型
enum SearchType { 
    EQUALS, NOT_EQUALS, LIKE, NOT_LIKE, 
    STARTS_WITH, ENDS_WITH, 
    GREATER_THAN, LESS_THAN, GREATER_EQUALS, LESS_EQUALS,
    BETWEEN, IN, NOT_IN, IS_NULL, IS_NOT_NULL
}
// 搜索组件
enum SearchComponent { 
    INPUT, SELECT, MULTI_SELECT, DATE, DATETIME, DATE_RANGE,
    NUMBER_RANGE, SWITCH
}
// 脱敏类型
enum MaskType { NONE, PHONE, EMAIL, ID_CARD, BANK_CARD, NAME, CUSTOM }
```

## 三、字段操作注解 (@DataforgeOperation) - 字段级别

### 整合@ExportConverter, @ExportExclude并扩展

#### 1. 导出配置 (@DataforgeExport)
```java
boolean enabled() default true; // 是否导出（原@ExportExclude逻辑）
String header() default ""; // 导出列头（默认取@DataforgeField.label）
int exportOrder() default 0; // 导出顺序（默认取@DataforgeField.order）
String format() default ""; // 导出格式化（日期、数字等）
Class<? extends ExportValueConverter> converter() default ExportValueConverter.class; // 原@ExportConverter
int width() default 0; // Excel列宽
String cellStyle() default ""; // 单元格样式（如：dataFormat, alignment, fill等）
boolean wrapText() default false; // 是否自动换行
int columnType() default 0; // 列类型（0-字符串，1-数字，2-日期，3-布尔）
String comment() default ""; // 列注释
```

#### 2. 导入配置 (@DataforgeImport)
```java
boolean enabled() default true; // 是否允许导入
boolean required() default false; // 导入是否必填
String sample() default ""; // 示例数据（用于导入模板）
Class<? extends ImportValueConverter> converter() default ImportValueConverter.class; // 导入值转换器
String validationRegex() default ""; // 导入验证正则
String validationMessage() default ""; // 验证失败提示
String defaultValue() default ""; // 导入默认值（当单元格为空时）
boolean unique() default false; // 是否唯一（用于去重校验）
String duplicateMessage() default "数据重复"; // 重复数据提示
```

#### 3. 批量操作配置
```java
boolean batchable() default true; // 是否支持批量操作
String batchPermission() default ""; // 批量操作权限标识
boolean batchRequired() default false; // 批量操作是否必填
String batchValidation() default ""; // 批量操作验证规则
```

## 四、DTO控制注解 (@DataforgeDto) - 字段/类级别

### 扩展现有@DtoExcludeFrom

#### 1. 字段级别使用（控制单个字段）
```java
// 包含/排除
DtoType[] include() default {}; // 包含在哪些DTO中（空表示所有）
DtoType[] exclude() default {}; // 从哪些DTO中排除

// 字段映射
String dtoFieldName() default ""; // DTO中的字段名（默认同实体字段名）
Class<?> dtoFieldType() default void.class; // DTO中的字段类型（默认同实体字段类型）

// 转换器
Class<?> converter() default void.class; // 字段值转换器
String format() default ""; // 格式化（用于DTO序列化）

// 验证组
Class<?>[] validationGroups() default {}; // 验证组（用于分组验证）

// 快捷属性
boolean readOnly() default false; // 只读（等价于exclude={CREATE, UPDATE}）
boolean writeOnly() default false; // 只写（等价于exclude=RESPONSE）
boolean createOnly() default false; // 仅创建（等价于include=CREATE）
boolean updateOnly() default false; // 仅更新（等价于include=UPDATE）
```

#### 2. 类级别使用（控制父类字段和全局策略）
```java
// 父类字段覆盖
DataforgeDto[] parentOverrides() default {}; // 复用自身注解配置父类字段

// 全局忽略
String[] ignoreFields() default {}; // 全局忽略的字段列表

// DTO生成策略
String dtoPackage() default ""; // DTO包名（默认：实体包名 + ".dto"）
String dtoSuffix() default "DTO"; // DTO后缀
boolean generateAll() default true; // 是否生成所有类型DTO
DtoType[] generateTypes() default {DtoType.CREATE, DtoType.UPDATE, DtoType.RESPONSE}; // 指定生成的DTO类型

// 接口文档
String apiSummary() default ""; // API摘要
String apiDescription() default ""; // API详细描述
String[] apiTags() default {}; // API标签
```

#### 3. 扩展的DtoType枚举
```java
enum DtoType {
    CREATE_REQUEST,      // 创建请求DTO
    UPDATE_REQUEST,      // 更新请求DTO  
    QUERY_REQUEST,       // 查询请求DTO（搜索条件）
    RESPONSE,           // 响应DTO（单条）
    PAGE_RESPONSE,      // 分页响应DTO
    EXPORT_DTO,         // 导出DTO
    IMPORT_DTO,         // 导入DTO
    SIMPLE_RESPONSE,    // 简化响应DTO（用于下拉框等）
    DETAIL_RESPONSE     // 详情响应DTO（包含关联数据）
}
```

## 五、使用示例

### 实体定义示例
```java
@DataforgeEntity(
    pathSegment = "users",
    displayName = "用户管理",
    defaultSortField = "createTime",
    defaultSortDirection = SortDirection.DESC,
    defaultPageSize = 20,
    softDelete = true,
    enableCache = true
)
@DataforgeDto(
    parentOverrides = {
        @DataforgeDto(fieldName = "createTime", include = DtoType.RESPONSE),
        @DataforgeDto(fieldName = "id", include = {DtoType.UPDATE_REQUEST, DtoType.RESPONSE})
    },
    ignoreFields = {"password", "salt"}
)
public class User extends BaseEntity<Long> {
    
    @DataforgeField(
        label = "用户名",
        order = 1,
        columnSortable = true, // 支持表格排序
        columnWidth = 150,
        required = true,
        component = FormComponent.TEXT,
        searchable = true,
        searchType = SearchType.LIKE,
        minLength = 3,
        maxLength = 50
    )
    @DataforgeDto(include = {DtoType.CREATE_REQUEST, DtoType.UPDATE_REQUEST, DtoType.QUERY_REQUEST})
    private String username;
    
    @DataforgeField(
        label = "密码",
        order = 2,
        component = FormComponent.PASSWORD,
        sensitive = true,
        masked = true
    )
    @DataforgeDto(exclude = DtoType.RESPONSE) // 响应中排除密码
    @DataforgeExport(enabled = false) // 导出中排除
    private String password;
    
    @DataforgeField(
        label = "状态",
        order = 3,
        component = FormComponent.SELECT,
        dictCode = "USER_STATUS",
        columnSortable = true,
        searchable = true,
        searchType = SearchType.EQUALS,
        searchComponent = SearchComponent.SELECT
    )
    @DataforgeExport(converter = StatusConverter.class, width = 100)
    private String status;
    
    @DataforgeField(
        label = "创建时间",
        order = 100,
        component = FormComponent.DATETIME,
        format = "yyyy-MM-dd HH:mm:ss",
        columnSortable = true,
        columnAlign = ColumnAlign.CENTER,
        columnWidth = 180,
        readonly = true
    )
    @DataforgeExport(format = "yyyy-MM-dd", width = 120)
    private Date createTime;
}
```

## 六、迁移策略

### 阶段一：注解定义
1. 创建新的注解类：`DataforgeField`, `DataforgeOperation`, `DataforgeDto`
2. 扩展现有注解：`DataforgeEntity`, `DtoType`
3. 创建相关枚举：`ColumnAlign`, `FormComponent`, `SearchType`等

### 阶段二：处理器更新
1. 更新`template-dataforge-processor`以支持新注解
2. 更新元数据扫描器`MetaScanner`
3. 更新DTO生成器`DtoGeneratorProcessor`

### 阶段三：渐进迁移
1. 新实体使用新注解系统
2. 旧实体逐步迁移（可同时支持新旧注解）
3. 提供迁移工具或指南

### 阶段四：废弃旧注解
1. 标记`@Searchable`, `@ExportConverter`, `@ExportExclude`, `@DtoExcludeFrom`为`@Deprecated`
2. 在文档中说明迁移路径
3. 在未来的主要版本中移除旧注解
