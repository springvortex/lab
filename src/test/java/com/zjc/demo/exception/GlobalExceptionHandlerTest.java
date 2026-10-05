package com.zjc.demo.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.support.TestRequest;
import com.zjc.demo.web.ApiResponse;

/**
 * {@link GlobalExceptionHandler} 的单元测试。
 *
 * <p>
 * <b>与集成测试的分工：</b>本类只覆盖「真实请求难以稳定构造」的分支——静态资源路径为
 * {@code null}、请求体异常的 message 为 {@code null}、{@code @Validated} + 方法参数校验、
 * 表单绑定失败、异步超时。其余能由真实 HTTP 触发的路径全部放在
 * {@code ApiIntegrationTest}，避免「直接调用处理器」这种绕过 Spring 路由的假验证。
 *
 * @author jiancai.zhong
 */
class GlobalExceptionHandlerTest {

    /**
     * 被测处理器，无成员状态，可直接 new。
     */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * 校验 {@code handle*} 方法返回的状态码与响应体 code 一致。
     *
     * @param response 处理器返回值
     * @param expected 期望的 HTTP 状态码
     */
    private static void assertStatus(ResponseEntity<ApiResponse<Void>> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(expected.value());
    }

    /**
     * 请求体解析失败时，异常 message 为 {@code null} 的兜底分支。
     */
    @Test
    @DisplayName("请求体解析失败：message 为 null 时回退到默认文案")
    void notReadableWithNullMessageFallsBackToDefault() {
        // 强转为 Throwable：Jackson 2 / Spring 6 下存在 (String, Throwable) 与
        // (String, HttpInputMessage) 两个重载，直接传 null 会产生歧义
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(null, (Throwable) null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotReadable(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(ApiResponseConstant.BAD_REQUEST.message());
    }

    /**
     * 方法参数校验失败（{@code @Validated} + 参数约束）的聚合提示。
     */
    @Test
    @DisplayName("方法参数校验失败：聚合所有约束违反项")
    void constraintViolationIsAggregated() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        TestRequest invalid = new TestRequest();
        invalid.setName(" ");
        invalid.setAge(0);
        Set<ConstraintViolation<TestRequest>> violations = validator.validate(invalid);
        assertThat(violations).hasSize(2);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleConstraintViolation(new ConstraintViolationException(violations));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .contains("name: 名称不能为空")
                .contains("age: 年龄必须大于 0")
                .contains("; ");
    }

    /**
     * 表单绑定失败的聚合提示（与请求体校验走同一套格式化逻辑）。
     */
    @Test
    @DisplayName("表单绑定失败：聚合所有字段错误")
    void bindExceptionIsAggregated() {
        BindException e = new BindException(new Object(), "testRequest");
        e.addError(new FieldError("testRequest", "name", "名称不能为空"));
        e.addError(new FieldError("testRequest", "age", "年龄必须大于 0"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("name: 名称不能为空; age: 年龄必须大于 0");
    }

    /**
     * 异步请求超时必须返回 408，而不是被兜底成 500。
     */
    @Test
    @DisplayName("异步超时：返回 408")
    void asyncTimeoutReturns408() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAsyncTimeout(new AsyncRequestTimeoutException());

        assertStatus(response, HttpStatus.REQUEST_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(ApiResponseConstant.REQUEST_TIMEOUT.message());
    }

    /**
     * 客户端要求的响应类型无法产出时返回 406。
     */
    @Test
    @DisplayName("Accept 不匹配：返回 406")
    void mediaTypeNotAcceptableReturns406() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMediaTypeNotAcceptable(new HttpMediaTypeNotAcceptableException("no match"));

        assertStatus(response, HttpStatus.NOT_ACCEPTABLE);
    }

    /**
     * 请求体媒体类型不支持时返回 415，且不把原始 Content-Type 原样回显。
     */
    @Test
    @DisplayName("Content-Type 不支持：返回 415")
    void mediaTypeNotSupportedReturns415() {
        HttpMediaTypeNotSupportedException e =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.<MediaType>of());

        ResponseEntity<ApiResponse<Void>> response = handler.handleMediaTypeNotSupported(e);

        assertStatus(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(ApiResponseConstant.UNSUPPORTED_MEDIA_TYPE.message());
    }

    /**
     * 非法业务码必须兜底成 500：响应体保留业务传的原值，但 HTTP 状态码不能被带偏。
     */
    @Test
    @DisplayName("业务异常：非法状态码兜底为 500")
    void illegalBusinessCodeFallsBackTo500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException(10001, "非 HTTP 语义编码"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(10001);
    }

    /**
     * 构造 Boot 2 下的「找不到 Handler」异常，统一收口构造签名差异。
     *
     * <p>
     * Spring 5 用 {@link NoHandlerFoundException}，Spring 6 起换成
     * {@code org.springframework.web.servlet.resource.NoResourceFoundException}，
     * 且构造器签名也不同（前者是 {@code (httpMethod, requestURL, headers)}，
     * 后者是 {@code (HttpMethod, resourcePath)}）。收口到这里后，
     * 后续再跨版本升级只需改这一个方法。
     *
     * @param requestUrl 请求路径，可为 {@code null}
     * @return 用于测试的异常实例
     */
    private static NoHandlerFoundException noHandlerFound(String requestUrl) {
        return new NoHandlerFoundException("GET", requestUrl, new HttpHeaders());
    }

    /**
     * 静态资源 404 的忽略名单与日志开关。
     */
    @Nested
    @DisplayName("静态资源 404")
    class NotFound {

        /**
         * 命中忽略名单的路径不记日志，但状态码仍是 404（只影响日志噪声，不影响响应）。
         */
        @Test
        @DisplayName("忽略名单：favicon 与 apple-touch-icon 均返回 404")
        void ignoredResourcesStillReturn404() {
            for (String path : new String[]{
                    "favicon.ico", "apple-touch-icon.png", "apple-touch-icon-precomposed.png"
            }) {
                ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandlerFound(noHandlerFound(path));
                assertThat(response.getStatusCode()).as("路径 %s", path).isEqualTo(HttpStatus.NOT_FOUND);
            }
        }

        /**
         * 带前导斜杠的写法同样命中忽略名单（归一化逻辑生效）。
         */
        @Test
        @DisplayName("忽略名单：前导斜杠会被归一化")
        void leadingSlashIsNormalized() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandlerFound(noHandlerFound("/favicon.ico"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        /**
         * 非忽略路径照常记录 WARN，便于排查错误链接。
         */
        @Test
        @DisplayName("非忽略路径：照常告警并返回 404")
        void otherPathsAreLogged() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandlerFound(noHandlerFound("nope"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        /**
         * 资源路径为 {@code null} 时不能抛 NPE，必须安全地按「非忽略」处理。
         */
        @Test
        @DisplayName("资源路径为 null：不抛 NPE")
        void nullResourcePathIsSafe() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandlerFound(noHandlerFound(null));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 框架自带状态码的异常（{@link ResponseStatusException}）必须按自身状态码透出，
     * 而不是被兜底改写成 500。
     *
     * <p>
     * Spring 5（Boot 2）里 {@code ResponseStatusException} 直接继承 {@code NestedRuntimeException}，
     * 不像 Spring 6 那样还有 {@code ErrorResponseException} 父类，所以它本身就是最顶层的
     * 「带状态码的框架异常」。端到端行为（真实 HTTP 请求是否命中本处理器）
     * 由 {@code ApiIntegrationTest} 中访问 {@code /test/status-*} 的用例验证。
     */
    @Nested
    @DisplayName("框架异常：状态码如实透出")
    class ResponseStatus {

        /**
         * 4xx 且带原因：状态码与原因都透给调用方，帮助联调。
         */
        @Test
        @DisplayName("4xx 带原因：状态码与原因都透出")
        void clientErrorKeepsStatusAndDetail() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(
                    new ResponseStatusException(HttpStatus.CONFLICT, "订单状态冲突"));

            assertStatus(response, HttpStatus.CONFLICT);
            assertThat(response.getBody().getMessage()).isEqualTo("订单状态冲突");
        }

        /**
         * 4xx 但没带原因：消息回退到状态码短语，不能是 null 或空串。
         */
        @Test
        @DisplayName("4xx 无原因：消息回退到状态码短语")
        void clientErrorWithoutDetailFallsBackToReasonPhrase() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND));

            assertStatus(response, HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getMessage()).isEqualTo("Not Found");
        }

        /**
         * 5xx：状态码如实透出，但内部细节必须隐藏，只给通用文案。
         */
        @Test
        @DisplayName("5xx：隐藏内部细节")
        void serverErrorHidesDetails() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(
                    new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "模拟内部细节，不应出现"));

            assertStatus(response, HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getMessage()).isEqualTo(ApiResponseConstant.INTERNAL_ERROR.message());
        }

        /**
         * 非标准状态码（{@code HttpStatus.resolve} 返回 null）时兜底为 500，不能抛异常。
         */
        @Test
        @DisplayName("非标准状态码：兜底为 500 且不抛异常")
        @SuppressWarnings("deprecation")
        void unresolvableStatusFallsBackTo500() {
            // Spring 5 独有的 (int, String, Throwable) 构造器在 5.3 已标记 @Deprecated，
            // 但它是唯一能把 HttpStatus 枚举之外的状态码塞进异常的方式，此处刻意保留。
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleResponseStatus(new ResponseStatusException(599, null, null));

            assertStatus(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
