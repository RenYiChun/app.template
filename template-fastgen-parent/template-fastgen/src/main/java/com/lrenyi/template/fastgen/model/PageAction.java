package com.lrenyi.template.fastgen.model;

/**
 * 页面域内一个 HTTP 端点，用于驱动 Controller/Service 的生成（数据驱动，模板无需 if 分支）。
 */
public class PageAction {

    /** GET / POST */
    private String httpMethod;
    /** 请求路径，如 /api/auth/login、/api/auth/captcha */
    private String path;
    /** Service 方法名，如 handleSubmit、getCaptcha */
    private String handlerMethod;
    /** 是否带请求体（POST submit 为 true，GET captcha 为 false） */
    private boolean requestBody;
    /** Controller 返回类型，如 ResponseEntity<Map<String, Object>> */
    private String returnType;
    /** Service 方法参数签名，如 LoginPageRequest request、HttpSession session */
    private String paramSignature;
    /** Service 方法返回类型，如 ResponseEntity<Map<String, Object>>、Map<String, String> */
    private String serviceReturnType;
    /** 方法注释 */
    private String comment;

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHandlerMethod() {
        return handlerMethod;
    }

    public void setHandlerMethod(String handlerMethod) {
        this.handlerMethod = handlerMethod;
    }

    public boolean isRequestBody() {
        return requestBody;
    }

    public void setRequestBody(boolean requestBody) {
        this.requestBody = requestBody;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getParamSignature() {
        return paramSignature;
    }

    public void setParamSignature(String paramSignature) {
        this.paramSignature = paramSignature;
    }

    public String getServiceReturnType() {
        return serviceReturnType;
    }

    public void setServiceReturnType(String serviceReturnType) {
        this.serviceReturnType = serviceReturnType;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
