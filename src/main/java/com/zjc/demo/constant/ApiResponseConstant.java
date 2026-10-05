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
 * @author jiancai.zhong
 */
public enum ApiResponseConstant implements ErrorCodeConstant {

	/**
	 * 请求成功。
	 */
	SUCCESS(200, "操作成功"),

	/**
	 * 通用失败（未分类的错误），按客户端错误处理。
	 */
	FAILURE(400, "操作失败"),

	/**
	 * 参数非法（校验不通过）。
	 */
	PARAM_INVALID(400, "参数非法"),

	/**
	 * 请求体缺失或无法解析。
	 */
	BAD_REQUEST(400, "请求体格式错误"),

	/**
	 * 资源不存在。
	 */
	NOT_FOUND(404, "资源不存在"),

	/**
	 * 数据冲突（如唯一约束冲突）。
	 */
	CONFLICT(409, "数据冲突"),

	/**
	 * 请求方法不支持。
	 */
	METHOD_NOT_ALLOWED(405, "请求方法不支持"),

	/**
	 * 请求体媒体类型不支持。
	 */
	UNSUPPORTED_MEDIA_TYPE(415, "不支持的请求体类型"),

	/**
	 * 请求超时。
	 */
	REQUEST_TIMEOUT(408, "请求处理超时"),

	/**
	 * 未认证（未登录或 token 无效）。
	 */
	UNAUTHORIZED(401, "未认证"),

	/**
	 * 无权限访问。
	 */
	FORBIDDEN(403, "无权限"),

	/**
	 * 服务内部错误。
	 */
	INTERNAL_ERROR(500, "服务内部错误"),

	/**
	 * 服务不可用（降级或维护中）。
	 */
	SERVICE_UNAVAILABLE(503, "服务不可用");

	private final int code;
	private final String message;

	/**
	 * 构造响应码枚举项。
	 *
	 * @param code    状态码，同时作为 HTTP 响应状态码，必须是 100~599 的合法值
	 * @param message 默认提示文案，会直接返回给调用方
	 */
	ApiResponseConstant(int code, String message) {
		this.code = code;
		this.message = message;
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
	 * @return 默认提示文案
	 */
	@Override
	public String message() {
		return message;
	}
}
