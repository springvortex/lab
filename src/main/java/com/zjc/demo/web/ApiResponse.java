package com.zjc.demo.web;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.constant.ErrorCodeConstant;
import com.zjc.demo.constant.TraceConstant;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.MDC;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 全局统一接口响应封装，REST Controller 默认返回实体。
 *
 * <p>
 * 统一响应结构为 {@code success + code + message + data + traceId + timestamp}。前端只需判断
 * {@code success} 即可分支处理，{@code code} 供细粒度错误区分。默认使用
 * {@link ApiResponseConstant} 的标准编码，也支持自定义 {@link ErrorCodeConstant}。
 *
 * <p>
 * <b>核心约定：{@code code} 同时就是 HTTP 响应状态码。</b>
 * 这样设计后，异常不再一律返回 200，网关重试、前端拦截器、APM 告警都能按状态码工作，
 * 而响应体里仍保留一份同样的 {@code code}，便于调用方不用读 HTTP 头也能判错。
 *
 * <p>
 * 取值必须是合法 HTTP 状态码（100~599），推荐直接使用 {@link ApiResponseConstant} 或
 * {@link HttpStatus}。若传入非法值（例如业务自定义的非 HTTP 语义编码 10001），
 * 会在 {@link #httpStatus()} 处兜底为 500，详见该方法说明。
 *
 * <p>
 * <b>使用示例：</b>
 *
 * <pre>{@code
 * // 成功（HTTP 200）
 * ApiResponse<User> ok = ApiResponse.success(user);
 * ApiResponse<Void> ok2 = ApiResponse.successMessage("操作成功");
 *
 * // 失败（HTTP 400）
 * ApiResponse<Void> fail = ApiResponse.failureMessage("参数非法");
 *
 * // 指定状态码
 * ApiResponse<Void> fail2 = ApiResponse.failure(ApiResponseConstant.NOT_FOUND);
 *
 * // 需要带状态码返回时（异常处理器里最常见）
 * return ApiResponse.failure(ApiResponseConstant.NOT_FOUND).toResponseEntity();
 * }</pre>
 *
 * @param <T> 响应数据泛型
 * @author jiancai.zhong
 */
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1492116327070318294L;
    /**
     * 响应生成时间戳（毫秒），实例创建时固定，不对外提供 setter
     */
    @Getter
    private final Long timestamp = System.currentTimeMillis();
    /**
     * 是否请求成功
     *
     * <p>
     * 只生成 setter，读取统一走 {@link #isSuccess()}，避免 Boolean 为空时拆箱异常。
     */
    @Setter
    protected Boolean success;
    /**
     * 响应码，<b>同时用作 HTTP 响应状态码</b>，必须是合法 HTTP 状态码（100~599）。
     *
     * <p>
     * 默认 {@code 200}，即 {@link ApiResponseConstant#SUCCESS}。
     */
    @Getter
    @Setter
    private Integer code = ApiResponseConstant.SUCCESS.code();
    /**
     * 响应提示信息
     */
    @Getter
    @Setter
    private String message = ApiResponseConstant.SUCCESS.message();
    /**
     * 业务返回数据
     */
    @Getter
    @Setter
    private T data;
    /**
     * 本次请求的链路追踪 ID，与日志里的 traceId 一致，便于前后端协同排查。
     *
     * <p>
     * <b>自动填充：</b>实例创建时从 {@link MDC} 读取 {@link TraceConstant#MDC_KEY}，
     * 因此无论 Controller 正常返回还是异常处理器返回，都不需要业务代码手动设置。
     * 非 HTTP 场景（定时任务、单元测试、消息消费）下该值为 {@code null}。
     *
     * <p>
     * 若需要覆盖（例如 MQ 消费场景回填上游 traceId），可通过 setter 显式赋值。
     */
    @Getter
    @Setter
    private String traceId = MDC.get(TraceConstant.MDC_KEY);

    /**
     * 构建一个默认为「成功」的响应对象：{@code code=200}、{@code message=操作成功}，
     * {@code success} 与 {@code data} 为 {@code null}。
     *
     * <p>
     * 业务代码通常不直接调用本构造器，而应使用 {@code success} / {@code failure} 系列工厂方法
     * 或 {@link Builder}，以免遗漏 {@code success} 标志。保留公开构造器是为了兼容
     * Jackson 反序列化等需要无参构造的场景（{@code success} 字段单独提供了 setter）。
     */
    public ApiResponse() {
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
     * @return 成功响应，{@code code} 为 200
     */
    public static <T> ApiResponse<T> success() {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        return response;
    }

    /**
     * 成功响应，携带返回数据。
     *
     * @param data 返回数据
     * @param <T>  响应数据泛型
     * @return 成功响应，{@code data} 为传入的数据
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    /**
     * 成功响应，自定义提示信息，无数据。
     *
     * <p>
     * 方法名显式带 {@code Message}，避免与 {@link #success(Object)} 在 {@code T=String} 时
     * 产生重载歧义：若命名为 {@code success(String)}，调用 {@code success("x")} 会被绑定到
     * 本方法，字符串被当作提示信息而非业务数据。
     *
     * @param message 提示文案
     * @param <T>     响应数据泛型
     * @return 成功响应，{@code message} 为传入的文案
     */
    public static <T> ApiResponse<T> successMessage(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        return response;
    }

    /**
     * 成功响应，自定义提示信息 + 返回数据。
     *
     * @param message 提示文案
     * @param data    返回数据
     * @param <T>     响应数据泛型
     * @return 成功响应，同时携带提示文案与数据
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    /**
     * 默认失败响应，{@code code} 为 {@link ApiResponseConstant#FAILURE}（400）。
     *
     * @param <T> 响应数据泛型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure() {
        return failure(ApiResponseConstant.FAILURE);
    }

    /**
     * 默认失败响应，附带自定义数据。
     *
     * @param data 错误附属数据，例如字段级校验结果明细
     * @param <T>  响应数据泛型
     * @return 失败响应，{@code data} 为传入的数据
     */
    public static <T> ApiResponse<T> failure(T data) {
        ApiResponse<T> response = baseFailure();
        response.setData(data);
        return response;
    }

    /**
     * 默认失败响应，自定义错误信息。
     *
     * <p>
     * 方法名显式带 {@code Message}，避免与 {@link #failure(Object)} 在 {@code T=String} 时
     * 产生重载歧义。
     *
     * @param message 错误提示
     * @param <T>     响应数据泛型
     * @return 失败响应，{@code message} 为传入的文案
     */
    public static <T> ApiResponse<T> failureMessage(String message) {
        ApiResponse<T> response = baseFailure();
        response.setMessage(message);
        return response;
    }

    /**
     * 自定义错误码 + 错误信息。
     *
     * <p>
     * {@code code} 应为合法 HTTP 状态码；传入非法值时响应体照原样保留该值，
     * 但实际 HTTP 状态码会在 {@link #httpStatus()} 处兜底成 500。
     *
     * @param code    响应码（HTTP 状态码）
     * @param message 错误信息
     * @param <T>     响应数据泛型
     * @return 失败响应，携带指定的状态码与文案
     */
    public static <T> ApiResponse<T> failure(Integer code, String message) {
        ApiResponse<T> response = baseFailure();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    /**
     * 以 {@link HttpStatus} 构造失败响应，避免手写状态码数字。
     *
     * @param status  HTTP 状态码
     * @param message 错误信息
     * @param <T>     响应数据泛型
     * @return 失败响应，携带指定的状态码与文案
     */
    public static <T> ApiResponse<T> failure(HttpStatus status, String message) {
        return failure(status.value(), message);
    }

    /**
     * 使用枚举构建失败响应，推荐业务异常场景使用。
     *
     * @param responseEnum 响应枚举，提供状态码与默认文案
     * @param <T>          响应数据泛型
     * @return 失败响应，携带枚举的状态码与文案
     */
    public static <T> ApiResponse<T> failure(ApiResponseConstant responseEnum) {
        ApiResponse<T> response = baseFailure();
        response.setCode(responseEnum.code());
        response.setMessage(responseEnum.message());
        return response;
    }

    /**
     * 构造失败响应的公共底板：{@code success=false} 且状态码、文案取
     * {@link ApiResponseConstant#FAILURE}。
     *
     * <p>
     * 各 {@code failure} 重载在此之上覆盖不同字段，避免重复代码。私有方法，
     * 之所以让每个重载各自调用（而不是先 new 再改字段），是为了让每处默认值的来源
     * 在调用点可见。
     *
     * @param <T> 响应数据泛型
     * @return 已置为失败状态的响应对象
     */
    private static <T> ApiResponse<T> baseFailure() {
        ApiResponse<T> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(false);
        apiResponse.setCode(ApiResponseConstant.FAILURE.code());
        apiResponse.setMessage(ApiResponseConstant.FAILURE.message());
        return apiResponse;
    }

    /**
     * 获取链式构建器入口。
     *
     * <p>
     * 使用示例：
     *
     * <pre>{@code
     * ApiResponse<User> resp = ApiResponse.<User>builder().ok().data(user).build();
     * }</pre>
     *
     * @param <T> 响应数据泛型
     * @return 新的构建器实例
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 判断当前响应是否为成功状态。
     *
     * @return {@code success} 字段为 {@code true} 时返回 {@code true}，为 {@code null} 时返回
     * {@code false}
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * 解析 {@link #code} 对应的 HTTP 状态码。
     *
     * <p>
     * {@code code} 为 {@code null} 或不在 HTTP 状态码取值范围内时，兜底返回
     * {@link HttpStatus#INTERNAL_SERVER_ERROR}（500），避免因非法状态码导致
     * {@code HttpStatus.valueOf} 抛 {@link IllegalArgumentException} 而把请求打挂。
     *
     * <p>
     * 若新项目需要「HTTP 状态码 + 业务细粒度错误码」两套编码并存，建议在此扩展一个独立的
     * {@code subCode} 字段承载业务码，而不要让 {@code code} 脱离 HTTP 语义。
     *
     * @return 与 {@code code} 对应的 HTTP 状态码
     */
    public HttpStatus httpStatus() {
        return resolveStatus(code);
    }

    /**
     * 转换为 {@link ResponseEntity}，HTTP 状态码取自 {@link #code}，响应体为当前对象。
     *
     * <p>
     * 供 {@code @RestControllerAdvice} 异常处理器使用，使异常不再一律返回 200。Controller
     * 正常返回场景无需调用——直接返回 {@code ApiResponse} 时，Spring 默认按 200 处理。
     *
     * @return 携带正确 HTTP 状态码的 {@link ResponseEntity}
     */
    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.status(httpStatus()).body(this);
    }

    /**
     * {@link ApiResponse} 链式构建器，适用于需要条件化设置多个字段的场景。
     *
     * <p>
     * 更简单的场景请优先使用 {@code success} / {@code failure} 系列静态工厂方法，
     * 代码更短且不易漏设 {@code success} 标志。
     *
     * @param <T> 响应数据泛型
     * @author jiancai.zhong
     */
    public static final class Builder<T> {

        /**
         * 是否成功，默认 {@code false}，需通过 {@link #ok()} 或 {@link #success(boolean)} 设置
         */
        private boolean success;
        /**
         * 响应码，{@code null} 时沿用 {@link ApiResponse} 的默认值（200）
         */
        private Integer code;
        /**
         * 响应提示信息，{@code null} 时沿用 {@link ApiResponse} 的默认值
         */
        private String message;
        /**
         * 业务返回数据
         */
        private T data;

        /**
         * 构建器由 {@link ApiResponse#builder()} 创建，各字段初始为 {@code null} 或
         * {@code false}，需通过 {@link #ok()} / {@link #fail()} 或逐个 setter 显式设置。
         */
        public Builder() {
        }

        /**
         * 快速初始化成功配置：{@code success=true}，状态码与文案取
         * {@link ApiResponseConstant#SUCCESS}。
         *
         * @return 当前构建器，便于继续链式调用
         */
        public Builder<T> ok() {
            this.success = true;
            this.code = ApiResponseConstant.SUCCESS.code();
            this.message = ApiResponseConstant.SUCCESS.message();
            return this;
        }

        /**
         * 快速初始化失败配置：{@code success=false}，状态码与文案取
         * {@link ApiResponseConstant#FAILURE}。
         *
         * @return 当前构建器，便于继续链式调用
         */
        public Builder<T> fail() {
            this.success = false;
            this.code = ApiResponseConstant.FAILURE.code();
            this.message = ApiResponseConstant.FAILURE.message();
            return this;
        }

        /**
         * 设置成功标志。
         *
         * @param success 是否成功
         * @return 当前构建器，便于继续链式调用
         */
        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * 设置响应码，取值应为合法 HTTP 状态码。
         *
         * @param code 响应码
         * @return 当前构建器，便于继续链式调用
         */
        public Builder<T> code(Integer code) {
            this.code = code;
            return this;
        }

        /**
         * 设置提示信息。
         *
         * @param message 提示信息
         * @return 当前构建器，便于继续链式调用
         */
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 设置业务数据。
         *
         * @param data 业务数据
         * @return 当前构建器，便于继续链式调用
         */
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        /**
         * 构建最终响应对象。
         *
         * <p>
         * {@code code} 或 {@code message} 未被显式设置时，保留 {@link ApiResponse} 的默认值
         * （成功时的 200 与默认文案），而不是置为空。
         *
         * @return 构建完成的响应对象
         */
        public ApiResponse<T> build() {
            ApiResponse<T> response = new ApiResponse<>();
            response.setSuccess(success);
            if (code != null) {
                response.setCode(code);
            }
            if (message != null) {
                response.setMessage(message);
            }
            response.setData(data);
            return response;
        }
    }
}
