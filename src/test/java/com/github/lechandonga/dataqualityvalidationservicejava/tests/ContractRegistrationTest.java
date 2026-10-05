package com.github.lechandonga.dataqualityvalidationservicejava.tests;

import com.github.lechandonga.dataqualityvalidationservicejava.exception.InvalidContractException;
import com.github.lechandonga.dataqualityvalidationservicejava.model.Contract;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldConstraint;
import com.github.lechandonga.dataqualityvalidationservicejava.model.FieldType;
import com.github.lechandonga.dataqualityvalidationservicejava.service.ContractValidator;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessArea;
import com.github.lechandonga.dataqualityvalidationservicejava.support.BusinessAreaLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.contract;
import static com.github.lechandonga.dataqualityvalidationservicejava.support.Fixtures.field;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：契约注册——非法/自相矛盾契约必须被拒绝并说明全部原因，不能静默接受。
 */
@BusinessArea("契约注册与合法性校验")
@ExtendWith(BusinessAreaLogger.class)
class ContractRegistrationTest {

    @Nested
    @DisplayName("畸形契约必须拒绝")
    class Malformed {

        @Test
        @DisplayName("空字段列表被拒绝并说明原因")
        void emptyFields() {
            InvalidContractException ex = assertThrows(InvalidContractException.class,
                    () -> ContractValidator.validate(contract()));
            assertEquals(1, ex.getDetails().size());
            assertTrue(ex.getDetails().get(0).contains("至少声明一个字段"));
        }

        @Test
        @DisplayName("字段缺名、缺类型被分别指出")
        void missingNameAndType() {
            FieldConstraint noname = field(null, FieldType.INTEGER);
            FieldConstraint notype = field("age", null);
            InvalidContractException ex = assertThrows(InvalidContractException.class,
                    () -> ContractValidator.validate(contract(noname, notype)));
            assertTrue(ex.getDetails().stream().anyMatch(d -> d.contains("缺少字段名")));
            assertTrue(ex.getDetails().stream().anyMatch(d -> d.contains("未声明字段类型")));
        }

        @Test
        @DisplayName("自相矛盾的 min>max 区间被拒绝")
        void invertedRange() {
            FieldConstraint age = field("age", FieldType.INTEGER);
            age.setMin(100.0);
            age.setMax(1.0);
            InvalidContractException ex = assertThrows(InvalidContractException.class,
                    () -> ContractValidator.validate(contract(age)));
            assertTrue(ex.getDetails().get(0).contains("自相矛盾"));
        }

        @Test
        @DisplayName("非数值/字符串字段配置区间被拒绝")
        void rangeOnBoolean() {
            FieldConstraint flag = field("flag", FieldType.BOOLEAN);
            flag.setMin(0.0);
            flag.setMax(1.0);
            InvalidContractException ex = assertThrows(InvalidContractException.class,
                    () -> ContractValidator.validate(contract(flag)));
            assertTrue(ex.getDetails().get(0).contains("不允许配置取值区间"));
        }

        @Test
        @DisplayName("时效约束配置在非 TIMESTAMP 字段或阈值非正数被拒绝")
        void badFreshness() {
            FieldConstraint name = field("name", FieldType.STRING);
            name.setMaxAgeMillis(1000L);
            FieldConstraint eventTime = field("eventTime", FieldType.TIMESTAMP);
            eventTime.setMaxAgeMillis(-5L);
            InvalidContractException ex = assertThrows(InvalidContractException.class,
                    () -> ContractValidator.validate(contract(name, eventTime)));
            assertTrue(ex.getDetails().stream().anyMatch(d -> d.contains("只能用于 TIMESTAMP")));
            assertTrue(ex.getDetails().stream().anyMatch(d -> d.contains("必须为正数")));
        }

        @Test
        @DisplayName("字段名重复被拒绝")
        void duplicateFieldNames() {
            InvalidContractException ex = assertThrows(InvalidContractException.class,
                    () -> ContractValidator.validate(
                            contract(field("id", FieldType.STRING), field("id", FieldType.INTEGER))));
            assertTrue(ex.getDetails().stream().anyMatch(d -> d.contains("字段名重复")));
        }

        @Test
        @DisplayName("一份契约里的多处问题一次性全部报出，而不是遇到第一个就返回")
        void reportsAllReasonsAtOnce() {
            List<FieldConstraint> fields = new ArrayList<>();
            fields.add(null);
            FieldConstraint first = field("dup", FieldType.STRING);
            first.setMin(10.0);
            first.setMax(1.0);
            fields.add(first);
            fields.add(field("dup", FieldType.INTEGER));
            fields.add(field(null, null));
            fields.add(field("flag", FieldType.BOOLEAN));
            Contract bad = contract();
            bad.setFields(fields);
            InvalidContractException ex = assertThrows(InvalidContractException.class,
                    () -> ContractValidator.validate(bad));
            assertTrue(ex.getDetails().size() >= 4, "应累计多处问题，实际：" + ex.getDetails());
        }
    }

    @Nested
    @DisplayName("合法契约接受")
    class Valid {

        @Test
        @DisplayName("覆盖五类约束的合法契约通过校验")
        void acceptFullContract() {
            FieldConstraint id = field("id", FieldType.STRING);
            id.setRequired(true);
            id.setUnique(true);
            FieldConstraint age = field("age", FieldType.INTEGER);
            age.setMin(0.0);
            age.setMax(150.0);
            FieldConstraint eventTime = field("eventTime", FieldType.TIMESTAMP);
            eventTime.setMaxAgeMillis(60_000L);
            assertDoesNotThrow(() -> ContractValidator.validate(contract(id, age, eventTime)));
        }
    }
}
