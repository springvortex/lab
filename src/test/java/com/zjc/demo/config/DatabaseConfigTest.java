package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

/**
 * 数据库配置（{@code config/application-db.yaml}）的守护测试。
 *
 * <p>
 * YAML 里的 key 写错、{@code include} 漏配、profile 覆盖关系搞反，都不会抛异常，
 * 只会让配置静默失效。分页没开、连接池参数没生效，这类问题上线后才会以
 * 「性能差 / 连接耗尽」的形式冒出来。这里用 {@link ApplicationContextRunner}
 * 直接加载真实配置文件并断言取值，把「配置真的生效」变成一条会红的测试。
 *
 * @author jiancai.zhong
 */
class DatabaseConfigTest {

	/**
	 * 按指定激活的 profile 加载真实配置文件。
	 *
	 * @param activeProfile 要激活的环境 profile（{@code pub / cors / db} 由 {@code include} 叠加）
	 * @param consumer      对环境的断言逻辑
	 */
	private static void withProfile(String activeProfile, Consumer<Environment> consumer) {
		new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
				.withPropertyValues("spring.profiles.active=" + activeProfile)
				.run(context -> consumer.accept(context.getEnvironment()));
	}

	/**
	 * {@code application-db.yaml} 被 {@code include} 成功叠加：数据源、连接池、MyBatis-Plus 三项都能读到。
	 */
	@Test
	@DisplayName("db profile：数据源与连接池配置生效")
	void datasourceIsLoadedFromDbProfile() {
		withProfile("dev", env -> {
			assertThat(env.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.postgresql.Driver");
			assertThat(env.getProperty("spring.datasource.url")).contains("jdbc:postgresql:");
			assertThat(env.getProperty("spring.datasource.hikari.pool-name")).isEqualTo("VortexHikari");
			assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("20");
		});
	}

	/**
	 * 连接池的超时约束：校验超时必须小于连接超时，否则先超时的是池本身。
	 */
	@Test
	@DisplayName("db profile：Hikari 超时参数自洽")
	void hikariTimeoutsAreConsistent() {
		withProfile("dev", env -> {
			// Hikari 的超时是 long 毫秒，不是 Duration 字符串——写 "30s" 会启动失败
			assertThat(env.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("30000");
			assertThat(env.getProperty("spring.datasource.hikari.validation-timeout")).isEqualTo("5000");
			assertThat(env.getProperty("spring.datasource.hikari.max-lifetime")).isEqualTo("1800000");
		});
	}

	/**
	 * MyBatis-Plus 的关键开关：主键策略、逻辑删除字段与取值。
	 */
	@Test
	@DisplayName("db profile：MyBatis-Plus 主键与逻辑删除配置生效")
	void mybatisPlusGlobalConfigIsLoaded() {
		withProfile("dev", env -> {
			assertThat(env.getProperty("mybatis-plus.global-config.db-config.id-type")).isEqualTo("assign_id");
			assertThat(env.getProperty("mybatis-plus.global-config.db-config.logic-delete-field"))
					.isEqualTo("deleted");
			assertThat(env.getProperty("mybatis-plus.global-config.db-config.logic-delete-value")).isEqualTo("1");
			assertThat(env.getProperty("mybatis-plus.global-config.db-config.logic-not-delete-value"))
					.isEqualTo("0");
		});
	}

	/**
	 * 环境变量能覆盖文件里的默认值，换库不必改配置文件。
	 */
	@Test
	@DisplayName("dev：环境变量可覆盖默认数据源")
	void environmentVariablesOverrideDefaults() {
		new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
				.withPropertyValues("spring.profiles.active=dev", "DB_URL=jdbc:postgresql://db.internal:5432/app",
						"DB_USERNAME=app", "DB_PASSWORD=secret")
				.run(context -> {
					Environment env = context.getEnvironment();
					assertThat(env.getProperty("spring.datasource.url"))
							.isEqualTo("jdbc:postgresql://db.internal:5432/app");
					assertThat(env.getProperty("spring.datasource.username")).isEqualTo("app");
					assertThat(env.getProperty("spring.datasource.password")).isEqualTo("secret");
				});
	}
}
