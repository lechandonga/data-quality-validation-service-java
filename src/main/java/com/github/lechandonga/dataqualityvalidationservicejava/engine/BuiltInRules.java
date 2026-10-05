package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldSpec;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.IssueCode;
import com.github.lechandonga.dataqualityvalidationservicejava.domain.ValidationIssue;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 内置五条字段级规则。
 */
public final class BuiltInRules {

    private BuiltInRules() {
    }

    public static final Rule REQUIRED = new Rule() {
        @Override
        public String name() {
            return "REQUIRED";
        }

        @Override
        public boolean appliesTo(RuleContext context) {
            return context.field().required();
        }

        @Override
        public Optional<ValidationIssue> evaluate(RuleContext context) {
            Object value = context.record().get(context.field().name());
            if (value == null || value instanceof String text && text.isBlank()) {
                return Optional.of(ValidationIssue.of(name(), context.field().name(),
                        IssueCode.REQUIRED_MISSING, "required field is missing"));
            }
            return Optional.empty();
        }
    };

    public static final Rule FIELD_TYPE = new Rule() {
        @Override
        public String name() {
            return "FIELD_TYPE";
        }

        @Override
        public boolean appliesTo(RuleContext context) {
            return true;
        }

        @Override
        public Optional<ValidationIssue> evaluate(RuleContext context) {
            FieldSpec spec = context.field();
            Object value = context.record().get(spec.name());
            if (isMissing(value) || matchesType(spec.type(), value)) {
                return Optional.empty();
            }
            return Optional.of(ValidationIssue.of(name(), spec.name(),
                    IssueCode.FIELD_TYPE_MISMATCH,
                    "value is not of declared type " + spec.type()));
        }
    };

    public static final Rule VALUE_RANGE = new Rule() {
        @Override
        public String name() {
            return "VALUE_RANGE";
        }

        @Override
        public boolean appliesTo(RuleContext context) {
            FieldSpec spec = context.field();
            return spec.numeric() && (spec.min() != null || spec.max() != null);
        }

        @Override
        public Optional<ValidationIssue> evaluate(RuleContext context) {
            FieldSpec spec = context.field();
            Object raw = context.record().get(spec.name());
            if (isMissing(raw) || !(raw instanceof Number number)) {
                return Optional.empty();
            }
            double value = number.doubleValue();
            if (spec.min() != null && value < spec.min()) {
                return Optional.of(ValidationIssue.of(name(), spec.name(),
                        IssueCode.VALUE_OUT_OF_RANGE,
                        "value %s is below min %s".formatted(value, spec.min())));
            }
            if (spec.max() != null && value > spec.max()) {
                return Optional.of(ValidationIssue.of(name(), spec.name(),
                        IssueCode.VALUE_OUT_OF_RANGE,
                        "value %s is above max %s".formatted(value, spec.max())));
            }
            return Optional.empty();
        }
    };

    public static final Rule UNIQUENESS = new Rule() {
        @Override
        public String name() {
            return "UNIQUENESS";
        }

        @Override
        public boolean appliesTo(RuleContext context) {
            return context.field().unique();
        }

        @Override
        public Optional<ValidationIssue> evaluate(RuleContext context) {
            String field = context.field().name();
            Object value = context.record().get(field);
            if (isMissing(value)) {
                return Optional.empty();
            }
            if (context.duplicateIndex().isDuplicate(field, value)) {
                return Optional.of(ValidationIssue.of(name(), field,
                        IssueCode.NOT_UNIQUE,
                        "value '%s' is duplicated within the dataset".formatted(value)));
            }
            return Optional.empty();
        }
    };

    public static final Rule FRESHNESS = new Rule() {
        @Override
        public String name() {
            return "FRESHNESS";
        }

        @Override
        public boolean appliesTo(RuleContext context) {
            FieldSpec spec = context.field();
            return spec.type() == FieldType.TIMESTAMP
                    && spec.freshnessCheck()
                    && spec.maxAgeMillis() != null;
        }

        @Override
        public Optional<ValidationIssue> evaluate(RuleContext context) {
            FieldSpec spec = context.field();
            Object raw = context.record().get(spec.name());
            if (isMissing(raw)) {
                return Optional.empty();
            }
            Long epochMillis = toEpochMillis(raw);
            if (epochMillis == null) {
                return Optional.empty();
            }
            long age = context.evaluatedAtMillis() - epochMillis;
            if (age > spec.maxAgeMillis()) {
                return Optional.of(ValidationIssue.of(name(), spec.name(),
                        IssueCode.FRESHNESS_VIOLATION,
                        "timestamp is %s ms old, exceeds max age %s ms"
                                .formatted(age, spec.maxAgeMillis())));
            }
            return Optional.empty();
        }
    };

    public static boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    public static Long toEpochMillis(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Instant.parse(text.trim()).toEpochMilli();
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean matchesType(FieldType type, Object value) {
        return switch (type) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer
                    || value instanceof Long
                    || (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())
                    && !(value instanceof Float) && !(value instanceof Double));
            case DECIMAL -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case TIMESTAMP -> value instanceof Number || (value instanceof String text
                    && InstantParser.canParse(text.trim()));
        };
    }

    private static final class InstantParser {
        static boolean canParse(String text) {
            try {
                Instant.parse(text);
                return true;
            } catch (DateTimeParseException ex) {
                return false;
            }
        }
    }
}
