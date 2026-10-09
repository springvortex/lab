package com.zjc.demo.common.constant;

/**
 * 链路追踪相关常量。
 *
 * <p>
 * 单体应用同样适用 traceId：这里的 traceId 就是「一次请求的关联 ID」，用来把同一个请求 在不同位置（Controller
 * 日志、切面日志、异常堆栈、响应体）的输出串起来。 它不涉及微服务跨服务传递，因此不需要 W3C {@code traceparent}
 * 那套协议，本地生成即可。
 *
 * @author jiancai.zhong
 */
public final class TraceConstant {

	/**
	 * 放在 SLF4J {@code MDC} 里的 key，日志配置通过 {@code %X{traceId}} 取值。
	 */
	public static final String MDC_KEY = "traceId";

	/**
	 * 响应头名称，便于前端与网关拿到并回传，做端到端追踪。
	 *
	 * <p>
	 * 跨域场景下还需在 {@code WebConfig#addCorsMappings} 的 {@code exposedHeaders} 中放行，
	 * 否则浏览器会屏蔽该响应头。
	 */
	public static final String HEADER_NAME = "X-Trace-Id";

	/**
	 * 上游（Nginx / 网关）下发 traceId 时使用的请求头名称；存在时优先沿用，以便把链路串起来。
	 */
	public static final String REQUEST_HEADER_NAME = "X-Trace-Id";

	/**
	 * 上游传入 traceId 的最大保留长度，超出部分截断，防止超长 Header 污染日志。
	 */
	public static final int MAX_LENGTH = 64;

	/**
	 * 工具类禁止实例化。
	 *
	 * <p>
	 * 访问修饰符 {@code private} 只能拦住正常代码，反射仍可绕过，因此这里显式抛异常， 让「禁止实例化」成为运行时保证而不是一句注释。
	 */
	private TraceConstant() {
		throw new UnsupportedOperationException("常量类禁止实例化");
	}
}
