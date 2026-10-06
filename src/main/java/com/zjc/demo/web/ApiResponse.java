package com.zjc.demo.web;

import java.io.Serial;
import java.io.Serializable;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.constant.ErrorCodeConstant;
import com.zjc.demo.constant.TraceConstant;
import com.zjc.demo.util.MessageUtils;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全局统一接口响应封装，REST Controller 默认返回实体。
 *
 * <p>
 * 统一响应结构为 {@code success + code + message + data + traceId + timestamp}。前端只需判断
 * {@code success} 即可分支处理，{@code code} 供细粒度错误区分。默认使用 {@link ApiResponseConstant}
 * 的标准编码，也支持自定义 {@link ErrorCodeConstant}。
 *
 * <p>
 * <b>核心约定：{@code code} 同时就是 HTTP 响应状态码。</b> 这样设计后，异常不再一律返回 200，网关重试、前端拦截器、APM
 * 告警都能按状态码工作， 而响应体里仍保留一份同样的 {@code code}，便于调用方不用读 HTTP 头也能判错。
 *
 * <p>
 * 取值必须是合法 HTTP 状态码（100~599），推荐直接使用 {@link ApiResponseConstant} 或
 * {@link HttpStatus}。若传入非法值（例如业务自定义的非 HTTP 语义编码 10001）， 会在
 * {@link #httpStatus()} 处兜底为 500，详见该方法说明。
 *
 * <p>
 * <b>国际化：{@code message} 默认按请求语言自动取词。</b> 语言由 {@code Accept-Language}
 * 请求头决定（见 {@code config/I18nConfig}），业务代码无需传入文案：
 *
 * <pre>{@code
 * // zh-CN 请求 -> {"code":200,"message":"操作成功",...}
 * // en-US 请求 -> {"code":200,"message":"Success",...}
 * ApiResponse<User> ok = ApiResponse.success(user);
 * }</pre>
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
 * // 业务自定义错误码枚举（实现 ErrorCodeConstant 即可，同样支持国际化）
 * ApiResponse<Void> fail3 = ApiResponse.failure(UserErrorCode.USER_DISABLED);
 *
 * // 需要带状态码返回时（异常处理器里最常见）
 * return ApiResponse.failure(ApiResponseConstant.NOT_FOUND).toResponseEntity();
 * }</pre>
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
	 * 是否请求成功
	 *
	 * <p>
	 * 读取统一走 {@link #isSuccess()}，避免 Boolean 为空时拆箱异常。
	 */
	private Boolean success;
	/**
	 * 响应码，<b>同时用作 HTTP 响应状态码</b>，必须是合法 HTTP 状态码（100~599）。
	 *
	 * <p>
	 * 默认 {@code 200}，即 {@link ApiResponseConstant#SUCCESS}。
	 */
	private Integer code = ApiResponseConstant.SUCCESS.code();
	/**
	 * 响应提示信息，按请求语言自动取词。
	 *
	 * <p>
	 * 默认值是 {@code response.success} 的<b>当前语言</b>文案，由 {@link MessageUtils} 在实例创建时解析，
	 * 因此同一个接口在 {@code Accept-Language: zh-CN} 与 {@code en-US} 下会返回不同文案，
	 * 业务代码不需要做任何事。
	 *
	 * <p>
	 * 提醒：解析发生在<b>字段初始化</b>期间，即每次 {@code new ApiResponse(...)} 都重新取词。
	 * 这与 {@link #traceId} 从 MDC 读取是同一机制，都不依赖构造器参数。
	 */
	private String message = resolveMessage(ApiResponseConstant.SUCCESS);
	/**
	 * 业务返回数据
	 */
	private T data;
	/**
	 * 本次请求的链路追踪 ID，与日志里的 traceId 一致，便于前后端协同排查。
	 *
	 * <p>
	 * <b>自动填充：</b>实例创建时从 {@link MDC} 读取 {@link TraceConstant#MDC_KEY}， 因此无论
	 * Controller 正常返回还是异常处理器返回，都不需要业务代码手动设置。 非 HTTP 场景（定时任务、单元测试、消息消费）下该值为
	 * {@code null}。
	 *
	 * <p>
	 * 若需要覆盖（例如 MQ 消费场景回填上游 traceId），可通过 setter 显式赋值。
	 */
	private String traceId = MDC.get(TraceConstant.MDC_KEY);
	/**
	 * 响应生成时间戳（毫秒），实例创建时固定，<b>不可被外部覆盖</b>。
	 *
	 * <p>
	 * 三条入口都已封死，靠的是「{@code final} + 手写构造器挂 {@code @Builder}」这一组合：
	 * <ul>
	 * <li>{@code final} 让 {@code @Data} 不生成 {@code setTimestamp}；</li>
	 * <li>{@code @Builder} 挂在下面的手写构造器上（<b>不是</b>挂在字段或类上），因此构建器只认
	 * 构造器参数里出现的字段，{@code timestamp} 根本不会出现在构建器的方法列表里；</li>
	 * <li>没有 {@code @AllArgsConstructor}，也就没有「全参构造器塞入时间戳」这条路径。</li>
	 * </ul>
	 *
	 * <p>
	 * 反面教材：若把 {@code @Builder} 标在类上并配 {@code @Builder.Default}，Lombok 会为
	 * {@code timestamp} 生成 {@code timestamp(Long)} 构建方法，外部即可改写——这正是本类刻意
	 * 避开的写法。相关行为已由 {@code ApiResponseBuilderTest} 用反射钉死。
	 */
	private final Long timestamp = System.currentTimeMillis();

	/**
	 * 供 {@code @Builder} 使用的构造器，<b>参数刻意只收业务字段</b>。
	 *
	 * <p>
	 * 时间戳与 traceId 都不在参数里：前者由字段初始值提供（不可覆盖），后者从 {@link MDC} 读取， 需要覆盖 traceId 时走它的
	 * setter。
	 *
	 * <p>
	 * <b>默认值在这里回填</b>：{@code code} / {@code message} 为 {@code null} 时取
	 * {@link ApiResponseConstant#SUCCESS}，与旧手写 Builder 的行为一致——未设置与显式传 {@code null}
	 * 都回落默认值。注意这与 {@code @Builder.Default} 的语义不同，后者只对 「未调用 setter」生效、显式传
	 * {@code null} 会真的置空。
	 *
	 * <p>
	 * {@code message} 的默认值走 {@link MessageUtils} 取当前语言文案，因此「未指定文案」时，
	 * 响应语言跟随请求的 {@code Accept-Language}。
	 *
	 * <p>
	 * 业务代码优先使用 {@code success} / {@code failure} 工厂方法或 {@code builder()}，
	 * 不要直接调用本构造器。
	 *
	 * @param success 是否成功
	 * @param code    响应码（HTTP 状态码），{@code null} 时取 200
	 * @param message 提示信息，{@code null} 时取当前语言的默认文案
	 * @param data    业务数据
	 */
	@Builder
	public ApiResponse(Boolean success, Integer code, String message, T data) {
		this.success = success;
		this.code = code == null ? ApiResponseConstant.SUCCESS.code() : code;
		this.message = message == null ? resolveMessage(ApiResponseConstant.SUCCESS) : message;
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
	 * <p>
	 * 不显式指定文案，由构造器回填 {@code response.success} 的当前语言文案，因此请求带
	 * {@code Accept-Language: en-US} 时返回 {@code "Success"}。
	 *
	 * @param <T> 响应数据泛型
	 * @return 成功响应，{@code code} 为 200
	 */
	public static <T> ApiResponse<T> success() {
		return ApiResponse.<T>builder().success(true).build();
	}

	/**
	 * 成功响应，携带返回数据。
	 *
	 * <p>
	 * 文案同样走国际化默认值，见 {@link #success()}。
	 *
	 * @param data 返回数据
	 * @param <T>  响应数据泛型
	 * @return 成功响应，{@code data} 为传入的数据
	 */
	public static <T> ApiResponse<T> success(T data) {
		return ApiResponse.<T>builder().success(true).data(data).build();
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
		return ApiResponse.<T>builder().success(true).message(message).build();
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
		return ApiResponse.<T>builder().success(true).message(message).data(data).build();
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
	 * <p>
	 * 提示文案取 {@link ApiResponseConstant#FAILURE} 的<b>当前语言</b>文案。
	 *
	 * @param data 错误附属数据，例如字段级校验结果明细
	 * @param <T>  响应数据泛型
	 * @return 失败响应，{@code data} 为传入的数据
	 */
	public static <T> ApiResponse<T> failure(T data) {
		return ApiResponse.<T>builder().success(false).code(ApiResponseConstant.FAILURE.code())
				.message(resolveMessage(ApiResponseConstant.FAILURE)).data(data).build();
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
		return ApiResponse.<T>builder().success(false).code(ApiResponseConstant.FAILURE.code()).message(message)
				.build();
	}

	/**
	 * 自定义错误码 + 错误信息。
	 *
	 * <p>
	 * {@code code} 应为合法 HTTP 状态码；传入非法值时响应体照原样保留该值， 但实际 HTTP 状态码会在
	 * {@link #httpStatus()} 处兜底成 500。
	 *
	 * @param code    响应码（HTTP 状态码）
	 * @param message 错误信息
	 * @param <T>     响应数据泛型
	 * @return 失败响应，携带指定的状态码与文案
	 */
	public static <T> ApiResponse<T> failure(Integer code, String message) {
		return ApiResponse.<T>builder().success(false).code(code).message(message).build();
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
	 * <p>
	 * 提示文案按请求的 {@code Accept-Language} 解析：先取枚举的
	 * {@link ApiResponseConstant#messageKey()} 去资源文件里查，查不到才回落到枚举自带的固定文案。
	 *
	 * @param responseEnum 响应枚举，提供状态码与默认文案
	 * @param <T>          响应数据泛型
	 * @return 失败响应，携带枚举的状态码与当前语言的文案
	 */
	public static <T> ApiResponse<T> failure(ApiResponseConstant responseEnum) {
		return ApiResponse.<T>builder().success(false).code(responseEnum.code()).message(resolveMessage(responseEnum))
				.build();
	}

	/**
	 * 使用自定义 {@link ErrorCodeConstant} 枚举构建失败响应。
	 *
	 * <p>
	 * 相比 {@link #failure(ApiResponseConstant)}，本重载面向业务方自己的错误码枚举。与枚举
	 * {@code messageKey()} 为 {@code null}（未覆写）时自动回退到 {@code message()} 固定文案，
	 * 因此<b>旧的自定义枚举不改一行代码也能直接用</b>。
	 *
	 * @param errorCode 自定义错误码枚举
	 * @param <T>       响应数据泛型
	 * @return 失败响应，携带枚举的状态码与当前语言的文案
	 */
	public static <T> ApiResponse<T> failure(ErrorCodeConstant errorCode) {
		return ApiResponse.<T>builder().success(false).code(errorCode.code()).message(resolveMessage(errorCode))
				.build();
	}

	/**
	 * 解析枚举对应的提示文案：优先按 {@code messageKey()} 取当前语言的翻译，取不到则回落固定文案。
	 *
	 * <p>
	 * <b>为什么回落到 {@code message()} 而不是 key：</b>枚举的 {@code message()} 是一句完整的话
	 * （如「资源不存在」），而 key（如 {@code response.not-found}）不是。回落到 key 会让调用方看到
	 * 一个英文标识符，体验更差。真正需要「暴露 key 以便排查漏配」的场景是业务方自己调
	 * {@code MessageUtils}，那时 {@code useCodeAsDefaultMessage} 会返回 key。
	 *
	 * <p>
	 * 具体回落边界（含「无 Spring 容器」与「资源漏配」两种）由
	 * {@link MessageUtils#getMessageOrDefault(String, String, Locale)} 统一处理，
	 * 与 {@code BusinessException} 共用同一份实现。
	 *
	 * @param errorCode 错误码枚举
	 * @return 非 {@code null} 的提示文案
	 */
	private static String resolveMessage(ErrorCodeConstant errorCode) {
		return MessageUtils.getMessageOrDefault(errorCode.messageKey(), errorCode.message(), null);
	}

	/**
	 * 判断当前响应是否为成功状态。
	 *
	 * @return {@code success} 字段为 {@code true} 时返回 {@code true}，为 {@code null} 时返回
	 *         {@code false}
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
	 * 若新项目需要「HTTP 状态码 + 业务细粒度错误码」两套编码并存，建议在此扩展一个独立的 {@code subCode} 字段承载业务码，而不要让
	 * {@code code} 脱离 HTTP 语义。
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
}
