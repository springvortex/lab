package com.zjc.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

import com.zjc.demo.async.AsyncTaskMetricsDecorator;
import com.zjc.demo.async.MdcTaskDecorator;

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
 * 并自动应用到它创建的执行器上，因此<b>不需要</b>自己定义 {@code @Async} 的执行器 Bean，也不会丢失 Boot
 * 对线程池参数的默认配置与运维治理。</li>
 * </ol>
 *
 * <p>
 * <b>为什么必须自己组装（Boot 2 / Boot 3 共同的硬限制）：</b>本模板有两个装饰器诉求——「链路上下文透传」
 * （{@link MdcTaskDecorator}）与「异步任务耗时埋点」（{@link AsyncTaskMetricsDecorator}）。
 * Boot 的任务执行自动配置是用 {@code ObjectProvider<TaskDecorator>.getIfUnique()} 取装饰器的，
 * 意味着容器里<b>只能有一个</b> {@code TaskDecorator} 类型 Bean：注册两个时它拿不到唯一实例，
 * 会返回 {@code null} 并<b>静默丢弃全部装饰器</b>（既不报启动错，也不打日志——MDC 透传会悄悄失效）。
 * 因此这里手动把两者组合成<b>一个</b> Bean。
 *
 * <p>
 * <b>为什么用 {@code Runnable} 嵌套而不是框架的 {@code CompositeTaskDecorator}：</b>
 * 该类是 Spring 6.1 才加入的，Spring 5.3（Boot 2.7）没有。这里用一行 lambda 等价实现：
 * {@code outer.decorate(inner.decorate(task))} 即「内层的被外层包住」，语义完全一致。
 *
 * <p>
 * <b>顺序语义（容易踩）：</b>嵌套写法是自内向外层层包裹的，
 * 因此<b>最外层</b>最先执行、最后收尾。这里把耗时埋点放在最外层，测到的就是包含链路透传开销在内的
 * 端到端耗时；反过来放会少算一段。
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
	 * 唯一的 {@code TaskDecorator} Bean：把「链路上下文透传」与「异步任务耗时埋点」组合起来。
	 *
	 * <p>
	 * 两个装饰器在<b>代码层面仍是两个独立类</b>（各自的关注点互不干扰），只是在装配时合成一个 Bean，
	 * 这是 Boot 2 / Boot 3 下同时启用两者的唯一方式，详见类注释里的「为什么必须自己组装」。
	 *
	 * <p>
	 * 嵌套顺序决定包装顺序：{@link MdcTaskDecorator} 在内层先执行，
	 * {@link AsyncTaskMetricsDecorator} 在最外层，因此埋点统计的是端到端耗时。
	 *
	 * @param meterRegistry 指标注册表，由 actuator 自动配置提供
	 * @return 组合后的任务装饰器
	 */
	@Bean
	TaskDecorator taskDecorator(MeterRegistry meterRegistry) {
		TaskDecorator mdc = new MdcTaskDecorator();
		TaskDecorator metrics = new AsyncTaskMetricsDecorator(meterRegistry);
		return task -> metrics.decorate(mdc.decorate(task));
	}
}
