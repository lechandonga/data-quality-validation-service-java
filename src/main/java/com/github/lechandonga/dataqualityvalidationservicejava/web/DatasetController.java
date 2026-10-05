package com.github.lechandonga.dataqualityvalidationservicejava.web;

import com.github.lechandonga.dataqualityvalidationservicejava.dataset.DatasetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PutMapping("/{datasetId}/records")
    public Map<String, Object> replace(@PathVariable String datasetId,
                                       @RequestBody List<Map<String, Object>> records) {
        int count = datasetService.replaceRecords(datasetId, records);
        return Map.of("datasetId", datasetId, "recordCount", count);
    }

    @GetMapping("/{datasetId}")
    public Map<String, Object> get(@PathVariable String datasetId) {
        return Map.of("datasetId", datasetId, "records", datasetService.getRecords(datasetId));
    }
}
