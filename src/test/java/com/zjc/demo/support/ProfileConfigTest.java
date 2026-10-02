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
     * 这里同时起到「属性名拼写校验」的作用——Boot 4 起出站超时是复数
     * {@code spring.http.clients.*}，写成单数不会报错也不会生效。
     */
    @Test
    @DisplayName("超时配置：Tomcat / 异步请求 / 出站客户端均已绑定")
    void timeoutPropertiesAreBound() {
        withProfile("prod", env -> {
            assertThat(env.getProperty("server.tomcat.connection-timeout")).isEqualTo("20s");
            assertThat(env.getProperty("server.tomcat.keep-alive-timeout")).isEqualTo("20s");
            assertThat(env.getProperty("spring.mvc.async.request-timeout")).isEqualTo("30s");
            assertThat(env.getProperty("spring.http.clients.connect-timeout")).isEqualTo("3s");
            assertThat(env.getProperty("spring.http.clients.read-timeout")).isEqualTo("10s");
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
