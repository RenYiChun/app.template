package com.lrenyi.template.dataforge.controller;

import java.nio.charset.StandardCharsets;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内嵌 API 文档界面（Scalar API Reference）入口。当 app.dataforge.docs-ui-enabled 为 true 时，
 * 访问 docs-ui-path（默认 /docs）返回 Scalar 文档页，并加载 GET ${api-prefix}/docs 的 OpenAPI JSON。
 */
@RestController
@RequestMapping("${app.dataforge.docs-ui-path:/docs}")
@ConditionalOnProperty(name = "app.dataforge.docs-ui-enabled", havingValue = "true", matchIfMissing = true)
public class DocsUiController {
    
    private static final String SCALAR_CDN = "https://cdn.jsdelivr.net/npm/@scalar/api-reference";
    
    private final DataforgeProperties properties;
    
    public DocsUiController(DataforgeProperties properties) {
        this.properties = properties;
    }
    
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> docs() {
        String apiPrefix = properties.getApiPrefix();
        String specUrl = (apiPrefix == null || apiPrefix.isEmpty() ? "/api" :
                apiPrefix.startsWith("/") ? apiPrefix : "/" + apiPrefix) + "/docs";
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        html.append("  <title>Entity Dataforge API</title>\n");
        html.append("  <style>\n");
        html.append("    * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("    html, body { width: 100%; height: 100%; overflow: hidden; }\n");
        html.append("    #api-reference { width: 100%; height: 100%; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <script id=\"api-reference\" data-url=\"").append(specUrl).append("\"></script>\n");
        html.append("  <script>\n");
        html.append("    (function() {\n");
        html.append("      const urlParams = new URLSearchParams(window.location.search);\n");
        html.append("      const token = urlParams.get('token') || localStorage.getItem('auth_token');\n");
        html.append("      if (!token) return;\n");
        html.append("      \n");
        html.append("      // 1. 尝试通过配置注入\n");
        html.append("      const config = { authentication: { preferredSecurityScheme: 'bearerAuth', bearer: { token: "
                            + "token }, token: token } };\n");
        html.append("      const refEl = document.getElementById('api-reference');\n");
        html.append("      if (refEl) refEl.dataset.configuration = JSON.stringify(config);\n");
        html.append("      \n");
        html.append("      // 2. 暴力定时重试填充（针对 Scalar 异步渲染情况）\n");
        html.append("      let retryCount = 0;\n");
        html.append("      const timer = setInterval(() => {\n");
        html.append("        const inputs = document.querySelectorAll('input');\n");
        html.append("        let filled = false;\n");
        html.append("        inputs.forEach(input => {\n");
        html.append("          if ((input.placeholder === 'Token' || input.dataset.testid === 'auth-bearer-token') && "
                            + "!input.value) {\n");
        html.append("            input.value = token;\n");
        html.append("            input.dispatchEvent(new Event('input', { bubbles: true }));\n");
        html.append("            filled = true;\n");
        html.append("          }\n");
        html.append("        });\n");
        html.append("        if (filled || retryCount++ > 20) clearInterval(timer);\n");
        html.append("      }, 500);\n");
        html.append("    })();\n");
        html.append("  </script>\n");
        html.append("  <script src=\"").append(SCALAR_CDN).append("\"></script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return ResponseEntity.ok()
                             .contentType(MediaType.parseMediaType(
                                     MediaType.TEXT_HTML_VALUE + ";charset=" + StandardCharsets.UTF_8.name()))
                             .body(html.toString().getBytes(StandardCharsets.UTF_8));
    }
}
