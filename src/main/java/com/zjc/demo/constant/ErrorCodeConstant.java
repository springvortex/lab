package com.zjc.demo.constant;

/**
 * 错误码契约接口。
 *
 * <p>
 * 业务方让自定义枚举实现本接口，即可与 {@code ApiResponseConstant} 共用一套异常与响应体系： 枚举实例可以直接传给
 * {@code BusinessException} 与 {@code ApiResponse#failure}， 无需再写转换代码。
 *
 * <p>
 * <b>核心约定：{@code code()} 返回的就是 HTTP 状态码</b>，会直接用于 HTTP 响应状态， 因此必须是 100~599
 * 之间的合法值，禁止使用 0 / -1 / 10001 这类非 HTTP 语义的编码。 若需要更细的业务区分，请另设字段承载，不要让本方法脱离 HTTP
 * 语义。
 *
 * <p>
 * <b>国际化：{@code message()} 返回的是「兜底文案」，不是「最终文案」。</b> 推荐做成枚举常量字段，再覆写
 * {@link #messageKey()} 给出 {@code messages*.properties} 里的 key； 框架会按请求语言取词，取不到时回落到
 * {@code message()}。若某个枚举不需要国际化（例如只在内网使用的运维接口）， 保持默认实现即可，行为与旧版完全一致。
 *
 * @author jiancai.zhong
 */
public interface ErrorCodeConstant {

	/**
	 * 返回错误码。
	 *
	 * @return 错误码，必须是合法 HTTP 状态码（100~599）
	 */
	int code();

	/**
	 * 返回面向调用方的错误提示信息。
	 *
	 * <p>
	 * 这是<b>兜底文案</b>：当 {@link #messageKey()} 在资源文件里查不到时，框架会回落到本方法的返回值。
	 * 因此实现方必须保证它是一句完整、可直接返回给调用方的话，不能依赖国际化资源一定存在。
	 *
	 * @return 错误提示信息，不应包含内部实现细节
	 */
	String message();

	/**
	 * 返回国际化消息 key，默认取「错误码」的字符串形式。
	 *
	 * <p>
	 * <b>默认实现刻意不用 {@code code()}：</b>若用 {@code code()}，国际化文件里就得写
	 * {@code 404=资源不存在} 这样的键——多个枚举共用一个状态码时会互相覆盖（模板里 400 就有
	 * {@code FAILURE} / {@code PARAM_INVALID} / {@code BAD_REQUEST} 三项），语义直接串掉。
	 * 因此默认返回 {@code null}，表示「该枚举不使用国际化」，框架转而使用 {@link #message()}。
	 *
	 * <p>
	 * 需要国际化的实现方覆写本方法，返回如 {@code response.not-found} 这样的 key，
	 * 并在 {@code messages*.properties} 里为该 key 提供各语言文案。
	 *
	 * @return 国际化消息 key；{@code null} 表示不使用国际化、直接用 {@link #message()}
	 */
	default String messageKey() {
		return null;
	}
}
