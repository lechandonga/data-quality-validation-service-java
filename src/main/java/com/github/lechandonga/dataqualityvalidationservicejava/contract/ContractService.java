package com.github.lechandonga.dataqualityvalidationservicejava.contract;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ErrorCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldSpec;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationException;
import com.github.lechandonga.dataqualityvalidationservicejava.store.DatasetEntity;
import com.github.lechandonga.dataqualityvalidationservicejava.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 契约注册：所有非法/自相矛盾问题一次性收集后拒绝，绝不静默接受；
 * 发布的契约不可变，版本递增，并按 checksum 拒绝完全重复的注册。
 */
@Service
public class ContractService {

    private final InMemoryStore store;
    private final java.time.Clock clock;

    public ContractService(InMemoryStore store, java.time.Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Contract register(ContractRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null || request.datasetId() == null || request.datasetId().isBlank()) {
            throw new ValidationException(ErrorCode.MALFORMED_CONTRACT,
                    "datasetId must not be blank");
        }
        String datasetId = request.datasetId().trim();

        List<FieldSpec> specs = parseFields(request.fields(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.MALFORMED_CONTRACT,
                    "contract is invalid: " + errors.size() + " problem(s) found", errors);
        }

        String checksum = checksum(specs);
        DatasetEntity dataset = store.getOrCreateDataset(datasetId);
        dataset.lock().lock();
        try {
            Contract current = dataset.currentContract();
            if (current != null && current.checksum().equals(checksum)) {
                throw new ValidationException(ErrorCode.DUPLICATE_CONTRACT,
                        "contract is identical to current version " + current.version()
                                + " (checksum " + checksum + ") and was not accepted",
                        List.of("identical re-registration would be a silent no-op"));
            }
            int nextVersion = current == null ? 1 : current.version() + 1;
            Contract contract = new Contract(datasetId, nextVersion, specs, checksum,
                    clock.millis());
            dataset.addContract(contract);
            return contract;
        } finally {
            dataset.lock().unlock();
        }
    }

    private List<FieldSpec> parseFields(List<FieldSpecRequest> rawFields, List<String> errors) {
        if (rawFields == null || rawFields.isEmpty()) {
            errors.add("fields must contain at least one field");
            return List.of();
        }
        Set<String> names = new HashSet<>();
        List<FieldSpec> specs = new ArrayList<>();
        for (int i = 0; i < rawFields.size(); i++) {
            FieldSpecRequest raw = rawFields.get(i);
            String location = "fields[" + i + "]";
            if (raw == null) {
                errors.add(location + ": field definition must not be null");
                continue;
            }
            String name = raw.name() == null ? "" : raw.name().trim();
            if (name.isEmpty()) {
                errors.add(location + ": field name must not be blank");
            } else if (!names.add(name)) {
                errors.add(location + ": duplicate field name '" + name + "'");
            }

            FieldType type = FieldType.fromName(raw.type()).orElse(null);
            if (raw.type() == null || raw.type().isBlank()) {
                errors.add(location + " (" + name + "): field type must not be blank");
            } else if (type == null) {
                errors.add(location + " (" + name + "): unsupported field type '"
                        + raw.type() + "'; supported: STRING, INTEGER, DECIMAL, BOOLEAN, TIMESTAMP");
            }

            boolean hasMin = raw.min() != null;
            boolean hasMax = raw.max() != null;
            if (type != null && (hasMin || hasMax) && !(type == FieldType.INTEGER
                    || type == FieldType.DECIMAL)) {
                errors.add(location + " (" + name + "): min/max are only valid on INTEGER/DECIMAL fields");
            }
            if (hasMin && hasMax && raw.min() > raw.max()) {
                errors.add(location + " (" + name + "): min " + raw.min()
                        + " must not be greater than max " + raw.max());
            }

            boolean freshness = Boolean.TRUE.equals(raw.freshnessCheck());
            if (type != null && freshness && type != FieldType.TIMESTAMP) {
                errors.add(location + " (" + name
                        + "): freshnessCheck is only valid on TIMESTAMP fields");
            }
            if (freshness && (raw.maxAgeMillis() == null || raw.maxAgeMillis() <= 0)) {
                errors.add(location + " (" + name
                        + "): freshnessCheck requires a positive maxAgeMillis");
            }
            if (!freshness && raw.maxAgeMillis() != null) {
                errors.add(location + " (" + name
                        + "): maxAgeMillis is set while freshnessCheck is not enabled"
                        + " (contradictory constraint would be silently ignored)");
            }

            if (type != null && !name.isEmpty() && names.contains(name)) {
                specs.add(buildSpec(name, type, raw, freshness));
            } else if (type != null && !name.isEmpty()) {
                specs.add(buildSpec(name, type, raw, freshness));
            }
        }
        return specs;
    }

    private FieldSpec buildSpec(String name, FieldType type, FieldSpecRequest raw,
                                boolean freshness) {
        return new FieldSpec(name, type,
                Boolean.TRUE.equals(raw.required()),
                raw.min(), raw.max(),
                Boolean.TRUE.equals(raw.unique()),
                freshness,
                freshness ? raw.maxAgeMillis() : null);
    }

    public Contract requireCurrent(String datasetId) {
        DatasetEntity dataset = store.dataset(datasetId);
        if (dataset == null || dataset.currentContract() == null) {
            throw new ValidationException(ErrorCode.CONTRACT_NOT_FOUND,
                    "no contract registered for dataset '" + datasetId + "'");
        }
        return dataset.currentContract();
    }

    public Contract requireVersion(String datasetId, int version) {
        DatasetEntity dataset = store.dataset(datasetId);
        Contract contract = dataset == null ? null : dataset.contractVersion(version);
        if (contract == null) {
            throw new ValidationException(ErrorCode.CONTRACT_NOT_FOUND,
                    "contract version " + version + " not found for dataset '" + datasetId + "'");
        }
        return contract;
    }

    public List<Contract> listVersions(String datasetId) {
        DatasetEntity dataset = store.dataset(datasetId);
        if (dataset == null || dataset.allContracts().isEmpty()) {
            throw new ValidationException(ErrorCode.CONTRACT_NOT_FOUND,
                    "no contract registered for dataset '" + datasetId + "'");
        }
        return dataset.allContracts();
    }

    public static String checksum(List<FieldSpec> specs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (FieldSpec spec : specs) {
                String line = String.join("|",
                        spec.name(),
                        spec.type().name(),
                        String.valueOf(spec.required()),
                        String.valueOf(spec.min()),
                        String.valueOf(spec.max()),
                        String.valueOf(spec.unique()),
                        String.valueOf(spec.freshnessCheck()),
                        String.valueOf(spec.maxAgeMillis()));
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
