package com.github.lechandonga.dataqualityvalidationservicejava.engine;

import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;

/**
 * 松散类型输入到契约类型的归一化转换。
 * 转换失败抛出 {@link CoercionException}，由引擎归一化为 TYPE_COERCION_ERROR。
 */
public final class TypeCoercion {

    private TypeCoercion() {
    }

    public static final class CoercionException extends RuntimeException {
        public CoercionException(String message) {
            super(message);
        }
    }

    public static Object coerce(Object raw, FieldType target) {
        if (raw == null) {
            return null;
        }
        try {
            return switch (target) {
                case STRING -> raw instanceof String s ? s
                        : (raw instanceof Number || raw instanceof Boolean) ? raw.toString()
                        : incompatible(raw, target);
                case INTEGER -> toLong(raw);
                case DECIMAL -> toDouble(raw);
                case BOOLEAN -> toBoolean(raw);
                case TIMESTAMP -> toTimestamp(raw);
            };
        } catch (CoercionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CoercionException("无法将值转换为 " + target);
        }
    }

    private static Long toLong(Object raw) {
        if (raw instanceof Long l) {
            return l;
        }
        if (raw instanceof Integer i) {
            return i.longValue();
        }
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return (long) d;
            }
            throw new CoercionException("数值不是整数");
        }
        if (raw instanceof String s) {
            return Long.parseLong(s.trim());
        }
        return incompatible(raw, FieldType.INTEGER);
    }

    private static Double toDouble(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s) {
            return Double.parseDouble(s.trim());
        }
        return incompatible(raw, FieldType.DECIMAL);
    }

    private static Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            String v = s.trim().toLowerCase();
            if ("true".equals(v) || "false".equals(v)) {
                return Boolean.parseBoolean(v);
            }
            throw new CoercionException("字符串不是合法布尔值");
        }
        return incompatible(raw, FieldType.BOOLEAN);
    }

    private static Long toTimestamp(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            String v = s.trim();
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException ignored) {
                return java.time.Instant.parse(v).toEpochMilli();
            }
        }
        return incompatible(raw, FieldType.TIMESTAMP);
    }

    private static <T> T incompatible(Object raw, FieldType target) {
        throw new CoercionException(
                "值类型 " + raw.getClass().getSimpleName() + " 与契约类型 " + target + " 不兼容");
    }
}
