package com.zjc.demo.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import com.zjc.demo.util.MessageUtils;

/**
 * 国际化（i18n）配置：消息源与语言解析策略的唯一入口。
 *
 * <p>
 * <b>为什么不用 Boot 的自动配置：</b>Boot 会自动创建 {@code MessageSource}（读
 * {@code spring.messages.*}）与 {@code LocaleResolver}（默认
 * {@code spring.web.locale-resolver: accept-header}），但自动创建的那个
 * {@link AcceptHeaderLocaleResolver} <b>只支持 {@link Locale#getDefault()} 一种语言</b>，
 * 也就是说在中文机器上 {@code Accept-Language: en-US} 会被<b>静默忽略</b>、照样返回中文——
 * 不报错、不告警。本类显式声明支持的语言清单，把这个坑堵上。
 *
 * <p>
 * <b>语言切换依据：{@code Accept-Language} 请求头。</b>这是 REST 的标准做法：浏览器与
 * HTTP 客户端自动携带，服务端无需约定额外参数，也不改变 URL 结构。
 *
 * <p>
 * <b>匹配规则（由 Spring 的 {@code AbstractLocaleResolver} 提供）：</b>
 * <ul>
 * <li>{@code zh}、{@code zh-CN}、{@code zh_Hans} 都归一到中文；</li>
 * <li>{@code en}、{@code en-US}、{@code en-GB} 都归一到英文；</li>
 * <li>头缺失、头非法、或语言不在支持清单里 —— 全部回落到 {@code app.i18n.default-locale}
 * 配置的默认语言（缺省中文），而不是 JVM 默认语言，保证「同一个请求在任何机器上结果一致」。</li>
 * </ul>
 *
 * <p>
 * <b>新增一门语言要改三处：</b>加一份 {@code i18n/messages_<语言>.properties}、
 * 把该语言加进 {@link #SUPPORTED_LOCALES}、在 README 的语言表里补一行。
 *
 * @author jiancai.zhong
 * @see com.zjc.demo.util.MessageUtils 业务代码取词入口
 */
@Configuration
public class I18nConfig {

	/**
	 * 支持的语言清单。
	 *
	 * <p>
	 * <b>⚠️ 这里的条目必须是「语言级」（country 为空），不能写国家/地区级。</b>
	 * 这是实测出来的硬约束，写错会导致「区域变体全部静默回落」：
	 *
	 * <p>
	 * Spring 的 {@code AcceptHeaderLocaleResolver#findSupportedLocale} 在找不到精确匹配时，
	 * 会遍历清单找「语言相同且 country 为空」的项做兜底；清单里若写 {@link Locale#US}
	 * （country = {@code "US"}），这个判定不成立，于是 {@code en-GB} / {@code en-AU}
	 * <b>全部回落到默认语言</b>，且没有任何日志。实测对照：
	 *
	 * <table border="1">
	 * <caption>清单内容对 en-GB 请求的影响</caption>
	 * <tr><th>清单写法</th><th>{@code en-US} 请求</th><th>{@code en-GB} 请求</th></tr>
	 * <tr><td>{@code [zh, en_US]}</td><td>英文</td><td><b>中文（失效）</b></td></tr>
	 * <tr><td>{@code [zh, en]}（当前写法）</td><td>英文</td><td>英文</td></tr>
	 * </table>
	 *
	 * <p>
	 * 中文同样写语言级（{@link Locale#CHINESE} 而非 {@link Locale#SIMPLIFIED_CHINESE}），
	 * 因为默认语言是<b>可配置</b>的：一旦把默认改成英文，中文就成了「非默认语言」，
	 * 此时 {@code zh_CN} 的 country 非空，会让 {@code zh-TW} 之类的区域变体静默回落。
	 * 两项都语言级，清单里任意一项都能安全地当默认语言。
	 *
	 * <p>
	 * 相应地，资源文件也用语言级命名（{@code messages_en.properties}）。
	 * {@code ResourceBundle} 的查找链是「精确 → 语言 → 基名」，语言级文件能一次覆盖
	 * 该语言的所有区域变体，与解析器的粒度正好对齐——两边必须是同一粒度，
	 * 否则会出现「解析器归一到 {@code en}、却没有 {@code messages_en.properties}」的空转。
	 *
	 * <p>
	 * 每一项都必须有对应的 {@code messages_<语言>.properties}。
	 * {@code MessageUtilsTest#everySupportedLocaleHasBundle} 有断言核对清单与文件一一对应。
	 *
	 * @see #localeResolver()
	 */
	public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.CHINESE, Locale.ENGLISH);

	/**
	 * 默认语言：由配置 {@code app.i18n.default-locale} 决定，请求头缺失或语言不支持时使用。
	 *
	 * <p>
	 * 构造时就把配置值<b>归一成 {@link #SUPPORTED_LOCALES} 里的那一项</b>，因此配置写
	 * {@code zh-CN} / {@code zh} / {@code zh_CN} 效果完全相同。归一的好处是：解析器的默认语言
	 * 与清单项是同一个 {@link Locale} 实例，不会出现「默认语言在清单外」这种半吊子状态。
	 */
	private final Locale defaultLocale;

	/**
	 * 按配置构造：默认语言取自 {@code app.i18n.default-locale}（缺省 {@code zh-CN}）。
	 *
	 * <p>
	 * 用构造器注入而不是字段注入，是为了让「配置写错」这类分支能被纯单元测试覆盖——
	 * 直接 {@code new I18nConfig("fr-FR")} 即可断言它抛异常，不必启动 Spring 容器。
	 *
	 * <p>
	 * <b>刻意不</b>用 {@link Locale#getDefault()} 兜底。容器里 JVM 默认语言常常是
	 * {@code en_US}（甚至 {@code POSIX}），若跟着它走，同一份代码在开发机与容器里的默认文案会不一致，
	 * 而「默认文案」恰恰是给调用方看的兜底信息，必须稳定可预期。
	 *
	 * @param defaultLocaleTag 默认语言标签，如 {@code zh-CN} / {@code en}；语言必须在支持清单内
	 * @throws IllegalStateException 标签非法、或其语言不在 {@link #SUPPORTED_LOCALES} 内时抛出
	 */
	public I18nConfig(@Value("${app.i18n.default-locale:zh-CN}") String defaultLocaleTag) {
		this.defaultLocale = resolveDefaultLocale(defaultLocaleTag);
	}

	/**
	 * 把配置值解析成 {@link #SUPPORTED_LOCALES} 里的某一项。
	 *
	 * <p>
	 * <b>为什么必须落在清单内：</b>清单决定了 {@link AcceptHeaderLocaleResolver} 认哪些语言。
	 * 默认语言若在清单外，会出现「不带头请求返回法文、但法文资源文件并不存在」的状态——不报错，
	 * 只是悄悄回落基名文件。与其静默，不如启动时直接失败，并在信息里列出可用值。
	 *
	 * @param tag 配置里的语言标签
	 * @return 清单中语言相同的那一项（区域变体归一，如 {@code en-US} → {@link Locale#ENGLISH}）
	 * @throws IllegalStateException 标签非法、或语言不在清单内
	 */
	private static Locale resolveDefaultLocale(String tag) {
		// 下划线形式（zh_CN）在 properties / 环境变量里更常见，统一转成 BCP 47 的连字符形式
		Locale parsed = Locale.forLanguageTag(tag.trim().replace('_', '-'));
		if (parsed.getLanguage().isEmpty()) {
			throw new IllegalStateException(
					"app.i18n.default-locale 不是合法的语言标签: " + tag + "，可用值: " + SUPPORTED_LOCALES);
		}
		for (Locale supported : SUPPORTED_LOCALES) {
			if (supported.getLanguage().equals(parsed.getLanguage())) {
				return supported;
			}
		}
		throw new IllegalStateException("app.i18n.default-locale=" + tag + " 的语言不在支持清单内，可用值: "
				+ SUPPORTED_LOCALES + "。新增语言请同时补 i18n/messages_<语言>.properties");
	}

	/**
	 * 当前生效的默认语言（已归一到 {@link #SUPPORTED_LOCALES} 中的某一项）。
	 *
	 * @return 默认语言
	 */
	public Locale getDefaultLocale() {
		return defaultLocale;
	}

	/**
	 * 消息源：负责按 key + 语言取出文案。
	 *
	 * <p>
	 * 用 {@link ReloadableResourceBundleMessageSource} 而不是 Boot 默认的
	 * {@code ResourceBundleMessageSource}，理由是它支持 {@code defaultEncoding} 与
	 * {@code fileEncodings}，能保证 properties 文件里的中文不因平台默认编码而乱码。
	 *
	 * <p>
	 * <b>三个开关都是刻意设置的：</b>
	 * <ul>
	 * <li>{@code setDefaultEncoding(UTF-8)}：properties 文件按 UTF-8 读取，中文不乱码。
	 * <b>JDK 9 之前 properties 强制 ISO-8859-1</b>，现在虽已放开，但默认编码仍随平台变化，
	 * 显式指定才可移植；</li>
	 * <li>{@code setFallbackToSystemLocale(false)}：查不到当前语言的 key 时，
	 * <b>直接回落到基名文件</b> {@code messages.properties}，不要再去看 JVM 默认语言。
	 * 设为 {@code true}（Boot 默认）时，英文环境缺一个 key 会先去找中文文件，
	 * 在「中英都缺、但系统语言是第三种」的场景下行为更难预期；</li>
	 * <li>{@code setUseCodeAsDefaultMessage(true)}：key 查不到时把 <b>key 本身</b>当文案返回，
	 * 而不是抛 {@code NoSuchMessageException}。文案缺失是配置问题，不该把业务请求打挂——
	 * 返回 key 既能保证接口可用，又能在响应里直接看到是哪个 key 漏了。</li>
	 * </ul>
	 *
	 * @return 消息源，基名为 {@code classpath:i18n/messages}
	 */
	@Bean
	public MessageSource messageSource() {
		ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
		// 注意：Reloadable 版本要求 classpath 前缀，写成 "i18n/messages" 会解析失败
		messageSource.setBasenames("classpath:i18n/messages");
		messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
		messageSource.setFallbackToSystemLocale(false);
		messageSource.setUseCodeAsDefaultMessage(true);
		// 回填给 MessageUtils：ApiResponse 的字段初始值与 ErrorCodeConstant 的默认文案
		// 都发生在对象构造期，拿不到依赖注入，只能靠静态持有。见 MessageUtils 类注释。
		MessageUtils.setMessageSource(messageSource);
		return messageSource;
	}

	/**
	 * 语言解析器：按 {@code Accept-Language} 请求头决定本次请求使用哪种语言。
	 *
	 * <p>
	 * <b>{@code setSupportedLocales} 是本类存在的核心理由。</b>
	 * {@link AcceptHeaderLocaleResolver} 自己不做「就近匹配」——它只认清单里明确列出的语言，
	 * 不在清单内就回落到 {@code setDefaultLocale} 指定的值。不调用这个 setter 时，Spring 的默认实现
	 * （{@code AbstractLocaleResolver}）会把清单设成<b>只含 {@link Locale#getDefault()}</b>，
	 * 于是「机器是中文 → 英文请求也返回中文」，且全程没有任何日志。
	 *
	 * <p>
	 * <b>匹配规则（已实测，见 {@link #SUPPORTED_LOCALES} 的对照表）：</b>
	 * <ul>
	 * <li>{@code zh}、{@code zh-CN}、{@code zh_Hans} 都归一到中文；</li>
	 * <li>{@code en}、{@code en-US}、{@code en-GB} 都归一到英文——<b>前提是清单里写语言级条目</b>；</li>
	 * <li>头缺失、头非法、或语言不在支持清单里 —— 全部回落到 {@code app.i18n.default-locale}
	 * 配置的默认语言（缺省中文），而不是 JVM 默认语言，保证「同一个请求在任何机器上结果一致」。</li>
	 * </ul>
	 *
	 * @return 语言解析器，默认语言取自配置
	 * @see #getDefaultLocale()
	 */
	@Bean
	public LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
		resolver.setSupportedLocales(SUPPORTED_LOCALES);
		resolver.setDefaultLocale(defaultLocale);
		return resolver;
	}
}
