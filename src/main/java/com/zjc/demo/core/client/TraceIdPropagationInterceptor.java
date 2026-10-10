package com.zjc.demo.core.client;

import com.zjc.demo.common.constant.web.TraceConstant;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import java.io.IOException;

/**
 * 出站请求链路透传：把 MDC 里的 traceId 写进请求头，下游的 {@code TraceIdFilter} 会读同一个头，
 * 链路 ID 由此串起来。挂一次即可覆盖全部 {@code RestClient} 出站调用。
 *
 * @author jiancai.zhong
 */
public class TraceIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    /**
     * 在出站请求上补充 traceId 请求头，已显式设置过的保持原值不动。
     *
     * @param request   当前请求
     * @param body      请求体字节
     * @param execution 执行链
     * @return 下游响应
     * @throws IOException 网络或 IO 异常
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = MDC.get(TraceConstant.MDC_KEY);
        HttpHeaders headers = request.getHeaders();
        if (traceId != null && !headers.containsHeader(TraceConstant.HEADER_NAME)) {
            headers.set(TraceConstant.HEADER_NAME, traceId);
        }
        return execution.execute(request, body);
    }
}
