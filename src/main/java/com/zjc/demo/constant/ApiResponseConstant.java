package com.zjc.demo.constant;

/**
 * 接口响应码与提示信息标准枚举，供统一响应封装 {@code ApiResponse} 与 {@code BusinessException} 使用。
 *
 * <p>
 * 实现了 {@link ErrorCodeConstant}，允许直接传入 {@code BusinessException} 构造。
 *
 * <p>
 * <b>核心约定：枚举的 {@code code} 同时就是 HTTP 响应状态码</b>，被
 * {@code ApiResponse#httpStatus()} 用于驱动 HTTP 状态码，因此取值必须是合法的 HTTP
 * 状态码（100~599），不允许再使用 0 / -1 / 10001 这类非 HTTP 语义的编码。
 *
 * <p>
 * <b>码段规划（与 HTTP 语义完全对齐）：</b>
 * <ul>
 * <li>{@code 2xx} — 成功</li>
 * <li>{@code 4xx} — 客户端错误：参数、鉴权、资源不存在</li>
 * <li>{@code 5xx} — 服务端错误</li>
 * </ul>
 *
 * <p>
 * 业务如需更细粒度的错误码（如 10001 用户不存在），请自行实现 {@link ErrorCodeConstant} 时 保证
 * {@code code()} 返回对应的 HTTP 状态码，并在业务侧另设字段承载细分编码。
 *
 * <p>
 * <b>国际化：每个枚举项都带一个 {@code messageKey}</b>，指向
 * {@code src/main/resources/i18n/messages*.properties} 里的条目（如 {@code response.not-found}）。
 * 框架按请求的 {@code Accept-Language} 取词；资源文件里查不到该 key 时回落到本枚举的 {@code message()}
 * 固定文案，因此「漏配翻译」不会导致接口报错，最多是语言没切过去。
 *
 * @author jiancai.zhong
 */
public enum ApiResponseConstant implements ErrorCodeConstant {

	/**
	 * 请求成功。
	 */
	SUCCESS(200, "操作成功", "response.success"),

	/**
	 * 通用失败（未分类的错误），按客户端错误处理。
	 */
	FAILURE(400, "操作失败", "response.failure"),

	/**
	 * 参数非法（校验不通过）。
	 */
	PARAM_INVALID(400, "参数非法", "response.param-invalid"),

	/**
	 * 请求体缺失或无法解析。
	 */
	BAD_REQUEST(400, "请求体格式错误", "response.bad-request"),

	/**
	 * 资源不存在。
	 */
	NOT_FOUND(404, "资源不存在", "response.not-found"),

	/**
	 * 数据冲突（如唯一约束冲突）。
	 */
	CONFLICT(409, "数据冲突", "response.conflict"),

	/**
	 * 请求方法不支持。
	 */
	METHOD_NOT_ALLOWED(405, "请求方法不支持", "response.method-not-allowed"),

	/**
	 * 请求体媒体类型不支持。
	 */
	UNSUPPORTED_MEDIA_TYPE(415, "不支持的请求体类型", "response.unsupported-media-type"),

	/**
	 * 请求超时。
	 */
	REQUEST_TIMEOUT(408, "请求处理超时", "response.request-timeout"),

	/**
	 * 未认证（未登录或 token 无效）。
	 */
	UNAUTHORIZED(401, "未认证", "response.unauthorized"),

	/**
	 * 无权限访问。
	 */
	FORBIDDEN(403, "无权限", "response.forbidden"),

	/**
	 * 服务内部错误。
	 */
	INTERNAL_ERROR(500, "服务内部错误", "response.internal-error"),

	/**
	 * 服务不可用（降级或维护中）。
	 */
	SERVICE_UNAVAILABLE(503, "服务不可用", "response.service-unavailable");

	private final int code;
	private final String message;
	private final String messageKey;

	/**
	 * 构造响应码枚举项。
	 *
	 * @param code       状态码，同时作为 HTTP 响应状态码，必须是 100~599 的合法值
	 * @param message    兜底提示文案，国际化文案缺失时使用
	 * @param messageKey 国际化消息 key，对应 {@code messages*.properties} 里的条目
	 */
	ApiResponseConstant(int code, String message, String messageKey) {
		this.code = code;
		this.message = message;
		this.messageKey = messageKey;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return 状态码，取值同时作为 HTTP 响应状态码
	 */
	@Override
	public int code() {
		return code;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 返回的是<b>兜底文案</b>（固定中文），不是按语言解析后的结果。需要多语言时应走
	 * {@link #messageKey()} + {@code MessageUtils}。
	 *
	 * @return 默认提示文案
	 */
	@Override
	public String message() {
		return message;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return 国际化消息 key，如 {@code response.not-found}
	 */
	@Override
	public String messageKey() {
		return messageKey;
	}
}
