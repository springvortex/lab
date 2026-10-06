package com.zjc.demo.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import com.zjc.demo.config.I18nConfig;

/**
 * 配置文件（{@code application.yaml} + {@code config/application-*.yaml}）的守护测试。
 *
 * <p>
 * <b>为什么要测配置文件：</b>YAML 里的 key 写错、profile 覆盖关系搞反，都不会抛异常，只会让配置
 * 静默失效——超时没生效、接口文档在生产开着，都是上线后才发现的问题。这里用 {@link ApplicationContextRunner}
 * 直接加载真实的配置文件并断言取值，把「配置真的生效」 变成一条会红的测试。
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
		new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
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
	 * 模板自定义项要显式可见：跨域与 Jackson 的开关在配置文件里能直接找到， 且生产的跨域来源被收敛到了具体域名，不再是通配的 {@code *}。
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
		withProfile("prod",
				env -> assertThat(env.getProperty("app.cors.allowed-origins")).isEqualTo("https://your-domain.com"));
	}

	/**
	 * 国际化配置独立成 {@code i18n} 附加 profile，并真正被 {@code include} 叠加上。
	 *
	 * <p>
	 * 这条断言钉两件事：一是 {@code config/application-i18n.yaml} 里的属性名没写错（写错不报错、
	 * 只是静默失效），二是 {@code application.yaml} 的 {@code include} 里确实有 {@code i18n}——
	 * 少了它，国际化配置整体不加载，而 {@code I18nConfig} 代码内有同名默认值兜着，
	 * <b>测试与冒烟都不会红</b>，属于典型静默失效。因此必须显式断言「配置文件生效」。
	 */
	@Test
	@DisplayName("i18n：资源基名 / 编码 / 兜底策略 / 语言解析均已从配置文件绑定")
	void i18nPropertiesAreBound() {
		withProfile("dev", env -> {
			// 这一项与下面几项不同：它是真的被读取的（I18nConfig 用 @Value 注入），
			// 写错会直接启动失败。这里断言的是「解析后落在支持清单内」而不是字面值——
			// zh-CN / zh_CN / zh 三种写法等价，钉死字面值会让改个写法就红，属于脆断言。
			String configured = env.getProperty("app.i18n.default-locale");
			assertThat(configured).as("app.i18n.default-locale 必须有值").isNotBlank();
			Locale parsed = Locale.forLanguageTag(configured.trim().replace('_', '-'));
			assertThat(I18nConfig.SUPPORTED_LOCALES).as("配置值 %s 必须落在支持清单内", configured)
					.anyMatch(supported -> supported.getLanguage().equals(parsed.getLanguage()));
			assertThat(env.getProperty("spring.messages.basename")).isEqualTo("i18n/messages");
			assertThat(env.getProperty("spring.messages.encoding")).isEqualTo("UTF-8");
			assertThat(env.getProperty("spring.messages.fallback-to-system-locale")).isEqualTo("false");
			assertThat(env.getProperty("spring.messages.use-code-as-default-message")).isEqualTo("true");
			assertThat(env.getProperty("spring.web.locale-resolver")).isEqualTo("accept-header");
		});
	}
}
