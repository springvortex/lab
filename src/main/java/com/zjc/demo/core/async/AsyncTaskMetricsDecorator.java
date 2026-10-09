package com.zjc.demo.core.async;

import java.util.concurrent.TimeUnit;

import org.springframework.core.task.TaskDecorator;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/**
 * 给异步任务打耗时埋点的任务装饰器，用来演示「多个 {@link TaskDecorator} 可以共存」 （Spring Boot 4.0 新能力）。
 *
 * <p>
 * <b>Boot 4 之前：</b>容器里出现两个 {@code TaskDecorator} Bean 会直接报错
 * （{@code NoUniqueBeanDefinitionException}），只能自己写一个「组合装饰器」把两个拼起来， 或者干脆放弃其中一个。
 *
 * <p>
 * <b>Boot 4 起：</b>自动配置会收集容器里<b>所有</b> {@code TaskDecorator} Bean
 * （{@code TaskExecutorConfigurations} 用 {@code ObjectProvider.orderedStream()}
 * 取全部， 只有一个就直接用、多个就组装成
 * {@code org.springframework.core.task.support.CompositeTaskDecorator}）， 按
 * {@code @Order} 指定的顺序依次包装任务。于是「链路透传」和「指标埋点」可以各写各的， 互不干扰，也不用修改对方。
 *
 * <p>
 * <b>顺序为什么重要（细节，容易踩）：</b>{@code CompositeTaskDecorator} 是
 * <i>依次包装</i>的——按顺序遍历装饰器列表，每个装饰器包住上一个的结果。 因此<b>列表里最后一个装饰器是最外层</b>，它最先执行、最后收尾。
 * 顺序在两种情况下会出问题：
 * <ul>
 * <li>两个装饰器都往 MDC 写键：外层若是「用快照整体覆盖 MDC」的装饰器， 内层刚写进去的键会被覆盖掉；</li>
 * <li>耗时埋点如果被放在最内层，测到的是「去掉外层开销之后」的时间。 埋点通常应放最外层，测到端到端耗时。</li>
 * </ul>
 * 本装饰器只读时间、不碰 MDC，因此与 {@link MdcTaskDecorator} 不存在冲突； 在 {@code AsyncConfig}
 * 里给它标了更高的 {@code @Order} 让它成为最外层。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>埋点失败不应该让业务任务失败，因此这里只记录时间、不抛异常；</li>
 * <li>耗时用 {@code System.nanoTime()} 计算（单调时钟，不受系统时间调整影响）， 不要用
 * {@code currentTimeMillis()}。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@RequiredArgsConstructor
public class AsyncTaskMetricsDecorator implements TaskDecorator {

	/**
	 * 异步任务耗时指标名。
	 *
	 * <p>
	 * 接上 Prometheus 后可直接查 {@code app_async_task_seconds}，
	 * 用来发现「异步任务悄悄变慢」这类问题——异步任务不影响接口响应时间， 不埋点的话变慢很难被发现。
	 */
	public static final String TIMER_NAME = "app.async.task";

	/**
	 * 指标注册表，由 Spring Boot 自动配置提供（项目引入了 actuator 就有）。
	 *
	 * <p>
	 * 构造器由 {@code @RequiredArgsConstructor} 生成：仅含 {@code final} 且未初始化的字段， 因此
	 * {@code TIMER_NAME}（有初始值的 static final）不会被纳入。
	 */
	private final MeterRegistry meterRegistry;

	/**
	 * 用计时逻辑包装任务。
	 *
	 * @param runnable 原始任务
	 * @return 包装后的任务，执行结束后记录一次耗时
	 */
	@Override
	public Runnable decorate(Runnable runnable) {
		return () -> {
			long start = System.nanoTime();
			try {
				runnable.run();
			} finally {
				meterRegistry.timer(TIMER_NAME).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
			}
		};
	}
}
