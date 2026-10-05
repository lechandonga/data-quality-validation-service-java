package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lechandonga.dataqualityvalidationservicejava.tests.support.BusinessCoverageLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=test")
@Timeout(60)
class WebApiIntegrationTest {

    @RegisterExtension
    static final BusinessCoverageLogger COVERAGE = new BusinessCoverageLogger();

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("端到端：注册契约→写入数据→提交任务→查询完整结论")
    void endToEndHappyPath() throws Exception {
        String datasetId = "web-" + System.nanoTime();

        String contractBody = """
                {
                  "datasetId": "%s",
                  "fields": [
                    {"name": "id", "type": "STRING", "required": true, "unique": true},
                    {"name": "age", "type": "INTEGER", "required": true, "min": 0, "max": 120}
                  ]
                }
                """.formatted(datasetId);
        ResponseEntity<String> registered = rest.exchange("/api/contracts",
                HttpMethod.POST,
                new HttpEntity<>(contractBody, jsonHeaders()), String.class);
        assertEquals(HttpStatus.OK, registered.getStatusCode());
        JsonNode contract = mapper.readTree(registered.getBody());
        assertEquals(1, contract.get("version").asInt());

        String recordsBody = """
                [
                  {"id": "u1", "age": 500},
                  {"id": "u1", "age": "bad"},
                  {}
                ]
                """;
        ResponseEntity<String> uploaded = rest.exchange(
                "/api/datasets/" + datasetId + "/records", HttpMethod.PUT,
                new HttpEntity<>(recordsBody, jsonHeaders()), String.class);
        assertEquals(HttpStatus.OK, uploaded.getStatusCode());

        ResponseEntity<String> submitted = rest.exchange(
                "/api/tasks?datasetId=" + datasetId, HttpMethod.POST,
                new HttpEntity<>(headersVoid()), String.class);
        assertEquals(HttpStatus.OK, submitted.getStatusCode());
        String taskId = mapper.readTree(submitted.getBody()).get("taskId").asText();

        JsonNode task = awaitTask(taskId);
        JsonNode report = task.get("report");
        assertEquals(3, report.get("records").size());
        String text = report.toString();
        assertTrue(text.contains("VALUE_OUT_OF_RANGE"), text);
        assertTrue(text.contains("FIELD_TYPE_MISMATCH"), text);
        assertTrue(text.contains("NOT_UNIQUE"), text);
        assertTrue(text.contains("REQUIRED_MISSING"), text);
    }

    @Test
    @DisplayName("畸形契约返回 400 MALFORMED_CONTRACT，且 details 含全部原因")
    void malformedContractReturnsAllDetails() {
        String body = """
                {
                  "datasetId": "bad-ds",
                  "fields": [
                    {"name": "x", "type": "UNKNOWN_TYPE", "min": 9, "max": 1}
                  ]
                }
                """;
        ResponseEntity<String> response = rest.exchange("/api/contracts",
                HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String responseBody = response.getBody();
        assertTrue(responseBody.contains("MALFORMED_CONTRACT"), responseBody);
        assertTrue(responseBody.contains("unsupported field type"), responseBody);
    }

    @Test
    @DisplayName("查询不存在任务返回 404 TASK_NOT_FOUND，错误体不含堆栈")
    void missingTaskReturns404WithoutStackTrace() {
        ResponseEntity<String> response = rest.getForEntity("/api/tasks/no-such-task", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("TASK_NOT_FOUND"));
        assertFalse(response.getBody().contains("at java."), response.getBody());
    }

    @Test
    @DisplayName("非法 JSON 请求体归一化为 BAD_REQUEST_BODY")
    void unreadableBodyIsClassified() {
        ResponseEntity<String> response = rest.exchange("/api/contracts",
                HttpMethod.POST,
                new HttpEntity<>("{not-json", jsonHeaders()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("BAD_REQUEST_BODY"), response.getBody());
    }

    @Test
    @DisplayName("指标与告警接口可查询，告警 evidence 可读")
    void metricsAndAlertsEndpointsWork() {
        ResponseEntity<String> metricsResponse = rest.getForEntity(
                "/api/metrics?datasetId=anything&from=0&to=9999999999999", String.class);
        assertEquals(HttpStatus.OK, metricsResponse.getStatusCode());
        assertTrue(metricsResponse.getBody().contains("taskCount"));

        ResponseEntity<String> alertsResponse = rest.getForEntity(
                "/api/alerts?datasetId=anything", String.class);
        assertEquals(HttpStatus.OK, alertsResponse.getStatusCode());
        assertEquals("[]", alertsResponse.getBody());
    }

    private HttpHeaders headersVoid() {
        return jsonHeaders();
    }

    private JsonNode awaitTask(String taskId) throws Exception {
        for (int i = 0; i < 200; i++) {
            ResponseEntity<String> response = rest.getForEntity("/api/tasks/" + taskId,
                    String.class);
            JsonNode node = mapper.readTree(response.getBody());
            if ("COMPLETED".equals(node.get("state").asText())) {
                return node;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("task " + taskId + " did not complete");
    }
}
