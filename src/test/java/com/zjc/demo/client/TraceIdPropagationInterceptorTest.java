package com.zjc.demo.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import com.zjc.demo.constant.TraceConstant;

/**
 * {@link TraceIdPropagationInterceptor} 的单元测试。
 *
 * <p>
 * 四条分支都要覆盖：MDC 有值且下游未设头（补上）、MDC 有值但调用方已设头（保留原值）、
 * MDC 为空（不写头）、以及响应必须原样返回不被替换。
 *
 * @author jiancai.zhong
 */
class TraceIdPropagationInterceptorTest {

    /**
     * 被测拦截器。
     */
    private final TraceIdPropagationInterceptor interceptor = new TraceIdPropagationInterceptor();

    /**
     * 构造一个模拟出站请求。
     *
     * @return 模拟请求
     */
    private static MockClientHttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.GET, URI.create("http://downstream-service/api/users"));
    }

    /**
     * 清理 MDC，避免用例之间互相影响。
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * MDC 有 traceId 且请求未带该头时，必须补上——这是链路闭环的关键一步。
     *
     * @throws Exception 拦截器抛出的异常
     */
    @Test
    @DisplayName("MDC 有值：补上 X-Trace-Id 请求头")
    void addsTraceIdHeader() throws Exception {
        MDC.put(TraceConstant.MDC_KEY, "4f3c2b1a7e9d4c2b8f6a1d0e5c3b7a92");
        MockClientHttpRequest request = request();

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(TraceConstant.HEADER_NAME))
                .isEqualTo("4f3c2b1a7e9d4c2b8f6a1d0e5c3b7a92");
    }

    /**
     * 非 HTTP 线程（定时任务、MQ 消费）里 MDC 为空，此时不应写入请求头。
     *
     * @throws Exception 拦截器抛出的异常
     */
    @Test
    @DisplayName("MDC 为空：不写请求头")
    void skipsWhenMdcIsEmpty() throws Exception {
        MDC.clear();
        MockClientHttpRequest request = request();

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(TraceConstant.HEADER_NAME)).isNull();
    }

    /**
     * 调用方已显式指定 traceId 时保持原值，便于对接已有的网关链路。
     *
     * @throws Exception 拦截器抛出的异常
     */
    @Test
    @DisplayName("调用方已设头：保留原值不覆盖")
    void keepsExistingHeader() throws Exception {
        MDC.put(TraceConstant.MDC_KEY, "local-generated");
        MockClientHttpRequest request = request();
        request.getHeaders().set(TraceConstant.HEADER_NAME, "from-gateway");

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(TraceConstant.HEADER_NAME)).isEqualTo("from-gateway");
    }

    /**
     * 拦截器只负责加头，响应必须原样返回，不能被包装或替换。
     *
     * @throws Exception 拦截器抛出的异常
     */
    @Test
    @DisplayName("响应原样返回")
    void returnsExecutionResponse() throws Exception {
        MDC.put(TraceConstant.MDC_KEY, "abc");
        MockClientHttpResponse expected = new MockClientHttpResponse(new byte[0], HttpStatus.NOT_FOUND);

        ClientHttpResponse actual = interceptor.intercept(request(), new byte[0], (req, body) -> expected);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getStatusCode().value()).isEqualTo(404);
    }
}
