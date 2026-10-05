package com.zjc.demo.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

/**
 * 配置文件（{@code application.yaml} + {@code config/application-*.yaml}）的守护测试。
 *
 * <p>
 * <b>为什么要测配置文件：</b>YAML 里的 key 写错、profile 覆盖关系搞反，都不会抛异常，只会让配置
 * 静默失效——超时没生效、接口文档在生产开着，都是上线后才发现的问题。这里用
 * {@link ApplicationContextRunner} 直接加载真实的配置文件并断言取值，把「配置真的生效」
 * 变成一条会红的测试。
 *
 * <p>
 * <b>前置事实（已实测）：</b>{@code active: prod} + {@code include: pub} 时
 * {@code activeProfiles} 顺序为 {@code [pub, prod]}，后者优先，因此
 * {@code application-prod.yaml} 能覆盖 {@code application-pub.yaml}。
 *
 * @author jiancai.zhong
 */
class ProfileConfigTest {

    /**
     * 按指定激活的 profile 加载真实配置文件，把环境交给断言使用。
     *
     * @param activeProfile 要激活的环境 profile（{@code pub} 由 {@code include} 自动叠加）
     * @param consumer      对环境的断言逻辑
     */
    private static void withProfile(String activeProfile, java.util.function.Consumer<Environment> consumer) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + activeProfile)
                .run(context -> consumer.accept(context.getEnvironment()));
    }

    /**
     * 生产环境必须关闭接口文档：接口清单等于系统结构图，属于敏感信息。
     */
    @Test
    @DisplayName("prod：接口文档三项全部关闭")
    void apiDocsAreDisabledInProd() {
        withProfile("prod", env -> {
            assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
            assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
            assertThat(env.getProperty("springdoc.show-actuator")).isEqualTo("false");
        });
    }

    /**
     * dev / test 继承 {@code pub} 里的开启配置，不需要各自再抄一遍。
     */
    @Test
    @DisplayName("dev/test：接口文档继承 pub 的默认开启")
    void apiDocsAreEnabledOutsideProd() {
        withProfile("dev", env -> assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("true"));
        withProfile("test", env -> assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("true"));
    }

    /**
     * 三类超时必须真的绑定到值：配了但不生效，比不配更危险（看起来已经防护了）。
     *
     * <p>
     * 这里同时起到「属性名拼写校验」的作用——三个超时项分属不同的命名空间，且各 Boot 版本
     * 叫法不一致，写错不会报错也不会生效：
     * <ul>
     * <li>Tomcat 连接/长连接：{@code server.tomcat.*}；</li>
     * <li>异步请求：{@code spring.mvc.async.request-timeout}；</li>
     * <li>出站客户端：Boot 3 才有 {@code spring.http.client.*}（Boot 4 起改复数
     * {@code spring.http.clients.*}），<b>Boot 2 没有这个前缀</b>，因此本模板改用自定义键
     * {@code app.http.client.*}，由 {@code RestTemplateConfig} 读取。</li>
     * </ul>
     */
    @Test
    @DisplayName("超时配置：Tomcat / 异步请求 / 出站客户端均已绑定")
    void timeoutPropertiesAreBound() {
        withProfile("prod", env -> {
            assertThat(env.getProperty("server.tomcat.connection-timeout")).isEqualTo("20s");
            assertThat(env.getProperty("server.tomcat.keep-alive-timeout")).isEqualTo("20s");
            assertThat(env.getProperty("spring.mvc.async.request-timeout")).isEqualTo("30s");
            assertThat(env.getProperty("app.http.client.connect-timeout")).isEqualTo("3000");
            assertThat(env.getProperty("app.http.client.read-timeout")).isEqualTo("10000");
        });
    }

    /**
     * profile 的装配顺序决定了覆盖关系，必须把它钉住。
     *
     * <p>
     * 规则是「{@code include} 的横切项按声明顺序在前，{@code active} 的环境项排在最后」，
     * 而 Spring 的取值优先级是<b>后者覆盖前者</b>，因此 environment profile 天然拥有最高优先级。
     * 一旦有人调整 {@code application.yaml} 里 {@code include} 的顺序或删掉某项，
     * 这条断言会先红，避免发生「覆盖静默反向」这类只在生产暴露的问题。
     */
    @Test
    @DisplayName("profile 顺序：include 项在前，active 项在最后（后者优先）")
    void profileOrderIsStable() {
        withProfile("prod", env -> assertThat(env.getActiveProfiles()).containsExactly("pub", "cors", "prod"));
    }

    /**
     * 404 统一治理的两个开关必须同时打开，否则 {@code NoHandlerFoundException} 不会抛出，
     * 未匹配路径会绕过 {@code GlobalExceptionHandler} 返回 Boot 默认错误页。
     *
     * <p>
     * 这是 Boot 2 特有的「配置组合生效」陷阱：只配
     * {@code spring.mvc.throw-exception-if-no-handler-found} 是不够的——
     * 默认的 {@code /**} 静态资源映射会把所有未匹配路径先接住，直接回 404。
     */
    @Test
    @DisplayName("404 治理：抛异常开关与静态资源映射关闭必须同时生效")
    void notFoundHandlingRequiresBothProperties() {
        withProfile("prod", env -> {
            assertThat(env.getProperty("spring.mvc.throw-exception-if-no-handler-found")).isEqualTo("true");
            assertThat(env.getProperty("spring.web.resources.add-mappings")).isEqualTo("false");
        });
    }

    /**
     * 模板自定义项要显式可见：跨域与 Jackson 的开关在配置文件里能直接找到，
     * 且生产的跨域来源被收敛到了具体域名，不再是通配的 {@code *}。
     */
    @Test
    @DisplayName("app.* 自定义配置：跨域与 Jackson 显式化，生产来源已收敛")
    void appPropertiesAreExplicit() {
        withProfile("dev", env -> {
            assertThat(env.getProperty("app.jackson.long-to-string")).isEqualTo("true");
            assertThat(env.getProperty("app.cors.allowed-origins")).isEqualTo("*");
            assertThat(env.getProperty("app.cors.max-age")).isEqualTo("3600");
            assertThat(env.getProperty("app.cors.allow-credentials")).isEqualTo("false");
        });
        // 生产来源必须被真正覆盖掉。这里断言具体值而不是「不等于 *」：
        // 若 pub 写标量、prod 写 YAML 列表，列表会变成 allowed-origins[0]，
        // 标量 "*" 依旧能被读到、覆盖静默失效——这条断言就是为了钉死这种写法。
        withProfile("prod", env ->
                assertThat(env.getProperty("app.cors.allowed-origins")).isEqualTo("https://your-domain.com"));
    }
}
