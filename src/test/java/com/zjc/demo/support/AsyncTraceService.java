package com.zjc.demo.support;

import java.util.concurrent.CompletableFuture;

import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.zjc.demo.constant.TraceConstant;

/**
 * 测试专用异步服务，用于验证 {@code @Async} 线程里能否读到请求线程的 traceId。
 *
 * <p>
 * 必须单独成一个 Bean：{@code @Async} 靠代理实现，同一个类内部自调用会绕过代理， 异步与上下文透传都不会生效且不报错。
 *
 * @author jiancai.zhong
 */
@Service
public class AsyncTraceService {

	/**
	 * 返回执行线程上 MDC 里的 traceId。
	 *
	 * <p>
	 * 若 {@code MdcTaskDecorator} 生效，返回值应与提交任务的请求线程 traceId 完全一致。
	 *
	 * @return 异步线程上的 traceId，MDC 无值时返回 {@code null}
	 */
	@Async
	public CompletableFuture<String> currentTraceId() {
		return CompletableFuture.completedFuture(MDC.get(TraceConstant.MDC_KEY));
	}
}
