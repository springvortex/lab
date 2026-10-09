package com.zjc.demo.exception;

import java.util.stream.Collectors;

import org.springdoc.api.OpenApiResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.web.ApiResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器，统一拦截各层抛出的异常并用 {@link ApiResponse} 包装返回。
 *
 * <p>
 * 异常处理优先级：业务异常 &gt; 参数校验异常 &gt; 请求异常 &gt; 兜底异常。 所有异常均会记录日志，便于排查。
 *
 * <p>
 * <b>HTTP 状态码约定：</b>响应体里的 {@code code} 即 HTTP 状态码，本类一律通过
 * {@link ApiResponse#toResponseEntity()} 返回，使 400 / 401 / 404 / 405 / 500
 * 等状态码如实透出给调用方、网关与 APM，而不是统一 200。响应体的 {@code code} 与 HTTP 状态码必须保持一致。
 *
 * <p>
 * <b>处理不到的场景（重要）：</b>
 * <ul>
 * <li>本类由 Spring MVC 的 {@code HandlerExceptionResolver} 调用，因此只能拦截
 * <b>DispatcherServlet 之后</b>抛出的异常。{@code Filter}（含 {@code TraceIdFilter}） 或
 * Servlet 容器层面抛出的异常不会进入这里，需在 Filter 内部自行处理；</li>
 * <li>兜底方法接收的是 {@link Exception}，{@link Error}（如 {@code OutOfMemoryError}）
 * 不会被捕获；</li>
 * <li>异步线程（{@code @Async}、自定义线程池）里抛出的异常不在当前请求线程上， 同样不会被拦截，需在
 * {@code AsyncUncaughtExceptionHandler} 里处理；</li>
 * <li>开关 {@code server.error.include-stacktrace} 控制的是 Boot 自带 {@code /error}
 * 端点；本类返回的响应体不含堆栈，堆栈只进服务端日志。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 多条校验错误信息的拼接分隔符。
	 */
	private static final String ERROR_DELIMITER = "; ";

	/**
	 * 不需要记录日志的静态资源路径名单。
	 *
	 * <p>
	 * 浏览器、iOS 与各类爬虫会自动请求这些文件，服务端没有对应资源时返回 404 属于正常现象。 若把它们一并记为
	 * WARN，日志里会堆出大量无意义的「请求路径不存在」，掩盖真正的错误请求。
	 *
	 * <p>
	 * 匹配的是 {@code NoResourceFoundException} 的 {@code resourcePath}，即<b>不含前导斜杠</b>
	 * 的资源路径（如 {@code favicon.ico}）。新增条目时请保持同样的书写方式， 带不带前导斜杠都会被
	 * {@link #isIgnoredResource(String)} 归一化处理。
	 */
	private static final String[] IGNORED_RESOURCE_PATHS = {
			// 浏览器标签页图标，所有现代浏览器都会自动请求
			"favicon.ico",
			// iOS Safari 添加到主屏时请求的图标
			"apple-touch-icon.png",
			// 旧版 iOS 的兼容写法
			"apple-touch-icon-precomposed.png" };

	/**
	 * 判断资源路径是否属于「不记日志」的名单。
	 *
	 * <p>
	 * 比较前会去掉前导斜杠，使 {@code /favicon.ico} 与 {@code favicon.ico} 都能命中，
	 * 避免因路径写法差异导致过滤失效。
	 *
	 * @param resourcePath 资源路径，可能为 {@code null}
	 * @return 命中忽略名单返回 {@code true}
	 */
	private static boolean isIgnoredResource(String resourcePath) {
		if (resourcePath == null) {
			return false;
		}
		String normalizedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
		for (String ignoredPath : IGNORED_RESOURCE_PATHS) {
			if (ignoredPath.equals(normalizedPath)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 业务异常：透传异常自身的错误码与提示信息。
	 *
	 * <p>
	 * {@link BusinessException} 的 {@code code} 应为合法 HTTP 状态码；若业务传入非法值 （例如非 HTTP 语义的
	 * 10001），{@link ApiResponse#httpStatus()} 会兜底为 500。
	 *
	 * @param e 业务异常
	 * @return 失败响应，HTTP 状态码取自异常自身的 {@code code}
	 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
		return ApiResponse.<Void>failure(e.getCode(), e.getMessage()).toResponseEntity();
	}

	/**
	 * {@code @RequestBody} 校验失败（{@code @Valid} + {@code @RequestBody}）。
	 *
	 * @param e 参数校验异常
	 * @return 400 响应，提示具体校验不通过的字段
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream().map(this::formatFieldError)
				.collect(Collectors.joining(ERROR_DELIMITER));
		log.warn("参数校验失败: {}", message);
		return ApiResponse.<Void>failure(ApiResponseConstant.PARAM_INVALID.code(), message).toResponseEntity();
	}

	/**
	 * 表单参数校验失败（{@code @Validated} + 对象绑定）。
	 *
	 * @param e 参数绑定异常
	 * @return 400 响应，提示具体校验不通过的字段
	 */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
		String message = e.getBindingResult().getFieldErrors().stream().map(this::formatFieldError)
				.collect(Collectors.joining(ERROR_DELIMITER));
		log.warn("参数绑定失败: {}", message);
		return ApiResponse.<Void>failure(ApiResponseConstant.PARAM_INVALID.code(), message).toResponseEntity();
	}

	/**
	 * {@code @Validated} 标注的类经 AOP 代理后，方法参数校验失败抛出的异常。
	 *
	 * <p>
	 * <b>注意适用范围：</b>Spring 6.1 起，Controller 方法参数上直接挂约束注解 （{@code @RequestParam} /
	 * {@code @PathVariable} 等）抛的是 {@link HandlerMethodValidationException}，由
	 * {@link #handleMethodValidation(HandlerMethodValidationException)} 处理； 本方法处理的是
	 * {@code @Validated} + AOP 代理场景（常见于 Service 层方法入参校验）。
	 *
	 * @param e 约束违反异常
	 * @return 400 响应，提示具体违反的约束
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
		String message = e.getConstraintViolations().stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
				.collect(Collectors.joining(ERROR_DELIMITER));
		log.warn("方法参数校验失败: {}", message);
		return ApiResponse.<Void>failure(ApiResponseConstant.PARAM_INVALID.code(), message).toResponseEntity();
	}

	/**
	 * Controller 方法参数上的约束注解校验失败（Spring 6.1+ 的默认行为）。
	 *
	 * <p>
	 * 在 {@code @RequestParam} / {@code @PathVariable} / {@code @RequestHeader} 上直接挂
	 * {@code @NotBlank}、{@code @Min} 这类约束时，Spring 6.1 起抛的是本异常， <b>不再是</b>
	 * {@link ConstraintViolationException}。若只处理后者，这类「客户端传参不合法」 会一路落到兜底的
	 * {@code Exception} 分支，被当成服务端故障报成 <b>500 并打一整条 ERROR
	 * 堆栈</b>——既误导排查方向，又容易被监控当成线上故障误告警。
	 *
	 * <p>
	 * 与 {@link #handleConstraintViolation(ConstraintViolationException)} 的输出格式保持一致，
	 * 均为 {@code 参数名: 原因} 的多条拼接。交叉参数（cross-parameter）约束的提示未纳入， 实际业务中极少使用。
	 *
	 * @param e 方法参数校验异常
	 * @return 400 响应，提示具体不通过的参数与原因
	 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
		String message = e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream().map(
						error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
				.collect(Collectors.joining(ERROR_DELIMITER));
		log.warn("方法参数校验失败: {}", message);
		return ApiResponse.<Void>failure(ApiResponseConstant.PARAM_INVALID.code(), message).toResponseEntity();
	}

	/**
	 * 缺少必填的 {@code @RequestParam}。
	 *
	 * @param e 缺少参数异常
	 * @return 400 响应，提示缺少的参数名
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
		String message = "缺少必填参数: " + e.getParameterName();
		log.warn(message);
		return ApiResponse.<Void>failure(ApiResponseConstant.PARAM_INVALID.code(), message).toResponseEntity();
	}

	/**
	 * 请求体无法解析（JSON 格式错误或缺失）。
	 *
	 * <p>
	 * 异常消息非空时透传 {@code e.getMessage()}（如 JSON 解析失败的具体位置）， 消息为空时回退到默认提示。
	 *
	 * @param e 消息解析异常
	 * @return 400 响应，提示请求体格式错误
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
		log.warn("请求体解析失败: {}", e.getMessage());
		String message = e.getMessage() != null ? e.getMessage() : ApiResponseConstant.BAD_REQUEST.message();
		return ApiResponse.<Void>failure(ApiResponseConstant.BAD_REQUEST.code(), message).toResponseEntity();
	}

	/**
	 * 请求体媒体类型不支持（Content-Type 不匹配或缺失）。
	 *
	 * @param e 媒体类型异常
	 * @return 415 响应
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		log.warn("不支持的请求体类型: {}", e.getContentType());
		return ApiResponse.<Void>failure(ApiResponseConstant.UNSUPPORTED_MEDIA_TYPE).toResponseEntity();
	}

	/**
	 * 客户端要求的响应类型无法产出（Accept 头不匹配）。
	 *
	 * @param e 媒体类型异常
	 * @return 406 响应
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
		log.warn("无法产出客户端要求的响应类型: {}", e.getMessage());
		return ApiResponse.<Void>failure(HttpStatus.NOT_ACCEPTABLE, "无法产出客户端要求的响应类型").toResponseEntity();
	}

	/**
	 * 请求路径不存在。
	 *
	 * <p>
	 * 命中 {@link #IGNORED_RESOURCE_PATHS} 的请求（如浏览器自动请求的 favicon）不记录日志， 其余路径记 WARN
	 * 便于排查错误链接；两种情况都返回 404，不改变响应结果。
	 *
	 * @param e 资源未找到异常
	 * @return 404 响应
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
		String resourcePath = e.getResourcePath();
		if (isIgnoredResource(resourcePath)) {
			// 浏览器自动请求，属于正常行为，不记录日志
			return ApiResponse.<Void>failure(ApiResponseConstant.NOT_FOUND).toResponseEntity();
		}
		log.warn("请求路径不存在: {}", resourcePath);
		return ApiResponse.<Void>failure(ApiResponseConstant.NOT_FOUND).toResponseEntity();
	}

	/**
	 * 接口文档分组不存在。
	 *
	 * <p>
	 * springdoc 在 {@code /v3/api-docs/{group}} 的 {@code {group}} 没匹配到任何已注册分组时抛出本异常。
	 * 它<b>只继承 {@code RuntimeException}</b>，既不是 {@link ErrorResponseException} 的子类、
	 * 也没实现 {@code ResponseStatus}，所以<b>接不住</b>下面的兜底分支，会被统一改写成 500 ——
	 * 语义上明明是「请求了一个不存在的分组」，却报成服务端故障，误导排查方向也容易触发误告警。
	 *
	 * <p>
	 * 本方法与 {@link #handleNoResourceFound(NoResourceFoundException)} 同为 404 语义： 调用方拿到的
	 * {@code code} 与 HTTP 状态码都是 404，响应体提示语统一走
	 * {@link ApiResponseConstant#NOT_FOUND}，不把 springdoc 的原始英文消息透出去。
	 *
	 * @param e 文档分组未找到异常
	 * @return 404 响应
	 */
	@ExceptionHandler(OpenApiResourceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleOpenApiResourceNotFound(OpenApiResourceNotFoundException e) {
		log.warn("接口文档分组不存在: {}", e.getMessage());
		return ApiResponse.<Void>failure(ApiResponseConstant.NOT_FOUND).toResponseEntity();
	}

	/**
	 * 请求方法不支持（如 POST 访问了 GET 接口）。
	 *
	 * @param e 方法不支持异常
	 * @return 405 响应，提示不支持的请求方法
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		log.warn("不支持的请求方法: {}", e.getMethod());
		return ApiResponse.<Void>failure(ApiResponseConstant.METHOD_NOT_ALLOWED.code(),
				ApiResponseConstant.METHOD_NOT_ALLOWED.message() + ": " + e.getMethod()).toResponseEntity();
	}

	/**
	 * 异步请求（{@code Callable} / {@code DeferredResult}）处理超时。
	 *
	 * @param e 异步超时异常
	 * @return 408 响应
	 */
	@ExceptionHandler(AsyncRequestTimeoutException.class)
	public ResponseEntity<ApiResponse<Void>> handleAsyncTimeout(AsyncRequestTimeoutException e) {
		log.warn("异步请求处理超时: {}", e.getMessage());
		return ApiResponse.<Void>failure(ApiResponseConstant.REQUEST_TIMEOUT).toResponseEntity();
	}

	/**
	 * 框架抛出的「自带状态码」异常，按异常状态码如实返回，而不是降级成 500。
	 *
	 * <p>
	 * Spring 6 起框架层大量使用 {@link ErrorResponseException} 及其子类
	 * {@link ResponseStatusException} 表达 4xx / 5xx，例如 API 版本管理抛出的
	 * {@code MissingApiVersionException}（没带版本）与
	 * {@code InvalidApiVersionException}（版本不在 supported 清单里）， 它们自带 400。
	 *
	 * <p>
	 * <b>不处理会出现什么：</b>这些异常会掉进下面的兜底 {@code Exception} 分支， 被统一改写成 <b>500 + ERROR
	 * 堆栈</b>——状态码语义丢失，调用方、网关与 APM 都会把它误判成服务端故障，而实际原因只是「请求少了个版本头」。
	 * 本方法按异常自带的状态码返回，4xx 用 WARN 记录（属于调用方问题）， 5xx 仍按 ERROR 记录并隐藏细节。
	 *
	 * <p>
	 * 注意本方法<b>不会</b>抢走更具体处理器的活：{@code NoResourceFoundException}、
	 * {@code HttpRequestMethodNotSupportedException} 等都有各自的
	 * {@code @ExceptionHandler}，Spring 优先选最具体的匹配。
	 *
	 * @param e 带状态码的框架异常
	 * @return 与异常状态码一致的响应
	 */
	@ExceptionHandler(ErrorResponseException.class)
	public ResponseEntity<ApiResponse<Void>> handleErrorResponse(ErrorResponseException e) {
		HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
		HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
		if (resolved.is5xxServerError()) {
			log.error("框架异常(状态码 {}): {}", resolved.value(), e.getMessage(), e);
			return ApiResponse.<Void>failure(ApiResponseConstant.INTERNAL_ERROR).toResponseEntity();
		}
		// 4xx 属于调用方用法问题，把框架给出的具体原因透出去更有助于联调（不含内部实现细节）
		String detail = e.getBody().getDetail();
		String message = detail == null ? resolved.getReasonPhrase() : detail;
		log.warn("框架异常(状态码 {}): {}", resolved.value(), message);
		return ApiResponse.<Void>failure(resolved.value(), message).toResponseEntity();
	}

	/**
	 * 兜底：未预期的异常，防止堆栈泄露给前端。
	 *
	 * <p>
	 * 未预期异常统一返回通用提示，避免数据库连接串、SQL 语句、内部路径等敏感信息通过异常消息泄露。 完整堆栈记录到服务端日志，且响应体里的
	 * {@code traceId} 会自动带上当前请求的链路 ID （由 {@code TraceIdFilter} 写入
	 * MDC、{@code ApiResponse} 构造时读取）， 用户报障时拿该 ID 即可在日志中定位到对应堆栈。
	 *
	 * @param e 未预期异常
	 * @return 500 响应，不包含异常细节
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("未预期异常", e);
		return ApiResponse.<Void>failure(ApiResponseConstant.INTERNAL_ERROR).toResponseEntity();
	}

	/**
	 * 格式化字段校验错误为 "字段名: 错误信息"。
	 *
	 * @param fieldError 字段校验错误
	 * @return 格式化后的错误描述
	 */
	private String formatFieldError(FieldError fieldError) {
		return fieldError.getField() + ": " + fieldError.getDefaultMessage();
	}
}
