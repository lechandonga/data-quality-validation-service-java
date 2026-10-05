package com.github.lechandonga.dataqualityvalidationservicejava.service;

import com.github.lechandonga.dataqualityvalidationservicejava.exception.InvalidContractException;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 契约合法性校验。注册前必须通过；收集全部问题后一次性拒绝，
 * 非法或自相矛盾的契约绝不静默接受。
 */
public final class ContractValidator {

    private ContractValidator() {
    }

    public static void validate(Contract contract) {
        List<String> reasons = new java.util.ArrayList<>();
        if (contract == null) {
            throw new InvalidContractException(List.of("契约内容为空"));
        }
        List<FieldConstraint> fields = contract.getFields();
        if (fields == null || fields.isEmpty()) {
            reasons.add("契约必须至少声明一个字段");
            throw new InvalidContractException(reasons);
        }

        Set<String> names = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            FieldConstraint field = fields.get(i);
            String label = "第 " + (i + 1) + " 个字段";
            if (field == null) {
                reasons.add(label + "定义为空");
                continue;
            }
            String name = field.getName();
            if (name == null || name.isBlank()) {
                reasons.add(label + "缺少字段名");
            } else if (!names.add(name)) {
                reasons.add("字段名重复：" + name);
            }
            if (field.getType() == null) {
                reasons.add(prefix(label, name) + "未声明字段类型");
            }
            validateRange(label, name, field, reasons);
            validateFreshness(label, name, field, reasons);
        }

        if (!reasons.isEmpty()) {
            throw new InvalidContractException(reasons);
        }
    }

    private static void validateRange(String label, String name, FieldConstraint field, List<String> reasons) {
        Double min = field.getMin();
        Double max = field.getMax();
        if (min != null && max != null && min > max) {
            reasons.add(prefix(label, name) + "取值区间自相矛盾：min(" + min + ") > max(" + max + ")");
        }
        FieldType type = field.getType();
        boolean numeric = type == FieldType.INTEGER || type == FieldType.DECIMAL;
        boolean string = type == FieldType.STRING;
        if ((min != null || max != null) && !numeric && !string) {
            reasons.add(prefix(label, name)
                    + "类型为 " + type + "，不允许配置取值区间（仅 INTEGER/DECIMAL/STRING 支持）");
        }
        if (string && min != null && min < 0) {
            reasons.add(prefix(label, name) + "字符串长度下界不能为负数：" + min);
        }
    }

    private static void validateFreshness(String label, String name, FieldConstraint field, List<String> reasons) {
        Long maxAge = field.getMaxAgeMillis();
        if (maxAge != null) {
            if (maxAge <= 0) {
                reasons.add(prefix(label, name) + "时效阈值必须为正数：" + maxAge + " ms");
            }
            if (field.getType() != null && field.getType() != FieldType.TIMESTAMP) {
                reasons.add(prefix(label, name)
                        + "maxAgeMillis 只能用于 TIMESTAMP 字段，当前类型为 " + field.getType());
            }
        }
    }

    private static String prefix(String label, String name) {
        return label + (name == null ? "" : "[" + name + "]");
    }
}
