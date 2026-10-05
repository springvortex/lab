package com.zjc.demo.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TestStrings} 的单元测试。
 *
 * <p>
 * 这个类是 JDK 8 下 {@code String#repeat} 的替代品，被多处「超长输入」用例依赖
 * （traceId 截断、切面返回值截断）。它的行为必须与 {@code String#repeat} 一致，
 * 否则这些边界用例会悄悄失去意义。
 *
 * @author jiancai.zhong
 */
class TestStringsTest {

    /**
     * 重复次数为 0 或负数时返回空串，与 {@code String#repeat} 保持一致。
     */
    @Test
    @DisplayName("重复 0 次/负数次：返回空串")
    void nonPositiveCountReturnsEmpty() {
        assertThat(TestStrings.repeat("x", 0)).isEmpty();
        assertThat(TestStrings.repeat("x", -1)).isEmpty();
    }

    /**
     * 正常拼接的长度与内容都必须正确，尤其是长串（这是它的唯一用途）。
     */
    @Test
    @DisplayName("重复 n 次：长度与内容正确")
    void repeatsGivenTimes() {
        String result = TestStrings.repeat("ab", 3000);

        assertThat(result).hasSize(6000).startsWith("ababab").endsWith("ababab");
    }

    /**
     * 空内容也不能出异常，长度保持为 0。
     */
    @Test
    @DisplayName("重复空串：仍为空串")
    void emptyTokenStaysEmpty() {
        assertThat(TestStrings.repeat("", 100)).isEmpty();
    }

    /**
     * 私有构造器被反射调用时必须抛异常，确保这个「工具类」无法被实例化。
     */
    @Test
    @DisplayName("私有构造器阻止实例化")
    void privateConstructorBlocksInstantiation() throws NoSuchMethodException {
        Constructor<TestStrings> constructor = TestStrings.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
