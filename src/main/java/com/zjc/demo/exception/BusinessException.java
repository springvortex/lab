package com.zjc.demo.exception;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.constant.ErrorCodeConstant;
import lombok.Getter;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * 业务异常，供 Service / Controller 层抛出，由全局异常处理器统一拦截。
 *
 * <p>
 * 配合 {@link ErrorCodeConstant} 体系使用，错误码与提示信息由枚举统一管理：
 *
 * <pre>{@code
 * throw new BusinessException(ApiResponseConstant.NOT_FOUND);
 * throw new BusinessException(UserErrorCode.USER_DISABLED);
 * throw new BusinessException("自定义提示");
 * throw new BusinessException(404, "用户不存在");
 * throw new BusinessException(HttpStatus.CONFLICT, "数据已存在");
 * }</pre>
 *
 * <p>
 * <b>约定：{@code code} 就是 HTTP 状态码</b>，会被
 * {@code ApiResponse#httpStatus()} 用来驱动真实的 HTTP 响应状态。因此自定义 {@link ErrorCodeConstant}
 * 枚举时应让 {@code code()} 返回合法的 HTTP 状态码（100~599）；若返回了 0 / -1 / 10001
 * 这类非 HTTP 语义的值，响应会兜底成 500。
 *
 * <p>
 * <b>使用注意：</b>
 * <ul>
 * <li>它表示「可预期的业务失败」，由 {@code GlobalExceptionHandler} 按 WARN 级别记录一行文案，
 * <b>不会打印堆栈</b>。真正的程序缺陷请抛其他运行时异常，走 ERROR 级别与堆栈记录；</li>
 * <li>不要用它做流程控制，异常在 JVM 中构造堆栈有成本，正常分支请用返回值表达；</li>
 * <li>异常消息会原样返回给调用方，禁止拼接 SQL、表名、内部地址等敏感信息。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 8505723016403438176L;
    /**
     * 错误码，同时作为 HTTP 响应状态码使用。
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
     * 自定义提示信息，错误码默认 {@code 400}（通用失败）。
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
     * <p>
     * {@code code} 应为合法 HTTP 状态码，否则响应会兜底为 500。
     *
     * @param code    错误码（HTTP 状态码）
     * @param message 错误提示
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 以 {@link HttpStatus} 指定错误码 + 自定义提示信息，避免手写状态码数字。
     *
     * @param status  HTTP 状态码
     * @param message 错误提示
     */
    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.code = status.value();
    }

}
