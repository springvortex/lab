package com.zjc.demo.support;

import com.zjc.demo.constant.ErrorCodeConstant;

/**
 * 测试用自定义错误码枚举，验证 {@link ErrorCodeConstant} 这个扩展点能被业务方正常实现。
 *
 * <p>
 * <b>刻意保留两种写法：</b>{@code USER_NOT_FOUND} 覆写 {@code messageKey()} 走国际化，
 * {@code STOCK_NOT_ENOUGH} 不覆写、直接用 {@code message()}。后者用来钉死「旧实现零改动仍可用」
 * 这条兼容性承诺——{@code messageKey()} 是接口的 {@code default} 方法，默认返回 {@code null}，
 * 框架会自动回退到固定文案。
 *
 * @author jiancai.zhong
 */
public enum TestErrorCodeConstant implements ErrorCodeConstant {

	/**
	 * 模拟「用户不存在」，状态码使用 404。覆写 messageKey，参与国际化。
	 */
	USER_NOT_FOUND(404, "用户不存在", "response.not-found"),

	/**
	 * 模拟「库存不足」，状态码使用 409。不覆写 messageKey，走固定文案（兼容性验证）。
	 */
	STOCK_NOT_ENOUGH(409, "库存不足", null);

	private final int code;
	private final String message;
	private final String messageKey;

	/**
	 * 构造自定义错误码。
	 *
	 * @param code       状态码，需为合法 HTTP 状态码
	 * @param message    兜底提示文案
	 * @param messageKey 国际化消息 key，{@code null} 表示不使用国际化
	 */
	TestErrorCodeConstant(int code, String message, String messageKey) {
		this.code = code;
		this.message = message;
		this.messageKey = messageKey;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return 状态码
	 */
	@Override
	public int code() {
		return code;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return 提示文案
	 */
	@Override
	public String message() {
		return message;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return 国际化消息 key，未配置时为 {@code null}
	 */
	@Override
	public String messageKey() {
		return messageKey;
	}
}
