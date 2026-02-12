package ${basePackage}.page;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Generated from @Page ${page.simpleName}.
 * 扩展点：在业务模块实现此接口，Controller 将委托给实现类；未提供实现时使用默认成功响应。
 */
public interface ${page.simpleName}Handler {

    /**
     * 处理 ${page.title} 提交。实现类中编写登录校验、写库等业务逻辑。
     *
     * @param request 表单请求体
     * @return 响应体，通常包含 success、message，登录场景可返回 token 等
     */
    ResponseEntity<Map<String, Object>> handleSubmit(${page.simpleName}Request request);
}
