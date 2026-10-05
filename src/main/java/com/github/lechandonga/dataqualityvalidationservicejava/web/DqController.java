package com.github.lechandonga.dataqualityvalidationservicejava.web;

import com.github.lechandonga.dataqualityvalidationservicejava.model.Alert;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Dataset;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationMetric;
import com.github.lechandonga.dataqualityvalidationservicejava.model.ValidationTask;
import com.github.lechandonga.dataqualityvalidationservicejava.service.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.DatasetService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.MetricService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.TaskService;
import com.github.lechandonga.dataqualityvalidationservicejava.web.dto.LoadRecordsRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * 数据质量校验与监控服务 REST 接口。
 */
@RestController
@RequestMapping("/api")
public class DqController {

    private final DatasetService datasetService;
    private final TaskService taskService;
    private final MetricService metricService;
    private final AlertService alertService;
    private final Clock clock;

    public DqController(DatasetService datasetService, TaskService taskService,
                        MetricService metricService, AlertService alertService, Clock clock) {
        this.datasetService = datasetService;
        this.taskService = taskService;
        this.metricService = metricService;
        this.alertService = alertService;
        this.clock = clock;
    }

    @PostMapping("/datasets/{datasetId}")
    public Dataset registerDataset(@PathVariable String datasetId) {
        return datasetService.registerDataset(datasetId);
    }

    @PutMapping("/datasets/{datasetId}/contract")
    public Contract registerContract(@PathVariable String datasetId, @RequestBody Contract contract) {
        return datasetService.registerContract(datasetId, contract);
    }

    @GetMapping("/datasets/{datasetId}/contract")
    public Contract getContract(@PathVariable String datasetId,
                                @RequestParam(required = false) Integer version) {
        return datasetService.getContractVersion(datasetId, version);
    }

    @PostMapping("/datasets/{datasetId}/records")
    public Map<String, Object> loadRecords(@PathVariable String datasetId,
                                           @RequestBody LoadRecordsRequest request) {
        int loaded = datasetService.loadRecords(datasetId, request.getRecords());
        return Map.of("datasetId", datasetId, "loaded", loaded);
    }

    @PostMapping("/datasets/{datasetId}/tasks")
    public ValidationTask submitTask(@PathVariable String datasetId,
                                     @RequestParam(required = false) Integer contractVersion) {
        return taskService.submit(datasetId, contractVersion);
    }

    @GetMapping("/tasks/{taskId}")
    public ValidationTask getTask(@PathVariable String taskId) {
        return taskService.getTask(taskId);
    }

    @GetMapping("/datasets/{datasetId}/metrics")
    public List<ValidationMetric> metrics(@PathVariable String datasetId,
                                          @RequestParam(required = false) Long windowMillis) {
        if (windowMillis == null) {
            return metricService.history(datasetId);
        }
        return metricService.window(datasetId, clock.millis(), windowMillis);
    }

    @GetMapping("/datasets/{datasetId}/alerts")
    public List<Alert> alerts(@PathVariable String datasetId) {
        return alertService.history(datasetId);
    }
}
