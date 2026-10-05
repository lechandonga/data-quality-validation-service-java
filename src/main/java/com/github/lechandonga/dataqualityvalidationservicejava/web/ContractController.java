package com.github.lechandonga.dataqualityvalidationservicejava.web;

import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractRequest;
import com.github.lechandonga.dataqualityvalidationservicejava.contract.ContractService;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping
    public Contract register(@RequestBody ContractRequest request) {
        return contractService.register(request);
    }

    @GetMapping("/{datasetId}")
    public List<Contract> listVersions(@PathVariable String datasetId) {
        return contractService.listVersions(datasetId);
    }

    @GetMapping("/{datasetId}/versions/{version}")
    public Contract getVersion(@PathVariable String datasetId, @PathVariable int version) {
        return contractService.requireVersion(datasetId, version);
    }
}
