package com.github.lechandonga.dataqualityvalidationservicejava.contract;

import java.util.List;

/**
 * 契约注册请求。
 */
public record ContractRequest(String datasetId, List<FieldSpecRequest> fields) {
}
