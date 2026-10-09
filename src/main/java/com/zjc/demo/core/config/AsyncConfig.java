package com.zjc.demo.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

import com.zjc.demo.core.async.AsyncTaskMetricsDecorator;
import com.zjc.demo.core.async.MdcTaskDecorator;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 异步执行配置：开启 {@code @Async} 并把链路上下文透传到异步线程。
 *
 * <p>
 * 这里只做了两件事，其余全部交给 Spring Boot 的默认值：
 * <ol>
 * <li>{@code @EnableAsync}：不加这个注解 {@code @Async} 完全不生效，且<b>不报错</b>——
 * 方法会变成同步执行，是最容易漏掉的一步；</li>
 * <li>注册 {@link TaskDecorator} Bean：Boot 的任务执行自动配置会收集容器里的 {@code TaskDecorator}
 * 并自动应用到它创建的执行器上，因此<b>不需要</b>自己定义 {@code @Async} 的执行器 Bean，也不会覆盖 Boot
 * 对虚拟线程等默认配置的适配。</li>
 * </ol>
 *
 * <p>
 * <b>Boot 4 起可以注册多个 {@code TaskDecorator}：</b>Boot 会把它们组装成一个
 * {@code CompositeTaskDecorator}，按 {@code @Order} 的顺序包装任务。
 * 注意「依次包装」的语义——<b>列表里最后一个（{@code @Order} 值最大的）是最外层</b>， 最先执行、最后收尾。本类给指标埋点标了
 * {@code @Order(Ordered.LOWEST_PRECEDENCE)}， 让它成为最外层，测到的就是包含链路透传开销在内的端到端耗时。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>{@code @Async} 方法必须写在<b>另一个 Bean</b> 里。同一个类内部调用会绕过代理，
 * 异步与上下文透传都不生效，且不报错；</li>
 * <li>{@code @Async} 方法抛出的异常不会传播到调用方，需要异步异常处理时实现
 * {@code AsyncUncaughtExceptionHandler}；</li>
 * <li>返回值用 {@code CompletableFuture}，不要用 {@code void}，否则拿不到结果与异常。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * 链路上下文透传装饰器，注册为 Bean 即对所有 Boot 托管的执行器生效。
	 *
	 * @return MDC 透传装饰器
	 */
	@Bean
	@Order(0)
	TaskDecorator mdcTaskDecorator() {
		return new MdcTaskDecorator();
	}

	/**
	 * 异步任务耗时埋点装饰器，与上一个是<b>并列</b>关系，两者会一起生效。
	 *
	 * <p>
	 * 这是 Boot 4 的新能力：注册多个 {@code TaskDecorator} Bean 不再冲突， 由 Boot
	 * 自动组合，业务上可以把「上下文透传」和「指标埋点」拆成两个独立关注点。
	 *
	 * @param meterRegistry 指标注册表，由 actuator 自动配置提供
	 * @return 耗时埋点装饰器
	 */
	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	TaskDecorator asyncTaskMetricsDecorator(MeterRegistry meterRegistry) {
		return new AsyncTaskMetricsDecorator(meterRegistry);
	}
}
