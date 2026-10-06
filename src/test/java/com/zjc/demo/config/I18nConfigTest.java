package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * {@link I18nConfig} 的测试：消息源与语言解析器的装配是否真的生效。
 *
 * <p>
 * <b>与 {@code MessageUtilsTest} 的分工：</b>本类只验证「配置本身对不对」——Bean 是否注册、 支持语言清单是否落进解析器、资源文件是否与清单一一对应； 至于「按语言取到的文案对不对」属于
 * {@code MessageUtils} 的职责，放在 {@code util/MessageUtilsTest}。
 *
 * <p>
 * <b>为什么值得单独测：</b>国际化的故障模式几乎全是<b>静默失效</b> ——
 * {@code LocaleResolver} 没设 {@code supportedLocales} 时英文请求照样返回中文且不报错， 只有断言「解析器里确实装着我配置的清单」才能把它钉死，而不是靠请求一看语言来猜。
 *
 * @author jiancai.zhong
 * @see com.zjc.demo.util.MessageUtilsTest
 */
@SpringBootTest
class I18nConfigTest {

	/**
	 * 容器里的语言解析器，由 {@link I18nConfig#localeResolver()} 创建。
	 */
	@Autowired
	private LocaleResolver localeResolver;

	/**
	 * 配置类实例，用于读取生效中的默认语言。
	 */
	@Autowired
	private I18nConfig i18nConfig;

	/**
	 * 容器里的消息源，由 {@link I18nConfig#messageSource()} 创建。
	 */
	@Autowired
	private MessageSource messageSource;

	/**
	 * 默认语言必须显式指定，不能跟着 JVM 走。
	 */
	@Nested
	@DisplayName("默认语言")
	class DefaultLocaleTest {

		/**
		 * 默认语言来自配置 {@code app.i18n.default-locale}（当前配的是中文）。
		 *
		 * <p>
		 * 刻意<b>不</b>用 {@link Locale#getDefault()}。容器里 JVM 默认语言常常是 {@code en_US}
		 * 甚至 {@code POSIX}，若跟着它走，同一份代码在开发机与容器里的默认文案会不一致——
		 * 而默认文案恰恰是给调用方看的兜底信息，必须稳定可预期。
		 */
		@Test
		@DisplayName("取自配置，不跟随 JVM")
		void comesFromConfiguration() {
			assertThat(i18nConfig.getDefaultLocale()).isEqualTo(Locale.CHINESE);
		}

		/**
		 * 默认语言必须是「清单里那一项」本身，不能只是语言相同。
		 *
		 * <p>
		 * 构造时已把配置值归一到 {@link I18nConfig#SUPPORTED_LOCALES} 的元素：
		 * 配 {@code zh-CN} 得到的是清单里的 {@link Locale#CHINESE}，而不是新建的 {@code zh_CN}。
		 * 这样解析器的默认值与清单项是同一个实例，不会出现「默认语言不在清单里」的半吊子状态。
		 */
		@Test
		@DisplayName("归一化：配置值映射到清单内的同一实例")
		void isNormalizedToSupportedEntry() {
			assertThat(I18nConfig.SUPPORTED_LOCALES).contains(i18nConfig.getDefaultLocale());
		}

		/**
		 * 解析器实例必须带默认语言。
		 *
		 * <p>
		 * {@code getDefaultLocale()} 是 protected 的（Spring 为子类扩展预留，没有公开读入口），
		 * 只能反射读取——与 {@code WebConfigTest} 读取 {@code CorsRegistry} 的做法一致。
		 */
		@Test
		@DisplayName("解析器持有该默认语言")
		void resolverCarriesDefaultLocale() {
			AcceptHeaderLocaleResolver resolver = (AcceptHeaderLocaleResolver) localeResolver;

			Locale resolved = ReflectionTestUtils.invokeMethod(resolver, "getDefaultLocale");
			assertThat(resolved).isEqualTo(i18nConfig.getDefaultLocale());
		}
	}

	/**
	 * 默认语言的解析与校验：这些分支不依赖 Spring 容器，直接 {@code new} 出来测。
	 *
	 * <p>
	 * <b>为什么单独一组：</b>{@code app.i18n.default-locale} 是给人填的配置，填错是常态——
	 * 写错语言、写成清单外的值，都必须在<b>启动时</b>炸出来而不是静默回落。
	 * 构造器注入让这些分支能被纯单元测试覆盖，不必为每种错误值起一个 Spring 上下文。
	 */
	@Nested
	@DisplayName("默认语言配置解析")
	class DefaultLocaleParsingTest {

		/**
		 * 区域变体写法归一到清单里的语言级项。
		 */
		@Test
		@DisplayName("带区域的写法归一到语言级")
		void regionalTagIsNormalized() {
			assertThat(new I18nConfig("zh-CN").getDefaultLocale()).isEqualTo(Locale.CHINESE);
			assertThat(new I18nConfig("en-US").getDefaultLocale()).isEqualTo(Locale.ENGLISH);
			assertThat(new I18nConfig("en-GB").getDefaultLocale()).isEqualTo(Locale.ENGLISH);
		}

		/**
		 * 下划线形式（环境变量 / properties 里更常见）同样可解析。
		 */
		@Test
		@DisplayName("下划线写法同样支持")
		void underscoreFormIsAccepted() {
			assertThat(new I18nConfig("zh_CN").getDefaultLocale()).isEqualTo(Locale.CHINESE);
			assertThat(new I18nConfig("en_US").getDefaultLocale()).isEqualTo(Locale.ENGLISH);
		}

		/**
		 * 纯语言写法直接用。
		 */
		@Test
		@DisplayName("纯语言写法直接命中")
		void plainLanguageIsAccepted() {
			assertThat(new I18nConfig("zh").getDefaultLocale()).isEqualTo(Locale.CHINESE);
			assertThat(new I18nConfig("en").getDefaultLocale()).isEqualTo(Locale.ENGLISH);
		}

		/**
		 * 非法标签必须启动即失败，且错误信息带上可用值。
		 */
		@Test
		@DisplayName("非法标签：抛异常并提示可用值")
		void invalidTagIsRejected() {
			assertThatThrownBy(() -> new I18nConfig("!!!")).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("app.i18n.default-locale").hasMessageContaining(I18nConfig.SUPPORTED_LOCALES
							.toString());
		}

		/**
		 * 清单外的语言必须失败。
		 *
		 * <p>
		 * 若放行，会出现「不带头请求返回法文、但法文资源文件并不存在」的静默状态——
		 * 接口照常 200，文案却悄悄回落基名文件。
		 */
		@Test
		@DisplayName("清单外语言：抛异常并提示补资源文件")
		void unsupportedLanguageIsRejected() {
			assertThatThrownBy(() -> new I18nConfig("fr-FR")).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("不在支持清单内").hasMessageContaining("messages_");
		}
	}

	/**
	 * 支持语言清单：这是整块国际化最容易被改坏的地方。
	 */
	@Nested
	@DisplayName("支持语言清单")
	class SupportedLocalesTest {

		/**
		 * 「非默认语言」的清单项必须是语言级——这是 {@code AcceptHeaderLocaleResolver} 兜底的条件。
		 *
		 * <p>
		 * <b>为什么只要求「非默认」语言级：</b>默认语言是<b>可配置</b>的，解析器只在
		 * <b>精确匹配失败</b>时才去找「语言相同且 country 为空」的项做兜底。清单里的默认项
		 * 靠精确匹配命中，本身不受这条约束；但只要某一项成了非默认项，它就必须是语言级——
		 * 把 {@link Locale#ENGLISH} 写成 {@link Locale#US} 后，{@code en-GB} / {@code en-AU}
		 * 找不到精确匹配、又因 country 非空而无法兜底，于是<b>静默回落到默认语言</b>，全程无日志。
		 *
		 * <p>
		 * 因此本模板让清单里<b>每一项</b>都保持语言级：默认语言可以在 {@code zh} / {@code en}
		 * 之间随意切换，而不会出现「换个默认值就静默失效」的情况。
		 */
		@Test
		@DisplayName("非默认语言项必须是语言级（country 为空）")
		void nonDefaultEntriesAreLanguageLevel() {
			for (Locale locale : I18nConfig.SUPPORTED_LOCALES) {
				if (locale.equals(i18nConfig.getDefaultLocale())) {
					continue;
				}
				assertThat(locale.getCountry()).as("非默认语言 %s 必须是语言级，否则区域变体会静默回落默认语言", locale).isEmpty();
			}
		}

		/**
		 * 解析器里的清单必须与 {@link I18nConfig#SUPPORTED_LOCALES} 完全一致。
		 *
		 * <p>
		 * 不调用 {@code setSupportedLocales} 时，Spring 默认只放 {@link Locale#getDefault()} 一项，
		 * 于是「机器中文 → 英文请求也返回中文」。这条断言确保那个 setter 没有被误删。
		 */
		@Test
		@DisplayName("解析器装载了完整清单（防止 setSupportedLocales 被删）")
		void resolverIsPopulated() {
			AcceptHeaderLocaleResolver resolver = (AcceptHeaderLocaleResolver) localeResolver;

			assertThat(resolver.getSupportedLocales()).containsExactlyElementsOf(I18nConfig.SUPPORTED_LOCALES);
		}

		/**
		 * 每一项都必须有对应的 {@code messages_<语言>.properties}。
		 *
		 * <p>
		 * 少一份文件的后果是「该语言下所有文案都回落到中文」，接口 200 但语义全错，
		 * 靠人工点页面很难发现。
		 */
		@Test
		@DisplayName("每项都有对应的资源文件")
		void everyLocaleHasBundle() {
			for (Locale locale : I18nConfig.SUPPORTED_LOCALES) {
				String bundleName = "i18n/messages_" + locale;
				assertThat(I18nConfigTest.class.getClassLoader().getResource(bundleName + ".properties"))
						.as("支持语言 %s 缺少资源文件 %s.properties", locale, bundleName).isNotNull();
			}
		}

		/**
		 * 清单里不能出现重复项，否则「新增语言但重复添加」这种手误无人发现。
		 */
		@Test
		@DisplayName("清单无重复项")
		void hasNoDuplicates() {
			List<Locale> locales = I18nConfig.SUPPORTED_LOCALES;

			assertThat(locales).doesNotHaveDuplicates().isNotEmpty();
		}
	}

	/**
	 * 消息源的类型与三个关键开关。
	 */
	@Nested
	@DisplayName("消息源装配")
	class MessageSourceTest {

		/**
		 * 消息源必须是 {@link ReloadableResourceBundleMessageSource}。
		 *
		 * <p>
		 * 不能换成 Boot 默认的 {@code ResourceBundleMessageSource}：后者没有
		 * {@code setDefaultEncoding}，properties 里的中文会随平台默认编码变化而乱码。
		 */
		@Test
		@DisplayName("类型为 ReloadableResourceBundleMessageSource")
		void hasReloadableType() {
			assertThat(messageSource).isInstanceOf(ReloadableResourceBundleMessageSource.class);
		}

		/**
		 * UTF-8 编码必须显式设置，否则中文乱码只在特定机器上出现。
		 *
		 * <p>
		 * <b>JDK 9 之前 properties 强制 ISO-8859-1</b>，现在虽已放开，但默认编码仍随平台变化，
		 * 显式指定才可移植。
		 *
		 * <p>
		 * {@code getDefaultEncoding()} 同样是 protected，走反射读取。
		 */
		@Test
		@DisplayName("默认编码为 UTF-8")
		void usesUtf8() {
			Object encoding = ReflectionTestUtils.invokeMethod(
					(ReloadableResourceBundleMessageSource) messageSource, "getDefaultEncoding");

			assertThat(encoding).as("中文 properties 必须按 UTF-8 读取").isEqualTo("UTF-8");
		}

		/**
		 * 走一遍真实的取词，验证基名设置正确。
		 *
		 * <p>
		 * <b>基名必须带 {@code classpath:} 前缀</b>：{@code Reloadable} 版本对不带前缀的路径
		 * 有额外的解析规则，写错时不一定报错，只是取不到词。这里用一次真实取词兜住。
		 */
		@Test
		@DisplayName("基名可用：能取到基名文件里的词")
		void basenameResolves() {
			String message = messageSource.getMessage("response.success", null, Locale.SIMPLIFIED_CHINESE);

			assertThat(message).isEqualTo("操作成功");
		}

		/**
		 * {@code fallbackToSystemLocale=false} 的可见后果：清单外的语言直接回落基名文件。
		 *
		 * <p>
		 * 设为 {@code true}（Boot 默认）时，缺 key 会先去找 JVM 默认语言对应的文件，
		 * 在「中英都缺、但系统语言是第三种」的场景下行为更难预期。
		 */
		@Test
		@DisplayName("清单外语言回落基名文件（fallbackToSystemLocale=false）")
		void fallsBackToBaseBundle() {
			assertThat(messageSource.getMessage("response.success", null, Locale.FRANCE)).isEqualTo("操作成功");
		}
	}
}
