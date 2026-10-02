package com.zjc.demo.filter;

import com.zjc.demo.constant.TraceConstant;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路追踪 ID 过滤器：为每次请求生成（或沿用上游下发的）traceId，放入 MDC 并写回响应头。
 *
 * <p>
 * <b>单体应用为什么也需要：</b>单个请求的处理日志会散落在 Controller 日志、
 * {@code WebLogAspect} 的请求/返回日志、异常堆栈等多处。没有 traceId 时只能靠时间 + 线程名
 * 人工比对；有了 traceId，一次请求的所有日志都能被一条命令捞出来：
 *
 * <pre>{@code
 * grep '4f3c2b1a7e9d4c2b8f6a1d0e5c3b7a92' logs/info/*.log
 * }</pre>
 *
 * <p>
 * 它不依赖任何分布式追踪组件（SkyWalking / Zipkin 等），后续想接入时把
 * {@link TraceConstant#MDC_KEY} 换成对应框架要求的 key 即可。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>上游可能通过请求头下发 traceId，这里会优先沿用，用于把 Nginx / 网关链路串起来；</li>
 * <li>外部输入必须经过 {@link #sanitize(String)} 清洗，防止携带换行符造成<b>日志伪造</b>
 * （往业务日志里注入伪造行）；</li>
 * <li>{@code OncePerRequestFilter} 无法覆盖异步 Servlet 请求的执行线程，如需在
 * {@code @Async} 或自定义线程池里继续可见，需要额外配置 TaskDecorator 做 MDC 透传。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);

        MDC.put(TraceConstant.MDC_KEY, traceId);
        response.setHeader(TraceConstant.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 虚拟线程 / 线程池复用下必须清理，否则会串到其他请求
            MDC.remove(TraceConstant.MDC_KEY);
        }
    }

    /**
     * 解析本次请求的 traceId：优先沿用上游下发值，缺失时本地生成。
     *
     * @param request 当前请求
     * @return 非空 traceId
     */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = sanitize(request.getHeader(TraceConstant.REQUEST_HEADER_NAME));
        return StringUtils.hasText(incoming) ? incoming : generateTraceId();
    }

    /**
     * 生成本地 traceId：32 位十六进制（去掉 UUID 的短横线），便于 grep 与复制。
     *
     * @return 新生成的 traceId
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 清洗外部传入的 traceId，避免日志伪造。
     *
     * <p>
     * 请求头是外部可控输入，若不限制字符集，攻击者可以塞入 {@code \r\n}
     * 在业务日志里伪造出整整一行看似正常的记录（Log Injection）。
     * 这里只保留 {@code 字母数字 _ . -}，并限制最大长度。
     *
     * @param value 原始请求头值
     * @return 清洗后的值，非法输入返回 {@code null}
     */
    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > TraceConstant.MAX_LENGTH) {
            trimmed = trimmed.substring(0, TraceConstant.MAX_LENGTH);
        }
        if (!trimmed.matches("[A-Za-z0-9_.-]+")) {
            log.warn("检测到非法的 {} 请求头，已忽略并使用本地生成的 traceId", TraceConstant.REQUEST_HEADER_NAME);
            return null;
        }
        return trimmed;
    }
}
