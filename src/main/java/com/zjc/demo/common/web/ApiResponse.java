package com.zjc.demo.common.web;

import com.zjc.demo.common.constant.web.ApiResponseConstant;
import com.zjc.demo.common.constant.web.TraceConstant;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 统一接口响应封装：{@code success + code + message + data + traceId + timestamp}。
 *
 * <p>
 * {@code code} 同时就是 HTTP 响应状态码，取值必须是 100~599；非法值会在 {@link #httpStatus()} 兜底成 500。
 *
 * @param <T> 响应数据泛型
 * @author jiancai.zhong
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1492116327070318294L;
    /**
     * 响应时间戳（毫秒），不可被外部覆盖。
     *
     * <p>
     * 靠 {@code final} + 「{@code @Builder} 挂在手写构造器上」封死：构建器只认构造器参数，时间戳不在其中；
     * 也没有全参构造器这条路径。若把 {@code @Builder} 标到类上，Lombok 就会生成
     * {@code timestamp(Long)} 构建方法，外部即可改写。
     */
    private final Long timestamp = System.currentTimeMillis();
    /**
     * 是否请求成功。读取请走 {@link #isSuccess()}，避免拆箱异常。
     */
    private Boolean success;
    /**
     * 响应码，同时用作 HTTP 响应状态码。
     */
    private Integer code = ApiResponseConstant.SUCCESS.code();
    /**
     * 响应提示信息
     */
    private String message = ApiResponseConstant.SUCCESS.message();
    /**
     * 业务返回数据
     */
    private T data;
    /**
     * 链路追踪 ID，实例创建时从 {@link MDC} 读取，非 HTTP 场景为 {@code null}。
     */
    private String traceId = MDC.get(TraceConstant.MDC_KEY);

    /**
     * 供 {@code @Builder} 使用的构造器，参数只收业务字段。
     *
     * @param success 是否成功
     * @param code    响应码，{@code null} 时取 200
     * @param message 提示信息，{@code null} 时取默认文案
     * @param data    业务数据
     */
    @Builder
    public ApiResponse(Boolean success, Integer code, String message, T data) {
        this.success = success;
        this.code = code == null ? ApiResponseConstant.SUCCESS.code() : code;
        this.message = message == null ? ApiResponseConstant.SUCCESS.message() : message;
        this.data = data;
    }

    /**
     * 将 {@code code} 解析为 {@link HttpStatus}，非法取值兜底为 500。
     *
     * @param code 响应码
     * @return HTTP 状态码，永不为 {@code null}
     */
    public static HttpStatus resolveStatus(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(code);
        } catch (IllegalArgumentException e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 成功响应，无数据。
     *
     * @param <T> 响应数据泛型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder().success(true).build();
    }

    /**
     * 成功响应，携带数据。
     *
     * @param data 返回数据
     * @param <T>  响应数据泛型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    /**
     * 成功响应，自定义提示信息，无数据。
     *
     * <p>
     * 名字里的 {@code Message} 不能省：叫 {@code success(String)} 会和 {@link #success(Object)}
     * 在 {@code T=String} 时撞重载，字符串会被当成提示而不是数据。
     *
     * @param message 提示文案
     * @param <T>     响应数据泛型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> successMessage(String message) {
        return ApiResponse.<T>builder().success(true).message(message).build();
    }

    /**
     * 成功响应，自定义提示信息 + 数据。
     *
     * @param message 提示文案
     * @param data    返回数据
     * @param <T>     响应数据泛型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    /**
     * 默认失败响应（400）。
     *
     * @param <T> 响应数据泛型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure() {
        return failure(ApiResponseConstant.FAILURE);
    }

    /**
     * 默认失败响应，附带自定义数据（如字段级校验明细）。
     *
     * @param data 错误附属数据
     * @param <T>  响应数据泛型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(T data) {
        return ApiResponse.<T>builder().success(false).code(ApiResponseConstant.FAILURE.code())
                .message(ApiResponseConstant.FAILURE.message()).data(data).build();
    }

    /**
     * 默认失败响应，自定义错误信息。
     *
     * @param message 错误提示
     * @param <T>     响应数据泛型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failureMessage(String message) {
        return ApiResponse.<T>builder().success(false).code(ApiResponseConstant.FAILURE.code()).message(message)
                .build();
    }

    /**
     * 自定义错误码 + 错误信息。
     *
     * @param code    响应码（HTTP 状态码）
     * @param message 错误信息
     * @param <T>     响应数据泛型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(Integer code, String message) {
        return ApiResponse.<T>builder().success(false).code(code).message(message).build();
    }

    /**
     * 以 {@link HttpStatus} 构造失败响应，避免手写数字。
     *
     * @param status  HTTP 状态码
     * @param message 错误信息
     * @param <T>     响应数据泛型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(HttpStatus status, String message) {
        return failure(status.value(), message);
    }

    /**
     * 使用枚举构建失败响应。
     *
     * @param responseEnum 响应枚举
     * @param <T>          响应数据泛型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(ApiResponseConstant responseEnum) {
        return ApiResponse.<T>builder().success(false).code(responseEnum.code()).message(responseEnum.message())
                .build();
    }

    /**
     * 判断是否成功，{@code success} 为 {@code null} 时返回 {@code false}。
     *
     * @return 成功返回 {@code true}
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * 解析 {@link #code} 对应的 HTTP 状态码，非法值兜底为 500。
     *
     * @return 与 {@code code} 对应的 HTTP 状态码
     */
    public HttpStatus httpStatus() {
        return resolveStatus(code);
    }

    /**
     * 转换为携带正确 HTTP 状态码的 {@link ResponseEntity}，供异常处理器使用。
     * Controller 正常返回场景无需调用。
     *
     * @return 携带 HTTP 状态码的响应实体
     */
    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.status(httpStatus()).body(this);
    }
}
