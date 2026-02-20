package com.lrenyi.template.dataforge.backend.domain;

import com.lrenyi.template.dataforge.annotation.ColumnAlign;
import com.lrenyi.template.dataforge.annotation.ColumnFixed;
import com.lrenyi.template.dataforge.annotation.DataforgeDto;
import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.annotation.DataforgeExport;
import com.lrenyi.template.dataforge.annotation.DataforgeField;
import com.lrenyi.template.dataforge.annotation.DataforgeImport;
import com.lrenyi.template.dataforge.annotation.DtoType;
import com.lrenyi.template.dataforge.annotation.FormComponent;
import com.lrenyi.template.dataforge.annotation.MaskType;
import com.lrenyi.template.dataforge.annotation.SearchComponent;
import com.lrenyi.template.dataforge.annotation.SearchType;
import com.lrenyi.template.dataforge.annotation.SortDirection;
import com.lrenyi.template.dataforge.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体 - 新注解系统示例
 * 
 * 展示了如何使用新的四大类注解：
 * 1. @DataforgeEntity - 实体全局配置
 * 2. @DataforgeField - 字段元数据配置
 * 3. @DataforgeExport/@DataforgeImport - 导入导出配置
 * 4. @DataforgeDto - DTO生成控制
 */
@Setter
@Getter
@Entity
@Table(name = "users_new")
@DataforgeEntity(
    pathSegment = "users",
    displayName = "用户管理",
    description = "系统用户管理模块，包含用户基本信息、权限等",
    defaultSortField = "createTime",
    defaultSortDirection = SortDirection.DESC,
    defaultPageSize = 20,
    pageSizeOptions = {10, 20, 50, 100},
    softDelete = true,
    enableCache = true,
    cacheExpireSeconds = 600,
    enableCreateAudit = true,
    enableUpdateAudit = true,
    enableDeleteAudit = true,
    tags = {"用户管理", "系统管理"}
)
@DataforgeDto(
    parentOverrides = {
        @DataforgeDto(parentFieldName = "createTime", include = DtoType.RESPONSE),
        @DataforgeDto(parentFieldName = "updateTime", include = DtoType.RESPONSE),
        @DataforgeDto(parentFieldName = "id", include = {DtoType.UPDATE, DtoType.RESPONSE})
    },
    ignoreFields = {"password", "salt"},
    dtoPackage = "com.lrenyi.template.dataforge.backend.dto",
    dtoSuffix = "DTO",
    apiSummary = "用户管理API",
    apiDescription = "提供用户管理的CRUD操作",
    apiTags = {"用户管理", "系统管理"}
)
public class UserNew extends BaseEntity<Long> {
    
    /**
     * 用户名 - 完整的字段配置示例
     */
    @DataforgeField(
        label = "用户名",
        description = "用户登录名，全局唯一",
        order = 1,
        columnVisible = true,
        columnSortable = true,  // ✅ 支持表格列排序
        columnResizable = true,
        columnFilterable = true,
        columnAlign = ColumnAlign.LEFT,
        columnWidth = 150,
        columnMinWidth = 100,
        columnFixed = ColumnFixed.NONE,
        columnEllipsis = false,
        columnClassName = "username-column",
        component = FormComponent.TEXT,
        placeholder = "请输入用户名",
        tips = "用户名长度为3-50个字符",
        required = true,
        readonly = false,
        disabled = false,
        hidden = false,
        regex = "^[a-zA-Z][a-zA-Z0-9_]{2,49}$",
        regexMessage = "用户名必须以字母开头，可包含字母、数字、下划线，长度3-50",
        minLength = 3,
        maxLength = 50,
        allowedValues = {},
        searchable = true,
        searchType = SearchType.LIKE,
        searchComponent = SearchComponent.INPUT,
        searchDefaultValue = "",
        searchRequired = false,
        searchPlaceholder = "搜索用户名",
        format = "",
        maskType = MaskType.NONE,
        sensitive = false,
        defaultValue = "",
        defaultValueExpression = "",
        foreignKey = false
    )
    @DataforgeDto(
        include = {DtoType.CREATE, DtoType.UPDATE, DtoType.QUERY},
        fieldName = "username",
        readOnly = false,
        createOnly = false,
        updateOnly = false,
        queryOnly = false
    )
    @DataforgeExport(
        enabled = true,
        header = "用户名",
        order = 1,
        format = "",
        converter = com.lrenyi.template.dataforge.support.ExportValueConverter.class,
        width = 150,
        cellStyle = "",
        wrapText = false,
        columnType = 0,
        comment = "用户登录名",
        hidden = false,
        group = "基本信息",
        frozen = false
    )
    @DataforgeImport(
        enabled = true,
        required = true,
        sample = "zhangsan",
        converter = com.lrenyi.template.dataforge.support.ImportValueConverter.class,
        validationRegex = "^[a-zA-Z][a-zA-Z0-9_]{2,49}$",
        validationMessage = "用户名格式不正确",
        defaultValue = "",
        unique = true,
        duplicateMessage = "用户名重复",
        dictCode = "",
        allowedValues = {},
        minValue = Double.MIN_VALUE,
        maxValue = Double.MAX_VALUE,
        minLength = 3,
        maxLength = 50,
        dateFormat = "",
        ignoreCase = false,
        trim = true,
        errorPolicy = DataforgeImport.ErrorPolicy.STOP
    )
    @Column(nullable = false, length = 64, unique = true)
    private String username;
    
    /**
     * 昵称 - 简化配置示例
     */
    @DataforgeField(
        label = "昵称",
        order = 2,
        columnSortable = true,  // ✅ 支持表格列排序
        columnWidth = 120,
        searchable = true
    )
    @Column(length = 64)
    private String nickname;
    
    /**
     * 密码 - 安全字段示例
     */
    @DataforgeField(
        label = "密码",
        order = 3,
        component = FormComponent.PASSWORD,
        sensitive = true,
        masked = true,
        maskType = MaskType.CUSTOM,
        maskPattern = "********"
    )
    @DataforgeDto(exclude = DtoType.RESPONSE)  // 响应中排除密码
    @DataforgeExport(enabled = false)  // 导出中排除
    @DataforgeImport(enabled = false)  // 导入中排除
    @Column(nullable = false, length = 128)
    private String password;
    
    /**
     * 邮箱 - 带验证的字段示例
     */
    @DataforgeField(
        label = "邮箱",
        order = 4,
        component = FormComponent.EMAIL,
        searchable = true,
        searchType = SearchType.LIKE
    )
    @Column(length = 128)
    private String email;
    
    /**
     * 手机号 - 脱敏字段示例
     */
    @DataforgeField(
        label = "手机号",
        order = 5,
        component = FormComponent.TEXT,
        searchable = true,
        maskType = MaskType.PHONE
    )
    @Column(length = 20)
    private String phone;
    
    /**
     * 部门ID - 外键关联字段示例
     */
    @DataforgeField(
        label = "部门",
        order = 6,
        component = FormComponent.SELECT,
        foreignKey = true,
        referencedEntity = "Department",
        displayField = "name",
        valueField = "id",
        lazyLoad = true
    )
    @Column(name = "department_id")
    private Long departmentId;
    
    /**
     * 状态 - 数据字典字段示例
     */
    @DataforgeField(
        label = "状态",
        order = 7,
        component = FormComponent.SELECT,
        dictCode = "USER_STATUS",
        columnSortable = true,  // ✅ 支持表格列排序
        searchable = true,
        searchType = SearchType.EQUALS,
        searchComponent = SearchComponent.SELECT
    )
    @DataforgeExport(
        converter = com.lrenyi.template.dataforge.backend.converter.StatusConverter.class,
        width = 100
    )
    @Column(length = 1)
    private String status; // 0-停用 1-正常
    
    /**
     * 头像 - 文件上传字段示例
     */
    @DataforgeField(
        label = "头像",
        order = 8,
        component = FormComponent.UPLOAD
    )
    @Column(length = 256)
    private String avatar;
    
    /**
     * 性别 - 枚举字段示例
     */
    @DataforgeField(
        label = "性别",
        order = 9,
        component = FormComponent.RADIO,
        enumClass = Gender.class,
        enumOptions = {"M", "F", "U"},
        enumLabels = {"男", "女", "未知"}
    )
    @Column(length = 1)
    private String gender;
    
    /**
     * 年龄 - 数字字段示例
     */
    @DataforgeField(
        label = "年龄",
        order = 10,
        component = FormComponent.NUMBER,
        minValue = 0,
        maxValue = 150
    )
    @Column
    private Integer age;
    
    /**
     * 生日 - 日期字段示例
     */
    @DataforgeField(
        label = "生日",
        order = 11,
        component = FormComponent.DATE,
        format = "yyyy-MM-dd"
    )
    @DataforgeExport(format = "yyyy年MM月dd日")
    @Column
    private java.time.LocalDate birthday;
    
    /**
     * 是否管理员 - 开关字段示例
     */
    @DataforgeField(
        label = "是否管理员",
        order = 12,
        component = FormComponent.SWITCH
    )
    @Column
    private Boolean admin;
    
    /**
     * 个人简介 - 多行文本示例
     */
    @DataforgeField(
        label = "个人简介",
        order = 13,
        component = FormComponent.TEXTAREA,
        maxLength = 1000
    )
    @Column(length = 1000)
    private String introduction;
    
    /**
     * 评分 - 评分组件示例
     */
    @DataforgeField(
        label = "评分",
        order = 14,
        component = FormComponent.RATE
    )
    @Column
    private Integer rating;
    
    /**
     * 盐值 - 完全隐藏字段示例
     */
    @DataforgeField(
        label = "盐值",
        hidden = true  // 完全隐藏，不显示在UI中
    )
    @DataforgeDto(exclude = {DtoType.CREATE, DtoType.UPDATE, DtoType.RESPONSE})
    @DataforgeExport(enabled = false)
    @DataforgeImport(enabled = false)
    @Column(length = 64)
    private String salt;
    
    /**
     * 最后登录时间 - 只读字段示例
     */
    @DataforgeField(
        label = "最后登录时间",
        order = 99,
        component = FormComponent.DATETIME,
        readonly = true,
        format = "yyyy-MM-dd HH:mm:ss"
    )
    @DataforgeDto(readOnly = true)  // 只读，仅出现在响应中
    @DataforgeExport(format = "yyyy-MM-dd HH:mm")
    @Column
    private java.time.LocalDateTime lastLoginTime;
    
    /**
     * 性别枚举
     */
    public enum Gender {
        M, F, U
    }
}