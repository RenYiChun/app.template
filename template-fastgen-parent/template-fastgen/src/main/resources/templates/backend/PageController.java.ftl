package ${basePackage}.controller;

import ${basePackage}.domain.${page.simpleName}Request;
import ${basePackage}.service.${page.simpleName}Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

/**
 * Generated from @Page ${page.simpleName}. 本域端点由 page.actions 驱动，若存在 ${page.simpleName}Service 实现则委托之。
 */
@RestController
public class ${page.simpleName}Controller {

    private final ${page.simpleName}Service ${page.simpleName?uncap_first}Service;

    @Autowired(required = false)
    public ${page.simpleName}Controller(${page.simpleName}Service ${page.simpleName?uncap_first}Service) {
        this.${page.simpleName?uncap_first}Service = ${page.simpleName?uncap_first}Service;
    }

<#list page.actions as action>
    /**
     * ${action.comment}
     */
    <#if action.httpMethod == "GET">
    @GetMapping("${action.path}")
    public ResponseEntity<${action.returnType}> ${action.handlerMethod}(HttpSession session) {
        if (${page.simpleName?uncap_first}Service == null) {
            return ResponseEntity.notFound().build();
        }
        ${action.returnType} result = ${page.simpleName?uncap_first}Service.${action.handlerMethod}(session);
        return result == null || result.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }
    <#else>
    @PostMapping("${action.path}")
    public ResponseEntity<${action.returnType}> ${action.handlerMethod}(@RequestBody ${page.simpleName}Request request) {
        if (${page.simpleName?uncap_first}Service != null) {
            return ${page.simpleName?uncap_first}Service.${action.handlerMethod}(request);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "提交成功"));
    }
    </#if>

</#list>
}
