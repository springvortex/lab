package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * {@link WebConfig} 的单元测试。
 *
 * <p>
 * 跨域是「配置写了但没生效」的高发区：{@code @Value} 字段与注册逻辑脱节、
 * {@code allowCredentials} 与 {@code *} 冲突、暴露头漏配导致前端读不到 traceId——
 * 这些都不会在启动时报错，只会在联调时表现为「莫名其妙被浏览器拦截」。
 * 因此这里直接断言注册进 {@link CorsRegistry} 的配置项，而不是只看代码有没有走到。
 *
 * @author jiancai.zhong
 */
class WebConfigTest {

    /**
     * 读取 {@link CorsRegistry} 中已注册的跨域配置。
     *
     * <p>
     * {@code getCorsConfigurations()} 是 protected 的，只能反射调用——这是 Spring 为子类
     * 扩展预留的方法，并没有提供公开的读取入口。
     *
     * @param registry 跨域注册表
     * @return 路径模式到跨域配置的映射
     * @throws Exception 反射调用失败
     */
    @SuppressWarnings("unchecked")
    private static Map<String, CorsConfiguration> readConfigurations(CorsRegistry registry) throws Exception {
        Method getter = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        getter.setAccessible(true);
        return (Map<String, CorsConfiguration>) getter.invoke(registry);
    }

    /**
     * 配置的跨域来源、方法、暴露头、凭证与缓存时长必须全部落到注册表里。
     *
     * @throws Exception 反射调用失败
     */
    @Test
    @DisplayName("跨域配置：各项参数完整落库且路径为 /**")
    void corsConfigurationIsRegisteredCompletely() throws Exception {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", new String[]{"https://app.example.com"});
        ReflectionTestUtils.setField(config, "maxAge", 1800L);
        ReflectionTestUtils.setField(config, "allowCredentials", true);

        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);
        CorsConfiguration cors = readConfigurations(registry).get("/**");

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(cors.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
        assertThat(cors.getExposedHeaders()).containsExactly("X-Trace-Id");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getMaxAge()).isEqualTo(1800L);
    }

    /**
     * 暴露头必须包含 {@code X-Trace-Id}，否则浏览器会屏蔽该响应头、前端拿不到链路 ID。
     *
     * @throws Exception 反射调用失败
     */
    @Test
    @DisplayName("暴露头包含 X-Trace-Id")
    void traceHeaderIsExposed() throws Exception {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", new String[]{"*"});
        ReflectionTestUtils.setField(config, "maxAge", 3600L);
        ReflectionTestUtils.setField(config, "allowCredentials", false);

        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);

        assertThat(readConfigurations(registry).get("/**").getExposedHeaders()).contains("X-Trace-Id");
    }

    /**
     * 默认不注册任何拦截器：模板要保持零业务耦合，这个扩展点必须存在且可被调用。
     *
     * <p>
     * 用子类计数而不是读 {@code getInterceptors()}：后者是 protected、且列表元素类型没有
     * 有意义的断言入口，计数更直接。
     */
    @Test
    @DisplayName("拦截器扩展点：默认不注册任何拦截器")
    void interceptorExtensionPointIsEmptyByDefault() {
        WebConfig config = new WebConfig();
        CountingInterceptorRegistry registry = new CountingInterceptorRegistry();

        config.addInterceptors(registry);

        assertThat(registry.interceptorCount).isZero();
    }

    /**
     * 记录拦截器注册次数的注册表。
     */
    private static final class CountingInterceptorRegistry extends InterceptorRegistry {

        /**
         * 已注册的拦截器数量。
         */
        private int interceptorCount;

        /**
         * 统计注册次数后交给父类处理。
         *
         * @param interceptor 拦截器
         * @return 父类返回的注册对象
         */
        @Override
        public InterceptorRegistration addInterceptor(HandlerInterceptor interceptor) {
            interceptorCount++;
            return super.addInterceptor(interceptor);
        }
    }
}
