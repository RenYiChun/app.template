package ${basePackage}.page;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Generated from @Page ${page.simpleName}.
 * 接收 ${page.title} 表单提交；若存在 ${page.simpleName}Handler 实现类则委托之，否则返回默认成功。
 */
@RestController
public class ${page.simpleName}Controller {

    private final ${page.simpleName}Handler ${page.simpleName?uncap_first}Handler;

    @Autowired(required = false)
    public ${page.simpleName}Controller(${page.simpleName}Handler ${page.simpleName?uncap_first}Handler) {
        this.${page.simpleName?uncap_first}Handler = ${page.simpleName?uncap_first}Handler;
    }

    /**
     * 处理 ${page.title} 提交。
     */
    @PostMapping("${page.apiPath}")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody ${page.simpleName}Request request) {
        if (${page.simpleName?uncap_first}Handler != null) {
            return ${page.simpleName?uncap_first}Handler.handleSubmit(request);
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "提交成功"
        ));
    }
}
