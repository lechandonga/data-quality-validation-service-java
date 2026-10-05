package com.github.lechandonga.dataqualityvalidationservicejava.tests.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 让单测日志清晰呈现“业务模块 → 用例 → 结果”，并在类结束时汇总该模块覆盖情况。
 */
public class BusinessCoverageLogger implements BeforeAllCallback, BeforeEachCallback,
        AfterEachCallback, AfterAllCallback {

    private static final Map<String, AtomicInteger> GLOBAL_MODULES = new LinkedHashMap<>();

    private String module;
    private int total;
    private int passed;
    private int failed;

    @Override
    public void beforeAll(ExtensionContext context) {
        module = context.getRequiredTestClass().getSimpleName();
        GLOBAL_MODULES.put(module, new AtomicInteger(0));
        System.out.println();
        System.out.println("====== [业务模块] " + module + " 开始：覆盖 "
                + moduleDescription(module) + " ======");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        total++;
        System.out.println("-- [用例] " + context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        boolean success = context.getExecutionException().isEmpty();
        if (success) {
            passed++;
            System.out.println("   [结果] PASS");
        } else {
            failed++;
            System.out.println("   [结果] FAIL -> "
                    + context.getExecutionException().get());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        GLOBAL_MODULES.get(module).set(total);
        System.out.println("====== [业务模块] " + module + " 汇总：用例 " + total
                + "，通过 " + passed + "，失败 " + failed + " ======");
        System.out.println();
    }

    private String moduleDescription(String simpleName) {
        return switch (simpleName) {
            case "ContractRegistrationTest" ->
                    "契约注册：畸形/自相矛盾契约拒绝、多原因聚合、版本演进与 checksum";
            case "ValidationEngineTest" ->
                    "校验引擎：多问题并报、五类约束、规则异常归一化与批次不中断";
            case "ConcurrentTaskTest" ->
                    "并发任务：同数据集并发提交的快照隔离与指标无丢失";
            case "AlertAndReproducibilityTest" ->
                    "指标告警：基线对比、evidence、抖动去重、恢复失活、跨版本历史复现";
            case "WebApiIntegrationTest" ->
                    "HTTP 端到端：契约/数据/任务/指标链路与错误分类";
            default -> simpleName;
        };
    }
}
