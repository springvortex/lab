package com.zjc.demo.async;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 把 {@link MDC} 上下文透传到异步线程的任务装饰器。
 *
 * <p>
 * <b>解决什么问题：</b>{@code TraceIdFilter} 只在请求线程上写入 traceId，而
 * {@code @Async}、线程池任务、{@code CompletableFuture.runAsync} 跑在别的线程上。 这些线程里的日志拿不到
 * traceId，日志里就会出现大量 {@code [-]}（缺省值）， 一次异步任务的日志无法与触发它的请求关联起来。
 *
 * <p>
 * <b>为什么注册成 Bean 就够了：</b>Spring Boot 的任务执行自动配置会收集容器里的 {@link TaskDecorator}
 * Bean，并自动应用到它创建的执行器上 （{@code SimpleAsyncTaskExecutor} 与
 * {@code ThreadPoolTaskExecutor} 都会）。 因此不需要自己定义 {@code @Async} 的执行器
 * Bean——只声明本类的 Bean 即可全局生效， 也不会覆盖 Boot 对虚拟线程等默认配置的适配。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>任务结束时 {@code finally} 里调用 {@code MDC.clear()}。虚拟线程与线程池都会复用线程， 不清理会让上一个任务的
 * traceId 串到下一个任务；</li>
 * <li>装饰发生在<b>提交任务时</b>，因此只能捕获提交那一刻的 MDC 快照， 提交之后主线程对 MDC 的修改不会同步过去；</li>
 * <li>本装饰器传输的是整个 MDC 上下文，不只是 traceId， 后续往 MDC 里放的其他键（如用户 ID）会一并透传；</li>
 * <li>它解决不了跨服务链路——跨服务需要由 {@code TraceIdPropagationInterceptor} 把 traceId
 * 写进出站请求头。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
public class MdcTaskDecorator implements TaskDecorator {

	/**
	 * 用提交时刻的 MDC 快照包装任务，保证任务在执行线程上能看到同样的上下文。
	 *
	 * @param runnable 原始任务
	 * @return 包装后的任务，执行前后自动设置与清理 MDC
	 */
	@Override
	public Runnable decorate(Runnable runnable) {
		Map<String, String> contextMap = MDC.getCopyOfContextMap();
		return () -> {
			if (contextMap != null) {
				MDC.setContextMap(contextMap);
			}
			try {
				runnable.run();
			} finally {
				// 线程会被复用，不清理会串到下一个任务
				MDC.clear();
			}
		};
	}
}
