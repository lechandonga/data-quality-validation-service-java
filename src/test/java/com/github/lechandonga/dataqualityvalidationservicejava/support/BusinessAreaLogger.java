package com.github.lechandonga.dataqualityvalidationservicejava.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试日志扩展：类级别打印业务域开始/结束与用例清单，
 * 方法级别打印每个用例的通过/失败，方便从日志核对业务覆盖情况。
 */
public class BusinessAreaLogger implements BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger("BUSINESS-COVERAGE");
    private final List<String> caseResults = new ArrayList<>();
    private String businessArea = "(未标注业务域)";

    @Override
    public void beforeAll(ExtensionContext context) {
        caseResults.clear();
        context.getTestClass().flatMap(c ->
                java.util.Optional.ofNullable(c.getAnnotation(BusinessArea.class)))
                .ifPresent(a -> businessArea = a.value());
        log.info("════════ 业务域开始：{}  测试类：{} ════════",
                businessArea, context.getRequiredTestClass().getSimpleName());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        log.info("▶ [{}] 开始用例：{}", businessArea, context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        boolean failed = context.getExecutionException().isPresent();
        String result = failed ? "失败 ✗" : "通过 ✓";
        log.info("■ [{}] 用例{}：{}", businessArea, result, context.getDisplayName());
        caseResults.add(context.getDisplayName() + " => " + result);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        long failed = caseResults.stream().filter(s -> s.contains("失败")).count();
        log.info("════════ 业务域结束：{}  用例数={}  通过={}  失败={} ════════",
                businessArea, caseResults.size(), caseResults.size() - failed, failed);
        caseResults.forEach(c -> log.info("    · [{}] {}", businessArea, c));
    }
}
