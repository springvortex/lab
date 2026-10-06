package com.zjc.demo.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

import com.zjc.demo.config.I18nConfig;

/**
 * {@link MessageUtils} 的单元测试。
 *
 * <p>
 * <b>本类的核心价值：把「静默失效」变成「会红的测试」。</b> 国际化最常见的故障不是抛异常，而是
 * <b>语言没切过去但什么都不报</b>：
 * <ul>
 * <li>某个语言缺 key → 悄悄回落到基名文件（中文），响应 200 但语言错了；</li>
 * <li>properties 文件编码写错 → 中文变乱码。</li>
 * </ul>
 * 这两条都有对应用例钉死，且不依赖「看响应猜语言」。
 *
 * <p>
 * <b>与 {@code config.I18nConfigTest} 的分工：</b>本类只负责「按 key + 语言取到的文案对不对」；
 * 语言解析器与消息源的<b>装配</b>（清单是否落进解析器、编码与兜底开关）由该测试类验证。
 *
 * @author jiancai.zhong
 * @see com.zjc.demo.config.I18nConfig
 */
@SpringBootTest
class MessageUtilsTest {

	/**
	 * 参与一致性校验的资源文件后缀，用于核对「各语言 key 集合一致」。
	 *
	 * <p>
	 * <b>必须是语言级（无国家/地区）</b>，与 {@link I18nConfig#SUPPORTED_LOCALES} 保持一致。
	 * 写成 {@code en_US} 会导致资源文件命名与清单粒度不匹配，进而出现
	 * 「解析器归一到 {@code en}、却没有 {@code messages_en.properties}」的空转。
	 */
	private static final List<String> EXPECTED_BUNDLE_SUFFIXES = List.of("zh", "en");

	/**
	 * 容器里的消息源，由 {@code I18nConfig} 创建。
	 */
	@Autowired
	private MessageSource messageSource;

	/**
	 * 每个用例结束后复位语言，避免语言串到其他用例。
	 */
	@AfterEach
	void resetLocale() {
		LocaleContextHolder.resetLocaleContext();
	}

	/**
	 * 取词：默认语言（中文）与英文两组文案。
	 */
	@Nested
	@DisplayName("按语言取词")
	class ResolveByLocale {

		/**
		 * 中文环境下取到中文文案。
		 */
		@Test
		@DisplayName("zh_CN：取到中文文案")
		void returnsChineseForSimplifiedChinese() {
			LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

			assertThat(MessageUtils.getMessage("response.success")).isEqualTo("操作成功");
			assertThat(MessageUtils.getMessage("response.not-found")).isEqualTo("资源不存在");
		}

		/**
		 * 英文环境下取到英文文案——这一条直接证明「语言真的能切」。
		 */
		@Test
		@DisplayName("en：取到英文文案")
		void returnsEnglishForEnglish() {
			LocaleContextHolder.setLocale(Locale.ENGLISH);

			assertThat(MessageUtils.getMessage("response.success")).isEqualTo("Success");
			assertThat(MessageUtils.getMessage("response.not-found")).isEqualTo("Resource not found");
		}

		/**
		 * 国家/地区级 Locale（{@code en_US} / {@code en_GB}）回落到语言级资源文件。
		 *
		 * <p>
		 * {@code ResourceBundle} 的查找链是「精确 → 语言 → 基名」：问 {@code en_US} 时先找
		 * {@code messages_en_US.properties}，没有就找 {@code messages_en.properties}，命中。
		 * 因此资源文件用语言级命名，能同时覆盖所有英文区域变体。
		 *
		 * <p>
		 * <b>与解析器的分工要分清：</b>{@code AcceptHeaderLocaleResolver} 负责把请求头
		 * <b>归一</b>到清单内的值（这一步有它自己的语言级兜底逻辑，且要求清单写语言级），
		 * 消息源则负责按拿到的值沿查找链找文件。两者都需要「语言级」这一粒度才能配合正确，
		 * 细节见 {@link I18nConfig#SUPPORTED_LOCALES}。
		 */
		@Test
		@DisplayName("国家/地区级 en_US / en_GB：沿查找链回落到语言级资源文件")
		void regionalLocaleFallsBackToLanguageBundle() {
			assertThat(MessageUtils.getMessage("response.success", Locale.US)).isEqualTo("Success");
			assertThat(MessageUtils.getMessage("response.success", Locale.UK)).isEqualTo("Success");
		}

		/**
		 * 清单外的语言（如法语）回落到基名文件（中文），而不是抛异常或返回 key。
		 */
		@Test
		@DisplayName("不支持的语言回落到基名文件")
		void unsupportedLocaleFallsBackToBaseBundle() {
			assertThat(MessageUtils.getMessage("response.success", Locale.FRANCE)).isEqualTo("操作成功");
		}
	}

	/**
	 * 占位符填充。
	 */
	@Nested
	@DisplayName("占位符")
	class Placeholder {

		/**
		 * 同一条 key 在不同语言下语序不同，靠 {@code {0}} 占位符表达。
		 *
		 * <p>
		 * 中文「POST 方法不支持」与英文「POST method is not supported」的参数位置一致，
		 * 但真实项目中经常不一致——用占位符而不是字符串拼接，才能在翻译时自由调整语序。
		 */
		@Test
		@DisplayName("占位符参数按语言填充")
		void argumentsAreInterpolated() {
			LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
			assertThat(MessageUtils.getMessage("response.method-not-allowed", "POST")).isEqualTo("POST 方法不支持");

			LocaleContextHolder.setLocale(Locale.ENGLISH);
			assertThat(MessageUtils.getMessage("response.method-not-allowed", "POST"))
					.isEqualTo("POST method is not supported");
		}

		/**
		 * 缺少必填参数的提示同样带占位符。
		 */
		@Test
		@DisplayName("缺少参数提示带参数名")
		void missingParameterIncludesName() {
			LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
			assertThat(MessageUtils.getMessage("request.missing-parameter", "name")).isEqualTo("缺少必填参数: name");

			LocaleContextHolder.setLocale(Locale.ENGLISH);
			assertThat(MessageUtils.getMessage("request.missing-parameter", "name"))
					.isEqualTo("Missing required parameter: name");
		}
	}

	/**
	 * 缺失 key 与异常路径的兜底。
	 */
	@Nested
	@DisplayName("兜底行为")
	class Fallback {

		/**
		 * key 不存在时返回 key 本身，而不是抛 {@code NoSuchMessageException}。
		 *
		 * <p>
		 * 依据 {@code I18nConfig} 里 {@code setUseCodeAsDefaultMessage(true)}。文案缺失是配置问题，
		 * 不该把业务请求打挂；返回 key 还能让响应里直接看出漏了哪个 key。
		 */
		@Test
		@DisplayName("key 不存在：返回 key 本身且不抛异常")
		void unknownKeyReturnsKeyItself() {
			assertThat(MessageUtils.getMessage("response.not-exist")).isEqualTo("response.not-exist");
		}

		/**
		 * 未初始化消息源（不启动 Spring 容器的纯单元测试）时不能抛 NPE。
		 *
		 * <p>
		 * 这条路径在正常应用中走不到，但 {@code ApiResponse} 的字段初始值会调用本类，
		 * 一旦某个测试忘了起容器，NPE 会以「莫名其妙的空指针」形式出现在无关的测试里，
		 * 排查成本极高。这里用反射把静态字段清空来复现并钉死。
		 */
		@Test
		@DisplayName("消息源未初始化：返回 key 而非 NPE")
		void nullMessageSourceReturnsKey() throws Exception {
			MessageSource original = extractMessageSource();
			try {
				MessageUtils.setMessageSource(null);
				assertThat(MessageUtils.getMessage("response.success")).isEqualTo("response.success");
			} finally {
				MessageUtils.setMessageSource(original);
			}
		}

		/**
		 * 消息源抛 {@link NoSuchMessageException} 时同样回落 key，不把异常抛给调用方。
		 *
		 * <p>
		 * 当前配置（{@code useCodeAsDefaultMessage=true}）下这条路径不会自然触发，
		 * 但它是 {@code MessageUtils} 对「消息源被换成别的实现」的防御：业务方可能把消息源
		 * 替换成基于数据库或远程配置的实现，或关掉 {@code useCodeAsDefaultMessage}。
		 * 那时若不做兜底，取词失败会直接变成业务接口 500。
		 *
		 * <p>
		 * 这里用桩实现强制走该分支，顺带钉死「响应式 API 不该因文案缺失而挂掉」这条设计意图。
		 */
		@Test
		@DisplayName("消息源抛 NoSuchMessageException：回落 key")
		void noSuchMessageFallsBackToKey() throws Exception {
			MessageSource original = extractMessageSource();
			try {
				MessageUtils.setMessageSource(new MessageSource() {
					@Override
					public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
						throw new NoSuchMessageException(code, locale);
					}

					@Override
					public String getMessage(String code, Object[] args, Locale locale)
							throws NoSuchMessageException {
						throw new NoSuchMessageException(code, locale);
					}

					@Override
					public String getMessage(MessageSourceResolvable resolvable, Locale locale)
							throws NoSuchMessageException {
						throw new NoSuchMessageException(resolvable.getCodes()[0], locale);
					}
				});

				assertThat(MessageUtils.getMessage("response.success")).isEqualTo("response.success");
			} finally {
				MessageUtils.setMessageSource(original);
			}
		}

		/**
		 * 消息源返回 {@code null} 时回落 key（不同实现可能返回 null 而非抛异常）。
		 *
		 * <p>
		 * {@link MessageSource} 不是函数式接口（三个抽象方法），因此必须用匿名类而非 lambda。
		 */
		@Test
		@DisplayName("消息源返回 null：回落 key")
		void nullMessageFallsBackToKey() throws Exception {
			MessageSource original = extractMessageSource();
			try {
				MessageUtils.setMessageSource(new MessageSource() {
					@Override
					public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
						return null;
					}

					@Override
					public String getMessage(String code, Object[] args, Locale locale) {
						return null;
					}

					@Override
					public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
						return null;
					}
				});

				assertThat(MessageUtils.getMessage("response.success")).isEqualTo("response.success");
			} finally {
				MessageUtils.setMessageSource(original);
			}
		}

		/**
		 * 显式传 {@code null} 语言时用当前请求语言，不应变成 NPE。
		 */
		@Test
		@DisplayName("locale 为 null：使用当前请求语言")
		void nullLocaleUsesCurrentLocale() {
			LocaleContextHolder.setLocale(Locale.ENGLISH);

			assertThat(MessageUtils.getMessage("response.success", (Locale) null)).isEqualTo("Success");
		}

		/**
		 * 从 {@link MessageUtils} 里反射读出当前消息源，供「替换再还原」用例使用。
		 *
		 * @return 当前静态持有的消息源
		 * @throws Exception 反射失败
		 */
		private MessageSource extractMessageSource() throws Exception {
			java.lang.reflect.Field field = MessageUtils.class.getDeclaredField("messageSource");
			field.setAccessible(true);
			return (MessageSource) field.get(null);
		}
	}

	/**
	 * {@code getMessageOrDefault}：带回落文案的取词，供「枚举 + 国际化」场景使用。
	 */
	@Nested
	@DisplayName("带回落的取词")
	class GetMessageOrDefault {

		/**
		 * 正常取到词时用译文，忽略回落文案。
		 */
		@Test
		@DisplayName("取到词：用译文")
		void usesMessageWhenResolved() {
			LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

			assertThat(MessageUtils.getMessageOrDefault("response.success", "FALLBACK", (Locale) null))
					.isEqualTo("操作成功");
		}

		/**
		 * key 为 {@code null}（枚举未覆写 {@code messageKey()}）时直接用回落文案，
		 * 这正是「旧自定义枚举零改动兼容」的实现依据。
		 */
		@Test
		@DisplayName("key 为 null：用回落文案")
		void usesFallbackWhenKeyIsNull() {
			assertThat(MessageUtils.getMessageOrDefault(null, "FALLBACK", (Locale) null)).isEqualTo("FALLBACK");
		}

		/**
		 * key 查不到时用回落文案，而不是把 key 当文案返回。
		 *
		 * <p>
		 * 这是与 {@link MessageUtils#getMessage(String)} 的关键差别：后者返回 key（便于排查漏配），
		 * 前者返回调用方给的完整句子（避免响应体里出现标识符）。
		 */
		@Test
		@DisplayName("key 取不到：用回落文案")
		void usesFallbackWhenUnresolved() {
			assertThat(MessageUtils.getMessageOrDefault("response.not-exist", "FALLBACK", (Locale) null))
					.isEqualTo("FALLBACK");
		}

		/**
		 * 带占位符的重载：取到词时填充参数，取不到时回落原文（不残留 {@code {0}}）。
		 */
		@Test
		@DisplayName("带占位符：取到词填参数，取不到用回落原文")
		void supportsPlaceholderArguments() {
			LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

			assertThat(MessageUtils.getMessageOrDefault("request.missing-parameter", "缺少参数", new Object[] { "name" },
					null)).isEqualTo("缺少必填参数: name");
			assertThat(MessageUtils.getMessageOrDefault("request.not-exist", "缺少参数", new Object[] { "name" }, null))
					.isEqualTo("缺少参数");
		}
	}

	/**
	 * 资源配置的一致性：文件与 key 集合必须严丝合缝。
	 *
	 * <p>
	 * 「支持语言清单与文件一一对应」这条已经移到 {@code config.I18nConfigTest}——它校验的是配置，
	 * 不是取词逻辑；这里保留的是「文件内容之间」的横向一致性。
	 */
	@Nested
	@DisplayName("资源配置一致性")
	class ResourceConsistency {

		/**
		 * 各语言资源文件的 key 集合必须完全一致。
		 *
		 * <p>
		 * 少一个 key 不会报错，只会让那个语言少一句翻译（回落到基名文件）。这条断言把
		 * 「翻译漏了」变成构建失败——这也是 {@code EXPECTED_BUNDLE_SUFFIXES} 必须与
		 * {@link I18nConfig#SUPPORTED_LOCALES} 同步维护的原因。
		 */
		@Test
		@DisplayName("各语言 key 集合完全一致")
		void allBundlesShareSameKeys() {
			List<String> baseKeys = List.copyOf(ResourceBundle.getBundle("i18n/messages", Locale.ROOT).keySet());
			assertThat(baseKeys).as("基名文件不能为空").isNotEmpty();

			for (String suffix : EXPECTED_BUNDLE_SUFFIXES) {
				ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages",
						Locale.forLanguageTag(suffix.replace('_', '-')));
				assertThat(bundle.keySet()).as("messages_%s.properties 的 key 集合必须与基名文件一致", suffix)
						.containsExactlyInAnyOrderElementsOf(baseKeys);
			}
		}

		/**
		 * 每一条文案都不能为空串，且不含 BOM / 前后空白。
		 *
		 * <p>
		 * properties 文件被编辑器加上 BOM 时，第一个 key 会变成 {@code \uFEFFresponse.success}，
		 * 取词静默失效——这是中文项目里踩过无数次的坑，用断言直接封死。
		 */
		@Test
		@DisplayName("全部文案非空且无 BOM / 首尾空白")
		void allMessagesAreClean() {
			List<String> suffixes = new ArrayList<>(EXPECTED_BUNDLE_SUFFIXES);
			suffixes.add("");
			for (String suffix : suffixes) {
				Locale locale = suffix.isEmpty() ? Locale.ROOT
						: Locale.forLanguageTag(suffix.replace('_', '-'));
				ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", locale);
				for (String key : bundle.keySet()) {
					assertThat(key).as("key 不能带 BOM 或空白: %s", key).doesNotStartWith("\uFEFF");
					assertThat(bundle.getString(key)).as("messages%s 的 %s 文案不能为空", suffix, key).isNotBlank();
				}
			}
		}

		/**
		 * 所有枚举项的 {@code messageKey} 都必须在资源文件里有对应条目。
		 *
		 * <p>
		 * 这条把「枚举里写了 key，资源文件忘了加」这种半成品状态拦在构建期。
		 */
		@Test
		@DisplayName("ApiResponseConstant 的每个 messageKey 都能取到词")
		void everyEnumMessageKeyResolves() {
			for (com.zjc.demo.constant.ApiResponseConstant value : com.zjc.demo.constant.ApiResponseConstant.values()) {
				String resolved = messageSource.getMessage(value.messageKey(), null, Locale.SIMPLIFIED_CHINESE);
				assertThat(resolved).as("枚举 %s 的 key %s 未在资源文件中定义", value.name(), value.messageKey())
						.isNotBlank().isNotEqualTo(value.messageKey());
			}
		}

		/**
		 * 代码里硬编码的「开发期错误」key 必须能取到词。
		 *
		 * <p>
		 * {@code MessageUtils} 与 {@code TraceConstant} 的私有构造器会取
		 * {@code error.utility-class-instantiation} / {@code error.constant-class-instantiation}。
		 * 这两个 key 只在这两处使用、平时走不到，一旦资源文件漏配，
		 * 抛出的异常消息会变成 key 本身——本断言兜住这种情况。
		 */
		@Test
		@DisplayName("开发期错误 key 都能取到词")
		void developerErrorKeysResolve() {
			List<String> keys = List.of("error.utility-class-instantiation", "error.constant-class-instantiation");

			for (String key : keys) {
				assertThat(messageSource.getMessage(key, null, Locale.SIMPLIFIED_CHINESE)).as("缺失 key: %s", key)
						.isNotBlank().isNotEqualTo(key);
				assertThat(messageSource.getMessage(key, null, Locale.ENGLISH)).as("英文缺失 key: %s", key)
						.isNotBlank().isNotEqualTo(key);
			}
		}
	}

	/**
	 * {@link MessageUtils} 作为工具类的约束。
	 */
	@Nested
	@DisplayName("工具类约束")
	class UtilityClassContract {

		/**
		 * 私有构造器必须抛异常，让「禁止实例化」成为运行时保证。
		 *
		 * <p>
		 * 只断言异常类型、不断言消息内容：消息里的中文会受源文件编码与终端编码影响，
		 * 断言文案容易产生「本机绿、别人红」的假失败。与 {@code TraceConstantTest} 保持一致。
		 */
		@Test
		@DisplayName("私有构造器不可实例化")
		void constructorIsBlocked() throws Exception {
			Constructor<MessageUtils> constructor = MessageUtils.class.getDeclaredConstructor();
			constructor.setAccessible(true);

			assertThatThrownBy(constructor::newInstance).hasCauseInstanceOf(UnsupportedOperationException.class);
		}
	}
}
