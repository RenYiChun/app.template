package com.lrenyi.template.platform.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 验证 users 实体的完整 CRUD 流程及分页响应结构。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = EntityPlatformSampleApplication.class
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsersCrudIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/users";
    }

    @Test
    @Order(1)
    void createUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> res = restTemplate.postForEntity(
                baseUrl(),
                new HttpEntity<>(Map.of("username", "testuser", "email", "test@example.com"), headers),
                JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = res.getBody();
        assertThat(node).isNotNull();
        assertThat(node.path("code").asInt()).isEqualTo(200);
        JsonNode data = node.path("data");
        assertThat(data).isNotNull();
        assertThat(data.path("username").asText()).isEqualTo("testuser");
        assertThat(data.path("id")).isNotNull();
    }

    @Test
    @Order(2)
    void listUsers_returnsPageStructure() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity(baseUrl(), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = res.getBody();
        assertThat(node).isNotNull();
        assertThat(node.path("code").asInt()).isEqualTo(200);
        JsonNode data = node.path("data");
        assertThat(data).isNotNull();
        assertThat(data.has("content")).isTrue();
        assertThat(data.has("totalElements")).isTrue();
        assertThat(data.has("totalPages")).isTrue();
        assertThat(data.has("number")).isTrue();
        assertThat(data.has("size")).isTrue();
        assertThat(data.path("content").isArray()).isTrue();
    }

    @Test
    @Order(3)
    void listUsers_withPagination() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity(baseUrl() + "?page=0&size=5", JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = res.getBody().path("data");
        assertThat(data.path("size").asInt()).isEqualTo(5);
        assertThat(data.path("number").asInt()).isEqualTo(0);
    }

    @Test
    @Order(4)
    void getUser() {
        createUserIfEmpty();
        ResponseEntity<JsonNode> listRes = restTemplate.getForEntity(baseUrl(), JsonNode.class);
        JsonNode content = listRes.getBody().path("data").path("content");
        if (content.isEmpty()) {
            return;
        }
        long id = content.get(0).path("id").asLong();
        ResponseEntity<JsonNode> res = restTemplate.getForEntity(baseUrl() + "/" + id, JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().path("data").path("id").asLong()).isEqualTo(id);
    }

    @Test
    @Order(5)
    void updateUser() {
        createUserIfEmpty();
        ResponseEntity<JsonNode> listRes = restTemplate.getForEntity(baseUrl(), JsonNode.class);
        JsonNode content = listRes.getBody().path("data").path("content");
        if (content.isEmpty()) {
            return;
        }
        long id = content.get(0).path("id").asLong();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("username", "updated", "email", "updated@example.com"), headers);
        ResponseEntity<JsonNode> res = restTemplate.exchange(baseUrl() + "/" + id, HttpMethod.PUT, entity, JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().path("data").path("username").asText()).isEqualTo("updated");
    }

    @Test
    @Order(6)
    void deleteUser() {
        createUserIfEmpty();
        ResponseEntity<JsonNode> listRes = restTemplate.getForEntity(baseUrl(), JsonNode.class);
        JsonNode content = listRes.getBody().path("data").path("content");
        if (content.isEmpty()) {
            return;
        }
        long id = content.get(0).path("id").asLong();
        ResponseEntity<JsonNode> res = restTemplate.exchange(baseUrl() + "/" + id, HttpMethod.DELETE, null, JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().path("code").asInt()).isEqualTo(200);
    }

    @Test
    void unknownEntity_returns404() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity("http://localhost:" + port + "/api/nonexistent", JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().path("code").asInt()).isEqualTo(404);
    }

    @Test
    void invalidId_returns400() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity(baseUrl() + "/invalid-id", JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().path("code").asInt()).isEqualTo(400);
    }

    private void createUserIfEmpty() {
        JsonNode listData = restTemplate.getForEntity(baseUrl(), JsonNode.class).getBody().path("data");
        if (listData.path("content").isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(baseUrl(),
                    new HttpEntity<>(Map.of("username", "fixture", "email", "f@f.com"), headers),
                    JsonNode.class);
        }
    }
}
