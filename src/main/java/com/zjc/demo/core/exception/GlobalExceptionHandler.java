package com.zjc.demo.core.exception;

import com.zjc.demo.common.constant.web.ApiResponseConstant;
import com.zjc.demo.common.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.api.OpenApiResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
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

/**
 * 全局异常处理器。响应体的 {@code code} 与 HTTP 状态码保持一致，全部通过
 * {@link ApiResponse#toResponseEntity()} 返回。
 *
 * <p>
 * 只能拦截 DispatcherServlet 之后抛出的异常：Filter、{@link Error}、异步线程里的异常都不经过这里。
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
     * 不记日志的静态资源路径名单。浏览器和爬虫会自动请求这些文件，记 WARN 只会淹没真正的错误。
     */
    private static final String[] IGNORED_RESOURCE_PATHS = {
            // 浏览器标签页图标，所有现代浏览器都会自动请求
            "favicon.ico",
            // iOS Safari 添加到主屏时请求的图标
            "apple-touch-icon.png",
            // 旧版 iOS 的兼容写法
            "apple-touch-icon-precomposed.png"};

    /**
     * 判断资源路径是否属于「不记日志」的名单，比较前去掉前导斜杠。
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
     * 业务异常，透传异常自身的错误码与提示信息。
     *
     * @param e 业务异常
     * @return 失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.<Void>failure(e.getCode(), e.getMessage()).toResponseEntity();
    }

    /**
     * {@code @RequestBody} 校验失败。
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
     * @return 400 响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream().map(this::formatFieldError)
                .collect(Collectors.joining(ERROR_DELIMITER));
        log.warn("参数绑定失败: {}", message);
        return ApiResponse.<Void>failure(ApiResponseConstant.PARAM_INVALID.code(), message).toResponseEntity();
    }

    /**
     * {@code @Validated} + AOP 代理场景（多见于 Service 层入参校验）抛出的约束违反异常。
     *
     * @param e 约束违反异常
     * @return 400 响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(ERROR_DELIMITER));
        log.warn("方法参数校验失败: {}", message);
        return ApiResponse.<Void>failure(ApiResponseConstant.PARAM_INVALID.code(), message).toResponseEntity();
    }

    /**
     * Controller 方法参数上的约束注解校验失败。Spring 6.1 起这类异常不再走
     * {@link ConstraintViolationException}，不单独处理就会被兜底成 500。
     *
     * @param e 方法参数校验异常
     * @return 400 响应
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
     * @return 400 响应
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
     * @param e 消息解析异常
     * @return 400 响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        String message = e.getMessage() != null ? e.getMessage() : ApiResponseConstant.BAD_REQUEST.message();
        return ApiResponse.<Void>failure(ApiResponseConstant.BAD_REQUEST.code(), message).toResponseEntity();
    }

    /**
     * 请求体媒体类型不支持。
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
     * 请求路径不存在。命中忽略名单的（如 favicon）不记日志。
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
     * springdoc 的文档分组不存在。该异常只继承 RuntimeException，不单独处理会被兜底成 500。
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
     * 请求方法不支持。
     *
     * @param e 方法不支持异常
     * @return 405 响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return ApiResponse.<Void>failure(ApiResponseConstant.METHOD_NOT_ALLOWED.code(),
                ApiResponseConstant.METHOD_NOT_ALLOWED.message() + ": " + e.getMethod()).toResponseEntity();
    }

    /**
     * 异步请求处理超时。
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
     * 框架自带状态码的异常（{@link ErrorResponseException} 及 {@link ResponseStatusException}），
     * 按异常状态码返回，4xx 记 WARN、5xx 记 ERROR 并隐藏细节。
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
        // 4xx 属于调用方用法问题，把框架给出的具体原因透出去更有助于联调
        String detail = e.getBody().getDetail();
        String message = detail == null ? resolved.getReasonPhrase() : detail;
        log.warn("框架异常(状态码 {}): {}", resolved.value(), message);
        return ApiResponse.<Void>failure(resolved.value(), message).toResponseEntity();
    }

    /**
     * 兜底：未预期异常，堆栈只进服务端日志，不泄露给前端。
     *
     * @param e 未预期异常
     * @return 500 响应
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
