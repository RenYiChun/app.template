package ${basePackage}.service;

import ${basePackage}.domain.${page.simpleName}Request;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

/**
 * Generated from @Page ${page.simpleName}. 本域端点由 page.actions 驱动，实现此接口后 Controller 委托给实现类。
 */
public interface ${page.simpleName}Service {

<#list page.actions as action>
    /**
     * ${action.comment}
     */
    ${action.serviceReturnType} ${action.handlerMethod}(${action.paramSignature});

</#list>
}
