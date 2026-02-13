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
    /** 后端 API 路径，非空时生成 PageController 与 Request DTO */
    private String apiPath;
    /** 提交成功后的前端跳转路径，非空时生成 router.push(successPath) */
    private String successPath;
    private List<FieldMetadata> fields = new ArrayList<>();
    /** 本域 HTTP 端点列表（如 submit、captcha），数据驱动 Controller/Service 生成，模板无需 if */
    private List<PageAction> actions = new ArrayList<>();

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

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getSuccessPath() {
        return successPath;
    }

    public void setSuccessPath(String successPath) {
        this.successPath = successPath;
    }

    public List<FieldMetadata> getFields() {
        return fields;
    }

    public void setFields(List<FieldMetadata> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }

    public List<PageAction> getActions() {
        return actions;
    }

    public void setActions(List<PageAction> actions) {
        this.actions = actions != null ? actions : new ArrayList<>();
    }
}
