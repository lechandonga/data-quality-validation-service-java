package com.github.lechandonga.dataqualityvalidationservicejava.bootstrap;

import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractService;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.FieldSpecRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.dataset.DatasetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时种入演示契约与数据（test profile 下不启用）。
 */
@Component
@Profile("!test")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ContractService contractService;
    private final DatasetService datasetService;

    public DemoDataSeeder(ContractService contractService, DatasetService datasetService) {
        this.contractService = contractService;
        this.datasetService = datasetService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        String datasetId = "demo-orders";
        contractService.register(new ContractRequest(datasetId, List.of(
                new FieldSpecRequest("id", "STRING", true, null, null, true, false, null),
                new FieldSpecRequest("amount", "DECIMAL", true, 0.0, 100000.0, false, false, null),
                new FieldSpecRequest("quantity", "INTEGER", true, 1.0, 1000.0, false, false, null),
                new FieldSpecRequest("eventTime", "TIMESTAMP", true, null, null, false,
                        true, 3_600_000L))));

        long now = System.currentTimeMillis();
        datasetService.replaceRecords(datasetId, List.of(
                Map.of("id", "o-1", "amount", 99.5, "quantity", 2, "eventTime", now - 60_000),
                Map.of("id", "o-2", "amount", -5.0, "quantity", 0, "eventTime", "not-a-time"),
                Map.of("id", "o-3", "amount", 200000.0, "quantity", 3, "eventTime", now - 7_200_000),
                Map.of("id", "o-1", "amount", 10.0, "quantity", 1, "eventTime", now - 30_000)));
        log.info("Demo dataset '{}' seeded; submit a task via POST /api/tasks?datasetId={}",
                datasetId, datasetId);
    }
}
