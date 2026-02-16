package com.lrenyi.template.platform.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.lrenyi.template.platform.config.RbacAutoConfiguration;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.rbac.init.PermissionInitializer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 验证 RBAC 权限初始化后 GET /api/permissions 返回非空数据。
 * 测试中显式调用 PermissionInitializer.run()，因 @SpringBootTest 下 ApplicationRunner 可能尚未执行。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = EntityPlatformSampleApplication.class
)
@Import(RbacAutoConfiguration.class)
class PermissionsApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired(required = false)
    private PermissionInitializer permissionInitializer;

    @Autowired
    private EntityRegistry entityRegistry;

    @Autowired
    private EntityManager entityManager;

    @Test
    void permissionsListShouldNotBeEmptyAfterRbacInit() {
        assertThat(permissionInitializer)
                .as("PermissionInitializer 应由 RbacAutoConfiguration 注册（需 JPA）")
                .isNotNull();
        assertThat(entityRegistry.getAll())
                .as("实体应由 scan-packages 注册，请确认 app.platform.scan-packages 含 platform.domain")
                .isNotEmpty();
        permissionInitializer.run(new DefaultApplicationArguments());
        Long count = entityManager.createQuery(
                        "SELECT COUNT(p) FROM com.lrenyi.template.platform.domain.Permission p", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("run() 后 sys_permission 应有记录")
                .isGreaterThan(0);
        String url = "http://localhost:" + port + "/api/permissions";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("code").asInt()).isEqualTo(200);
        JsonNode data = body.path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.isEmpty())
                .as("GET /api/permissions 的 data 应有记录")
                .isFalse();
    }
}
