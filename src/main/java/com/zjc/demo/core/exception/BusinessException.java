package com.zjc.demo.core.exception;

import com.zjc.demo.common.constant.web.ApiResponseConstant;
import com.zjc.demo.common.constant.web.ErrorCodeConstant;
import lombok.Getter;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * 业务异常，表示「可预期的业务失败」，由全局异常处理器按 WARN 记录一行文案、不打印堆栈。
 * 程序缺陷请抛其他运行时异常。
 *
 * <p>
 * {@code code} 就是 HTTP 状态码，取值必须是 100~599，否则响应兜底成 500。
 *
 * @author jiancai.zhong
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 8505723016403438176L;
    /**
     * 错误码，同时作为 HTTP 响应状态码。
     */
    private final int code;

    /**
     * 使用 {@link ErrorCodeConstant} 枚举构造（推荐）。
     *
     * @param errorCodeConstant 错误码枚举
     */
    public BusinessException(ErrorCodeConstant errorCodeConstant) {
        super(errorCodeConstant.message());
        this.code = errorCodeConstant.code();
    }

    /**
     * 自定义提示信息，错误码默认 400。
     *
     * @param message 错误提示
     */
    public BusinessException(String message) {
        super(message);
        this.code = ApiResponseConstant.FAILURE.code();
    }

    /**
     * 自定义错误码 + 提示信息。
     *
     * @param code    错误码（HTTP 状态码）
     * @param message 错误提示
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 以 {@link HttpStatus} 指定错误码，避免手写数字。
     *
     * @param status  HTTP 状态码
     * @param message 错误提示
     */
    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.code = status.value();
    }

}
