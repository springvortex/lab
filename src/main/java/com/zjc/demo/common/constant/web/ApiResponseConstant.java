package com.zjc.demo.common.constant.web;

/**
 * 标准响应码枚举，供 {@code ApiResponse} 与 {@code BusinessException} 使用。
 *
 * <p>
 * {@code code} 同时就是 HTTP 状态码，取值必须是 100~599，不能用 0 / -1 / 10001 这类非 HTTP 语义的编码。
 * 需要更细的业务区分时，另设字段承载，不要动这个值。
 *
 * @author jiancai.zhong
 */
public enum ApiResponseConstant implements ErrorCodeConstant {

    SUCCESS(200, "操作成功"),
    FAILURE(400, "操作失败"),
    PARAM_INVALID(400, "参数非法"),
    BAD_REQUEST(400, "请求体格式错误"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的请求体类型"),
    PAYLOAD_TOO_LARGE(413, "请求体过大"),
    REQUEST_TIMEOUT(408, "请求处理超时"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    INTERNAL_ERROR(500, "服务内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用");

    private final int code;
    private final String message;

    /**
     * 构造响应码枚举项。
     *
     * @param code    状态码，同时作为 HTTP 响应状态码
     * @param message 默认提示文案
     */
    ApiResponseConstant(int code, String message) {
        this.code = code;
        this.message = message;
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
     * @return 默认提示文案
     */
    @Override
    public String message() {
        return message;
    }
}
