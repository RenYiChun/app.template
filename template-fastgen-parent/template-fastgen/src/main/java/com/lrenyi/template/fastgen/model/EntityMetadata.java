package com.lrenyi.template.fastgen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域实体元数据，用于后端与前端生成。
 */
public class EntityMetadata {

    private String simpleName;
    private String packageName;
    private String tableName;
    private String displayName;
    private List<FieldMetadata> fields = new ArrayList<>();

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<FieldMetadata> getFields() {
        return fields;
    }

    public void setFields(List<FieldMetadata> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }
}
