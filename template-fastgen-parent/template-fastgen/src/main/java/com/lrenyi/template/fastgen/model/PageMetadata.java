package com.lrenyi.template.fastgen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 页面元数据，用于单页（如登录页）生成。
 */
public class PageMetadata {

    private String simpleName;
    private String packageName;
    private String title;
    private String layout;
    private String path;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<FieldMetadata> getFields() {
        return fields;
    }

    public void setFields(List<FieldMetadata> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }
}
