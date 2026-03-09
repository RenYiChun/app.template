package com.lrenyi.template.dataforge.meta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.lrenyi.template.dataforge.annotation.SortDirection;
import com.lrenyi.template.dataforge.support.BeanAccessor;
import lombok.Getter;
import lombok.Setter;

/**
 * 实体元数据：表名、path、CRUD 开关、主键类型、权限、字段、Action 列表。
 */
@Getter
@Setter
public class EntityMeta {
    
    private String entityName;
    private String tableName;
    private String pathSegment;
    private String displayName;
    private boolean crudEnabled = true;
    private boolean listEnabled = true;
    private boolean getEnabled = true;
    private boolean createEnabled = true;
    private boolean updateEnabled = true;
    private boolean updateBatchEnabled = true;
    private boolean deleteEnabled = true;
    private boolean deleteBatchEnabled = true;
    private boolean exportEnabled = true;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Class<?> primaryKeyType = Long.class;
    private String storageType = "jpa";
    private String permissionCreate = "";
    private String permissionRead = "";
    private String permissionUpdate = "";
    private String permissionDelete = "";
    private List<FieldMeta> fields = new ArrayList<>();
    private List<ActionMeta> actions = new ArrayList<>();
    /** 表单分组列数配置，key 为分组名，value 为列数 */
    private Map<String, Integer> formGroupCols = new HashMap<>();
    /**
     * 前端 Schema 配置（create/update/pageResponse/response），由后端计算好直接下发。
     * 格式：Map<String, Map<String, Object>>
     */
    private Map<String, Object> schemas = new HashMap<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Class<?> entityClass;
    
    // ==================== 新增生产级属性 ====================
    
    private String description = "";
    private String defaultSortField = "";
    private SortDirection defaultSortDirection = SortDirection.DESC;
    private int defaultPageSize = 20;
    private int[] pageSizeOptions = {10, 20, 50, 100};
    private boolean enableVirtualScroll = false;
    private int virtualScrollRowHeight = 54;
    private boolean treeEntity = false;
    private String treeParentField = "parentId";
    private String treeChildrenField = "children";
    private String treeNameField = "name";
    private String treeCodeField = "code";
    private int treeMaxDepth = 10;
    private boolean softDelete = false;
    private String deleteFlagField = "deleted";
    private String deleteTimeField = "deleteTime";
    private Class<?> deleteFlagType = Boolean.class;
    private boolean enableCreateAudit = true;
    private boolean enableUpdateAudit = true;
    private boolean enableDeleteAudit = true;
    private String createUserField = "createBy";
    private String updateUserField = "updateBy";
    private boolean enableCache = false;
    private long cacheExpireSeconds = 300;
    private String cacheRegion = "";
    private boolean enableQueryOptimization = true;
    private int maxBatchSize = 1000;
    private String[] tags = {};
    private String icon = "";
    private String color = "";
    private boolean showInMenu = true;
    private int menuOrder = 0;
    private boolean enableOperationLog = true;
    private boolean enableVersionControl = false;
    private String versionField = "version";
    private boolean enableDataPermission = false;
    private String dataPermissionType = "";
    private boolean enableImport = true;
    private String importTemplate = "";
    private String exportTemplate = "";

    /** 列表页 UI 布局（table / masterDetailTree 左树右表），未配置时前端默认表格 */
    private EntityUiLayoutMeta uiLayout;

    // ==================== DTO Info ====================
    private String dtoCreate = "";
    private String dtoUpdate = "";
    private String dtoResponse = "";
    private String dtoPageResponse = "";
    
    // ==================== 运行时支持 ====================
    
    /**
     * 高性能属性访问器（JDK 9+ VarHandle 或反射回退）。
     * 在 MetaScanner 扫描时注入。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private BeanAccessor accessor;
    
    public void setPermissionCreate(String permissionCreate) {
        this.permissionCreate = permissionCreate != null ? permissionCreate : "";
    }
    
    public void setPermissionRead(String permissionRead) {
        this.permissionRead = permissionRead != null ? permissionRead : "";
    }
    
    public void setPermissionUpdate(String permissionUpdate) {
        this.permissionUpdate = permissionUpdate != null ? permissionUpdate : "";
    }
    
    public void setPermissionDelete(String permissionDelete) {
        this.permissionDelete = permissionDelete != null ? permissionDelete : "";
    }
    
    public void setFields(List<FieldMeta> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }
    
    public void setActions(List<ActionMeta> actions) {
        this.actions = actions != null ? actions : new ArrayList<>();
    }
    
    // ==================== 数组属性自定义setter ====================
    
    public void setPageSizeOptions(int[] pageSizeOptions) {
        this.pageSizeOptions = pageSizeOptions != null ? pageSizeOptions : new int[]{10, 20, 50, 100};
    }
    
    public void setTags(String[] tags) {
        this.tags = tags != null ? tags : new String[0];
    }
}
