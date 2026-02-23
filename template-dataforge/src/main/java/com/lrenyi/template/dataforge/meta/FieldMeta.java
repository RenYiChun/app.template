package com.lrenyi.template.dataforge.meta;

import com.lrenyi.template.dataforge.annotation.ColumnAlign;
import com.lrenyi.template.dataforge.annotation.ColumnFixed;
import com.lrenyi.template.dataforge.annotation.FormComponent;
import com.lrenyi.template.dataforge.annotation.MaskType;
import com.lrenyi.template.dataforge.annotation.SearchComponent;
import com.lrenyi.template.dataforge.annotation.SearchType;
import lombok.Getter;
import lombok.Setter;

/**
 * 字段元数据，用于 CRUD 与 OpenAPI。
 */
@Getter
@Setter
public class FieldMeta {

    private String name;
    private String type;
    private String columnName;
    private boolean primaryKey;
    private boolean required;
    private boolean nullable = true;
    private boolean queryable = false;
    /** 搜索栏排序权重（由 @DataforgeField 注解设置） */
    private int searchOrder;
    /** 是否从导出中排除（由 @DataforgeExport(enabled=false) 设置） */
    private boolean exportExcluded;
    
    // ==================== 新增字段元数据属性 ====================
    
    // 基础信息
    private String label = "";
    private String description = "";
    private int order = 0;
    private String group = "";
    private int groupOrder = 0;
    
    // 表格列配置
    private boolean columnVisible = true;
    private boolean columnResizable = true;
    private boolean columnSortable = true;  // 用户特别关注的属性
    private boolean columnFilterable = false;
    private ColumnAlign columnAlign = ColumnAlign.LEFT;
    private int columnWidth = 0;
    private int columnMinWidth = 80;
    private ColumnFixed columnFixed = ColumnFixed.NONE;
    private boolean columnEllipsis = false;
    private String columnClassName = "";
    
    // 表单控件配置
    private FormComponent component = FormComponent.TEXT;
    private String placeholder = "";
    private String tips = "";
    private boolean uiRequired = false;  // UI层面的必填，区别于数据库required
    private boolean readonly = false;
    private boolean disabled = false;
    private boolean hidden = false;
    
    // 验证规则
    private String regex = "";
    private String regexMessage = "";
    private int minLength = 0;
    private int maxLength = 0;
    private double minValue = Double.MIN_VALUE;
    private double maxValue = Double.MAX_VALUE;
    private String[] allowedValues = {};
    
    // 数据字典/枚举
    private String dictCode = "";
    private String[] enumOptions = {};
    private String[] enumLabels = {};
    
    // 搜索配置
    private boolean searchable = false;
    private SearchType searchType = SearchType.EQUALS;
    private SearchComponent searchComponent = SearchComponent.INPUT;
    private String searchDefaultValue = "";
    private boolean searchRequired = false;
    private String searchPlaceholder = "";
    private String[] searchRangeFields = {};
    
    // 数据转换与显示
    private String format = "";
    private String maskPattern = "";
    private MaskType maskType = MaskType.NONE;
    private boolean sensitive = false;
    private String defaultValue = "";
    private String defaultValueExpression = "";
    
    // 关联关系
    private boolean foreignKey = false;
    private String referencedEntity = "";
    private String referencedField = "id";
    private String displayField = "name";
    private String valueField = "id";
    private boolean lazyLoad = true;
    
    // 导入导出配置
    private boolean exportEnabled = true;
    private String exportHeader = "";
    private int exportOrder = 0;
    private String exportFormat = "";
    private String exportConverterClassName = "";
    private int exportWidth = 0;
    private String exportCellStyle = "";
    private boolean exportWrapText = false;
    private int exportColumnType = 0;
    private String exportComment = "";
    private boolean exportHidden = false;
    private String exportGroup = "";
    private boolean exportFrozen = false;
    private String exportDataValidation = "";
    private String exportHyperlinkFormula = "";
    
    private boolean importEnabled = true;
    private boolean importRequired = false;
    private String importSample = "";
    private String importConverterClassName = "";
    private String importValidationRegex = "";
    private String importValidationMessage = "";
    private String importDefaultValue = "";
    private boolean importUnique = false;
    private String importDuplicateMessage = "数据重复";
    private String importDictCode = "";
    private String[] importAllowedValues = {};
    private double importMinValue = Double.MIN_VALUE;
    private double importMaxValue = Double.MAX_VALUE;
    private int importMinLength = 0;
    private int importMaxLength = 0;
    private String importDateFormat = "";
    private boolean importIgnoreCase = false;
    private boolean importTrim = true;
    private String importErrorPolicy = "STOP";
    
    // DTO控制
    private String[] dtoIncludeTypes = {};
    private String[] dtoExcludeTypes = {};
    private String dtoFieldName = "";
    private Class<?> dtoFieldType = void.class;
    private String dtoConverterClassName = "";
    private String dtoFormat = "";
    private Class<?>[] dtoValidationGroups = {};
    private boolean dtoReadOnly = false;
    private boolean dtoWriteOnly = false;
    private boolean dtoCreateOnly = false;
    private boolean dtoUpdateOnly = false;
    private boolean dtoQueryOnly = false;
    
    // ==================== 数组属性自定义setter ====================
    
    public void setAllowedValues(String[] allowedValues) {
        this.allowedValues = allowedValues != null ? allowedValues : new String[0];
    }
    
    public void setEnumOptions(String[] enumOptions) {
        this.enumOptions = enumOptions != null ? enumOptions : new String[0];
    }
    
    public void setEnumLabels(String[] enumLabels) {
        this.enumLabels = enumLabels != null ? enumLabels : new String[0];
    }
    
    public void setSearchRangeFields(String[] searchRangeFields) {
        this.searchRangeFields = searchRangeFields != null ? searchRangeFields : new String[0];
    }
    
    public void setImportAllowedValues(String[] importAllowedValues) {
        this.importAllowedValues = importAllowedValues != null ? importAllowedValues : new String[0];
    }
    
    public void setDtoIncludeTypes(String[] dtoIncludeTypes) {
        this.dtoIncludeTypes = dtoIncludeTypes != null ? dtoIncludeTypes : new String[0];
    }
    
    public void setDtoExcludeTypes(String[] dtoExcludeTypes) {
        this.dtoExcludeTypes = dtoExcludeTypes != null ? dtoExcludeTypes : new String[0];
    }
    
    public void setDtoValidationGroups(Class<?>[] dtoValidationGroups) {
        this.dtoValidationGroups = dtoValidationGroups != null ? dtoValidationGroups : new Class<?>[0];
    }
}
