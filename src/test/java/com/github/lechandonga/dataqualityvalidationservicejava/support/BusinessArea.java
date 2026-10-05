package com.github.lechandonga.dataqualityvalidationservicejava.support;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注测试类覆盖的业务域。配合 {@link BusinessAreaLogger} 在测试日志中
 * 输出“业务 → 用例 → 结果”的覆盖情况。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("business-area")
public @interface BusinessArea {
    /** 业务域名称，例如“契约注册”。 */
    String value();
}
