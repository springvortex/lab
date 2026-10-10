package com.zjc.demo.core.filter;

import com.zjc.demo.common.constant.web.TraceConstant;
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
 * 链路追踪 ID 过滤器：为每次请求生成或沿用上游下发的 traceId，写入 MDC 并回写响应头。
 *
 * <p>
 * 不依赖任何分布式追踪组件，接入 SkyWalking / Zipkin 时把 {@link TraceConstant#MDC_KEY} 换成对应 key 即可。
 *
 * @author jiancai.zhong
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
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
     * 解析 traceId：优先沿用上游下发值，缺失时本地生成。
     *
     * @param request 当前请求
     * @return 非空 traceId
     */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = sanitize(request.getHeader(TraceConstant.REQUEST_HEADER_NAME));
        return StringUtils.hasText(incoming) ? incoming : generateTraceId();
    }

    /**
     * 生成本地 traceId：32 位十六进制，便于 grep。
     *
     * @return 新生成的 traceId
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 清洗上游传入的 traceId。请求头是外部可控输入，不限制字符集的话可以塞 {@code \r\n} 伪造日志行。
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
