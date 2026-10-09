package com.zjc.demo.common.constant;

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
	 * @return 错误提示信息，不应包含内部实现细节
	 */
	String message();
}
