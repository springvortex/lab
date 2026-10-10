package com.zjc.demo.common.constant.web;

/**
 * 链路追踪相关常量。
 *
 * @author jiancai.zhong
 */
public final class TraceConstant {

    /**
     * SLF4J {@code MDC} 的 key，日志用 {@code %X{traceId}} 取值。
     */
    public static final String MDC_KEY = "traceId";

    /**
     * 响应头名称。跨域时还需在 {@code WebConfig#addCorsMappings} 的 {@code exposedHeaders} 中放行。
     */
    public static final String HEADER_NAME = "X-Trace-Id";

    /**
     * 上游（Nginx / 网关）下发 traceId 用的请求头，存在时优先沿用。
     */
    public static final String REQUEST_HEADER_NAME = "X-Trace-Id";

    /**
     * 上游传入 traceId 的最大保留长度，超出截断，防止超长 Header 污染日志。
     */
    public static final int MAX_LENGTH = 64;

    /**
     * 工具类禁止实例化。
     */
    private TraceConstant() {
        throw new UnsupportedOperationException("常量类禁止实例化");
    }
}
