package com.zjc.demo.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

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
 * <p>
 * ⚠️ <b>已知遗留红测：</b>{@link #appPropertiesAreExplicit} 在 {@code template}
 * 分支上本就是红的 ——{@code application-prod.yaml} 没有覆盖
 * {@code app.cors.allowed-origins}，该用例却断言生产已收敛。 属于模板既有问题，改动前请先确认是不是自己引起的。
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
	 * 读取属性的<b>原始声明值</b>，跳过 {@code ${...}} 占位符解析。
	 *
	 * <p>
	 * {@link Environment#getProperty(String)} 会做占位符替换，遇到没有默认值、环境变量又没注入的 占位符时会直接抛
	 * {@code PlaceholderResolutionException}。想断言「配的是不是占位符本身」时 这条路走不通——异常没法区分「压根没配这个
	 * key」和「配了但值取不到」。因此这里直接遍历 属性源，只看「有没有这个 key、值长什么样」。
	 *
	 * @param env 环境
	 * @param key 属性名
	 * @return 配置里写的原始字符串，不存在或来源不可枚举时返回 {@code null}
	 */
	private static String rawProperty(Environment env, String key) {
		ConfigurableEnvironment configurable = (ConfigurableEnvironment) env;
		for (PropertySource<?> propertySource : configurable.getPropertySources()) {
			if (propertySource instanceof EnumerablePropertySource<?> enumerable && enumerable.containsProperty(key)) {
				Object value = enumerable.getProperty(key);
				if (value != null) {
					return String.valueOf(value);
				}
			}
		}
		return null;
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
	 * jasypt 加密组件：公共项来自 {@code application-jasypt.yaml}，密钥按环境分开。
	 *
	 * <p>
	 * <b>为什么这条用例必须有：</b>jasypt 的配置漏了不会报错，只会在用到 {@code ENC(...)} 时 突然解不开。而「漏
	 * include」这种失误更隐蔽——{@code application-jasypt.yaml} 写得再对， 不挂进
	 * {@code application.yaml} 的 {@code include} 里就是一堆死配置。
	 */
	@Test
	@DisplayName("jasypt：公共项已加载，密钥按环境分流")
	void jasyptPropertiesAreBound() {
		// 公共项：算法与前后缀必须在两个环境里都读得到
		withProfile("dev", env -> {
			assertThat(env.getProperty("jasypt.encryptor.algorithm")).isEqualTo("PBEWITHHMACSHA512ANDAES_256");
			assertThat(env.getProperty("jasypt.encryptor.property.prefix")).isEqualTo("ENC(");
			assertThat(env.getProperty("jasypt.encryptor.property.suffix")).isEqualTo(")");
			assertThat(env.getProperty("jasypt.encryptor.iv-generator-classname"))
					.isEqualTo("org.jasypt.iv.RandomIvGenerator");
		});
		// dev 的密钥明文写在配置里，本地开箱即用
		withProfile("dev", env -> assertThat(env.getProperty("jasypt.encryptor.password")).isNotBlank());
		// prod 的密钥必须是环境变量占位符，不能在仓库里出现明文。
		// 用 rawProperty 读原始声明值：走 getProperty 的话这个 key 会直接抛
		// PlaceholderResolutionException（环境变量没注入），异常本身并不能说明「配的就是占位符」，
		// 反而把「压根没配」和「配了但没注入」混成同一种失败。
		withProfile("prod", env -> {
			String raw = rawProperty(env, "jasypt.encryptor.password");
			assertThat(raw).isNotBlank().contains("${").contains("JASYPT_ENCRYPTOR_PASSWORD");
			assertThat(raw).doesNotContain("Vortex@2026");
		});
	}
}
