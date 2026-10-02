package com.zjc.demo.client;

import com.zjc.demo.constant.TraceConstant;
import org.slf4j.MDC;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 出站请求链路透传：把当前线程 MDC 里的 traceId 写进请求头，交给下游服务继续同一条链路。
 *
 * <p>
 * <b>闭环关系：</b>{@code TraceIdFilter} 会从请求头 {@code X-Trace-Id} 读取上游下发的
 * traceId 并沿用。因此本拦截器写出的头，正好是下游服务 {@code TraceIdFilter} 要读的头——
 * 上下游不需要任何额外约定，链路 ID 就串起来了。
 *
 * <p>
 * 适用于 {@code RestClient} 以及构建在它之上的声明式 HTTP 客户端
 * （{@code @HttpExchange} 接口由 {@code HttpServiceProxyFactory} + {@code RestClientAdapter}
 * 生成，最终仍走 {@code RestClient}），因此挂一次即可覆盖全部出站调用。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>非 HTTP 线程（定时任务、MQ 消费）里 MDC 为空，此时<b>不会</b>写入请求头，
 * 下游会生成本地 traceId——这是预期行为，说明链路本来就断了；</li>
 * <li>调用方已显式设置了 {@code X-Trace-Id} 时保持原值不动，便于对接已有的网关链路；</li>
 * <li>{@code @Async} 线程里发出的请求依赖
 * {@code MdcTaskDecorator} 先把 MDC 透传过去，两者是配合关系。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
public class TraceIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    /**
     * 在出站请求上补充 traceId 请求头。
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
