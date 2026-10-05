package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationTask;
import com.github.lechandonga.dataqualityvalidationservicejava.service.DatasetService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.field;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 业务：HTTP 接入面——契约注册、任务提交/查询、指标/告警查询、错误码归一化的全链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessArea("REST 接口与错误分类")
@org.junit.jupiter.api.extension.ExtendWith(
        com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessAreaLogger.class)
class WebApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InMemoryStore store;
    @Autowired
    private DatasetService datasetService;

    @BeforeEach
    void clean() {
        store.datasets().clear();
        store.tasks().clear();
    }

    @Test
    @DisplayName("注册数据集→契约→数据→提交任务→查询结果→查询指标 的完整链路")
    void fullHappyPath() throws Exception {
        mockMvc.perform(post("/api/datasets/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetId").value("orders"));

        FieldConstraint amount = field("amount", FieldType.DECIMAL);
        amount.setRequired(true);
        amount.setMin(0.0);
        amount.setMax(1000.0);
        Map<String, Object> contractBody = new LinkedHashMap<>();
        contractBody.put("fields", List.of(toMap(amount)));
        mockMvc.perform(put("/api/datasets/orders/contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contractBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        Map<String, Object> record = Map.of("recordId", "row-1",
                "fields", Map.of("amount", 5000));
        mockMvc.perform(post("/api/datasets/orders/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("records", List.of(record)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(1));

        String response = mockMvc.perform(post("/api/datasets/orders/tasks"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(response).get("taskId").asText();

        ValidationTask task = waitForTask(taskId);
        org.junit.jupiter.api.Assertions.assertEquals(
                com.github.lechandonga.dataqualityvalidationservicejava.model.TaskStatus.COMPLETED,
                task.getStatus());

        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.violations[0].constraint").value("RANGE"));

        mockMvc.perform(get("/api/datasets/orders/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].violationCount").value(1));
        mockMvc.perform(get("/api/datasets/orders/alerts")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("畸形契约（min>max）返回 400、INVALID_CONTRACT 且明细说明矛盾点")
    void malformedContractReturnsDetailedError() throws Exception {
        datasetService.registerDataset("bad-contract");
        FieldConstraint age = field("age", FieldType.INTEGER);
        age.setMin(100.0);
        age.setMax(1.0);
        String body = objectMapper.writeValueAsString(Map.of("fields", List.of(toMap(age))));

        mockMvc.perform(put("/api/datasets/bad-contract/contract")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CONTRACT"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("自相矛盾")));
    }

    @Test
    @DisplayName("非法 JSON 请求体归一化为 MALFORMED_REQUEST，不透出 Jackson 原始信息")
    void malformedJsonNormalized() throws Exception {
        datasetService.registerDataset("bad-json");
        mockMvc.perform(put("/api/datasets/bad-json/contract")
                        .contentType(MediaType.APPLICATION_JSON).content("{ not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("不存在的任务/数据集返回 404 与 NOT_FOUND")
    void notFoundMapped() throws Exception {
        mockMvc.perform(get("/api/tasks/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mockMvc.perform(post("/api/datasets/ghost/tasks"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("缺少 recordId 的数据批次返回 400 与 INVALID_RECORD")
    void recordWithoutIdRejected() throws Exception {
        datasetService.registerDataset("no-id");
        FieldConstraint age = field("age", FieldType.INTEGER);
        datasetService.registerContract("no-id",
                com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.contract(age));
        String body = objectMapper.writeValueAsString(
                Map.of("records", List.of(Map.of("fields", Map.of("age", 1)))));
        mockMvc.perform(post("/api/datasets/no-id/records")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RECORD"));
    }

    private ValidationTask waitForTask(String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            String json = mockMvc.perform(get("/api/tasks/{id}", taskId))
                    .andReturn().getResponse().getContentAsString();
            String status = objectMapper.readTree(json).get("status").asText();
            if (!"PENDING".equals(status)) {
                return objectMapper.readValue(json, ValidationTask.class);
            }
            Thread.sleep(20);
        }
        throw new AssertionError("任务未在时限内完成：" + taskId);
    }

    private static Map<String, Object> toMap(FieldConstraint c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", c.getName());
        map.put("type", c.getType());
        map.put("required", c.isRequired());
        map.put("unique", c.isUnique());
        if (c.getMin() != null) {
            map.put("min", c.getMin());
        }
        if (c.getMax() != null) {
            map.put("max", c.getMax());
        }
        if (c.getMaxAgeMillis() != null) {
            map.put("maxAgeMillis", c.getMaxAgeMillis());
        }
        return map;
    }
}
