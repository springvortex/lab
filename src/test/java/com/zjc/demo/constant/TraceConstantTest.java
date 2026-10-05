package com.zjc.demo.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TraceConstant} 的单元测试。
 *
 * <p>
 * 常量类没有逻辑，但有两件事值得钉死：常量取值不能被随意改动（改了会让前端与网关的取值约定失效），
 * 以及私有构造器必须真正拦住实例化。
 *
 * @author jiancai.zhong
 */
class TraceConstantTest {

    /**
     * 校验常量取值与日志配置、跨域配置里的字面量保持一致。
     */
    @Test
    @DisplayName("常量取值符合约定")
    void constantsMatchConvention() {
        assertThat(TraceConstant.MDC_KEY).isEqualTo("traceId");
        assertThat(TraceConstant.HEADER_NAME).isEqualTo("X-Trace-Id");
        assertThat(TraceConstant.REQUEST_HEADER_NAME).isEqualTo("X-Trace-Id");
        assertThat(TraceConstant.MAX_LENGTH).isEqualTo(64);
    }

    /**
     * 私有构造器被反射调用时必须抛异常，确保这个「工具类」无法被实例化。
     */
    @Test
    @DisplayName("私有构造器阻止实例化")
    void privateConstructorBlocksInstantiation() throws NoSuchMethodException {
        Constructor<TraceConstant> constructor = TraceConstant.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
