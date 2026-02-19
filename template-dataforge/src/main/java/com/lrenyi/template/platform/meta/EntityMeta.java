package com.lrenyi.template.platform.meta;

import java.util.ArrayList;
import java.util.List;
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
    private Class<?> primaryKeyType = Long.class;
    private String permissionCreate = "";
    private String permissionRead = "";
    private String permissionUpdate = "";
    private String permissionDelete = "";
    private List<FieldMeta> fields = new ArrayList<>();
    private List<ActionMeta> actions = new ArrayList<>();
    private Class<?> entityClass;

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
}
