package com.zjc.demo.util;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;

/**
 * 国际化取词工具：业务代码取多语言文案的唯一入口。
 *
 * <p>
 * <b>解决的问题：</b>{@link MessageSource} 是 Spring Bean，而模板里最需要取词的地方——
 * {@code ApiResponse} 的字段初始值、{@code ErrorCodeConstant} 的默认文案——都发生在
 * 「对象构造时」，那里<b>拿不到依赖注入</b>。本类把容器里的 {@link MessageSource} 静态持有，
 * 让这些位置也能取词，同时避免每个调用点都写一遍 {@code context.getMessage(...)} 长参数列表。
 *
 * <p>
 * <b>使用示例：</b>
 *
 * <pre>{@code
 * // 简单取词（当前请求语言）
 * String msg = MessageUtils.getMessage("response.success");
 *
 * // 带占位符：i18n/messages.properties 里写 response.method-not-allowed={0} 方法不支持
 * String msg2 = MessageUtils.getMessage("response.method-not-allowed", "POST");
 *
 * // 指定语言（定时任务、批量导出一类没有请求上下文的场景）
 * String msg3 = MessageUtils.getMessage("response.success", Locale.US);
 * }
 * </pre>
 *
 * <p>
 * <b>当前语言取自 {@link LocaleContextHolder}</b>，它由 Spring MVC 在处理请求时根据
 * {@code Accept-Language} 写入，请求线程内全局可读，无需把 {@link Locale} 逐层往下传。
 * 非 HTTP 线程（{@code @Async}、定时任务、消息消费）读到的是一开始就设好的默认语言——
 * 这与 {@code ApiResponse#traceId} 在非 HTTP 线程为 {@code null} 是同一类现象。
 *
 * <p>
 * <b>资源文件命名用「语言级」</b>（{@code messages_en.properties} 而非
 * {@code messages_en_US.properties}）。{@code ResourceBundle} 的查找链是「精确 → 语言 → 基名」，
 * 所以语言级文件能一次覆盖所有该语言的区域变体（{@code en_US}、{@code en_GB}、{@code en_AU}…），
 * 不必逐个建文件。这也是 {@code I18nConfig#SUPPORTED_LOCALES} 同样使用语言级条目的原因——
 * 两边粒度一致，链路才不会空转。
 *
 * <p>
 * <b>找不到 key 时的行为：</b>由 {@code I18nConfig} 的消息源配置决定——返回 key 本身而不是抛异常
 * （{@code setUseCodeAsDefaultMessage(true)}）。本类额外兜一层，即使消息源被换成不返回 key 的实现，
 * 也不会把 {@code null} 传出去污染响应体。
 *
 * @author jiancai.zhong
 * @see com.zjc.demo.config.I18nConfig
 */
public final class MessageUtils {

	/**
	 * 容器注入的消息源。
	 *
	 * <p>
	 * 声明为 {@code static} 是刻意的：本类的调用点（{@code ApiResponse} 字段初始值、
	 * {@code ErrorCodeConstant} 枚举常量）都在对象构造期，拿不到实例注入。
	 *
	 * <p>
	 * 用 {@code volatile} 而非 {@code final}：由 {@code setMessageSource} 在启动时赋值，
	 * 加 {@code volatile} 保证多线程可见性——否则其他线程可能长时间读到 {@code null}。
	 *
	 * <p>
	 * <b>为 {@code null} 的情况：</b>不启动 Spring 容器的纯单元测试，或容器启动中途。此时
	 * {@link #getMessage(String)} 会走兜底返回 key，不会抛 NPE。
	 */
	private static volatile MessageSource messageSource;

	/**
	 * 工具类禁止实例化。
	 *
	 * <p>
	 * 与 {@code TraceConstant} 保持一致：访问修饰符 {@code private} 只能拦住正常代码，
	 * 反射仍可绕过，因此显式抛异常，让「禁止实例化」成为运行时保证而不是一句注释。
	 *
	 * <p>
	 * 文案走 {@code MessageUtils#getMessage} 而不是写死：本类的静态消息源此时通常已由
	 * {@code I18nConfig} 回填，取的是 {@code error.utility-class-instantiation} 的译文；
	 * 若在容器启动前被反射调用，消息源为 {@code null}，该方法回落 key 本身，同样可用。
	 * 这里传入固定文案作兜底，保证任何情况下抛出的都是完整句子。
	 */
	private MessageUtils() {
		throw new UnsupportedOperationException(
				getMessageOrDefault("error.utility-class-instantiation", "工具类禁止实例化", null));
	}

	/**
	 * 由 {@code I18nConfig} 在容器启动时回填消息源。
	 *
	 * <p>
	 * <b>不要手工调用。</b>回填动作由 {@code I18nConfig} 里消息源 Bean 的
	 * {@code @Bean} 方法完成——Spring 创建该 Bean 后立刻把实例写进本类，此后全局可读。
	 *
	 * @param messageSource 容器创建的消息源
	 */
	public static void setMessageSource(MessageSource messageSource) {
		MessageUtils.messageSource = messageSource;
	}

	/**
	 * 按当前请求语言取词。
	 *
	 * @param key 消息 key，如 {@code response.success}
	 * @return 文案；key 缺失或消息源不可用时返回 key 本身，永不为 {@code null}
	 */
	public static String getMessage(String key) {
		return getMessage(key, null, null);
	}

	/**
	 * 按当前请求语言取词，并填充占位符。
	 *
	 * @param key  消息 key
	 * @param args 占位符参数，按顺序替换 {@code {0}}、{@code {1}}…；无参数时传 {@code null}
	 * @return 文案；key 缺失或消息源不可用时返回 key 本身，永不为 {@code null}
	 */
	public static String getMessage(String key, @Nullable Object... args) {
		return getMessage(key, args, null);
	}

	/**
	 * 指定语言取词，供非 HTTP 上下文使用。
	 *
	 * @param key    消息 key
	 * @param locale 目标语言；为 {@code null} 时用当前请求语言
	 * @return 文案；key 缺失或消息源不可用时返回 key 本身，永不为 {@code null}
	 */
	public static String getMessage(String key, @Nullable Locale locale) {
		return getMessage(key, null, locale);
	}

	/**
	 * 按 key 取词，取不到时回落到调用方给的固定文案。
	 *
	 * <p>
	 * 这是「枚举 + 国际化」场景的标准写法，把两个容易漏判的边界一次处理干净：
	 * <ol>
	 * <li><b>key 为 {@code null}</b>（枚举未覆写 {@code messageKey()}）→ 直接用固定文案；</li>
	 * <li><b>取回来的就是 key 本身</b> —— 意味着没取到词（容器未就绪时的纯单元测试，
	 * 或资源文件漏配了该 key）→ 同样用固定文案，避免响应体里冒出
	 * {@code response.not-found} 这类标识符。</li>
	 * </ol>
	 *
	 * <p>
	 * {@code ApiResponse#failure(ErrorCodeConstant)}、{@code BusinessException} 与
	 * {@code GlobalExceptionHandler} 都走本方法，保证「回落规则」只有一份实现。
	 *
	 * @param key      消息 key，可为 {@code null}
	 * @param fallback 取不到词时使用的固定文案
	 * @param locale   目标语言；为 {@code null} 时用当前请求语言
	 * @return 非 {@code null} 的文案
	 */
	public static String getMessageOrDefault(@Nullable String key, String fallback, @Nullable Locale locale) {
		return getMessageOrDefault(key, fallback, null, locale);
	}

	/**
	 * 带占位符参数的「取词 + 回落」。
	 *
	 * <p>
	 * 占位符只在取到词时生效；回落时用的是 {@code fallback} 原文（通常不含占位符），
	 * 因此不会出现 {@code {0}} 裸露在响应里的情况。
	 *
	 * @param key      消息 key，可为 {@code null}
	 * @param fallback 取不到词时使用的固定文案
	 * @param args     占位符参数，可为 {@code null}
	 * @param locale   目标语言；为 {@code null} 时用当前请求语言
	 * @return 非 {@code null} 的文案
	 */
	public static String getMessageOrDefault(@Nullable String key, String fallback, @Nullable Object[] args,
			@Nullable Locale locale) {
		if (key == null) {
			return fallback;
		}
		String resolved = getMessage(key, args, locale);
		return key.equals(resolved) ? fallback : resolved;
	}

	/**
	 * 取词核心实现：指定语言 + 占位符参数。
	 *
	 * <p>
	 * 三层兜底，保证任何时候都吐出一个非 {@code null} 的字符串：
	 * <ol>
	 * <li>正常取词：{@code locale} 为空时用 {@link LocaleContextHolder#getLocale()}（请求语言）；</li>
	 * <li>消息源返回 {@code null}（换了实现时可能出现）：回落 key 本身；</li>
	 * <li>消息源不可用或抛出 {@link NoSuchMessageException}：回落 key 本身。</li>
	 * </ol>
	 *
	 * @param key    消息 key
	 * @param args   占位符参数
	 * @param locale 目标语言，{@code null} 表示用当前请求语言
	 * @return 非 {@code null} 的文案
	 */
	private static String getMessage(String key, @Nullable Object[] args, @Nullable Locale locale) {
		MessageSource source = messageSource;
		if (source == null) {
			return key;
		}
		Locale targetLocale = locale == null ? LocaleContextHolder.getLocale() : locale;
		try {
			String message = source.getMessage(key, args, targetLocale);
			return message == null ? key : message;
		} catch (NoSuchMessageException e) {
			// 消息源未开启 useCodeAsDefaultMessage 时的兜底，正常情况下不会走到
			return key;
		}
	}
}
