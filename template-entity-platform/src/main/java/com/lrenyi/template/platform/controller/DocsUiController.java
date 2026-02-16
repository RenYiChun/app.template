package com.lrenyi.template.platform.controller;

import com.lrenyi.template.platform.config.EntityPlatformProperties;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内嵌 API 文档界面（Scalar API Reference）入口。当 app.platform.docs-ui-enabled 为 true 时，
 * 访问 docs-ui-path（默认 /docs）返回 Scalar 文档页，并加载 GET ${api-prefix}/docs 的 OpenAPI JSON。
 */
@RestController
@RequestMapping("${app.platform.docs-ui-path:/docs}")
@ConditionalOnProperty(name = "app.platform.docs-ui-enabled", havingValue = "true", matchIfMissing = true)
public class DocsUiController {

    private static final Logger log = LoggerFactory.getLogger(DocsUiController.class);
    private static final String SCALAR_CDN = "https://cdn.jsdelivr.net/npm/@scalar/api-reference";

    private final EntityPlatformProperties properties;

    public DocsUiController(EntityPlatformProperties properties) {
        this.properties = properties;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> docs() {
        String apiPrefix = properties.getApiPrefix();
        String specUrl = (apiPrefix == null || apiPrefix.isEmpty() ? "/api" : apiPrefix.startsWith("/") ? apiPrefix : "/" + apiPrefix) + "/docs";
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        html.append("  <title>Entity Platform API</title>\n");
        html.append("  <style>\n");
        html.append("    * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("    html, body { width: 100%; height: 100%; overflow: hidden; }\n");
        html.append("    #api-reference { width: 100%; height: 100%; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <script id=\"api-reference\" data-url=\"").append(specUrl).append("\"></script>\n");
        html.append("  <script src=\"").append(SCALAR_CDN).append("\"></script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MediaType.TEXT_HTML_VALUE + ";charset=" + StandardCharsets.UTF_8.name()))
                .body(html.toString().getBytes(StandardCharsets.UTF_8));
    }
}
