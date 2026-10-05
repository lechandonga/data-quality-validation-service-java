package com.github.lechandonga.dataqualityvalidationservicejava.web;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationTask;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.metrics.MetricsService;
import com.github.lechandonga.dataqualityvalidationservicejava.task.ReplayResult;
import com.github.lechandonga.dataqualityvalidationservicejava.task.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService taskService;
    private final MetricsService metricsService;
    private final AlertService alertService;

    public TaskController(TaskService taskService, MetricsService metricsService,
                          AlertService alertService) {
        this.taskService = taskService;
        this.metricsService = metricsService;
        this.alertService = alertService;
    }

    @PostMapping("/tasks")
    public Map<String, Object> submit(@RequestParam String datasetId) {
        ValidationTask task = taskService.submit(datasetId);
        return Map.of(
                "taskId", task.taskId(),
                "datasetId", task.datasetId(),
                "state", task.state().name(),
                "contractVersion", task.contractVersion(),
                "contractChecksum", task.contractChecksum(),
                "submittedAtMillis", task.submittedAtMillis());
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> get(@PathVariable String taskId) {
        ValidationTask task = taskService.requireTask(taskId);
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("taskId", task.taskId());
        view.put("datasetId", task.datasetId());
        view.put("state", task.state().name());
        view.put("contractVersion", task.contractVersion());
        view.put("contractChecksum", task.contractChecksum());
        view.put("submittedAtMillis", task.submittedAtMillis());
        view.put("report", task.report());
        return view;
    }

    @PostMapping("/tasks/{taskId}/replay")
    public ReplayResult replay(@PathVariable String taskId) {
        return taskService.replay(taskId);
    }

    @GetMapping("/metrics")
    public Object metrics(@RequestParam String datasetId,
                          @RequestParam long from,
                          @RequestParam long to,
                          @RequestParam(required = false) Integer contractVersion) {
        return metricsService.aggregate(datasetId, from, to, contractVersion);
    }

    @PostMapping("/alerts/evaluate")
    public List<?> evaluate(@RequestParam String datasetId,
                            @RequestParam(defaultValue = "300000") long windowMillis) {
        return alertService.evaluate(datasetId, windowMillis);
    }

    @GetMapping("/alerts")
    public List<?> alerts(@RequestParam String datasetId) {
        return alertService.listAlerts(datasetId);
    }

    @GetMapping("/metrics/reports")
    public Object reports(@RequestParam String datasetId,
                          @RequestParam long from,
                          @RequestParam long to,
                          @RequestParam(required = false) Integer contractVersion) {
        return Map.of("reports",
                metricsService.reports(datasetId, from, to, contractVersion));
    }
}
