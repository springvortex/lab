package com.zjc.demo.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * {@link AsyncTaskMetricsDecorator} 的单元测试。
 *
 * <p>
 * <b>这里要钉死的是一条容易被忽视的约定：埋点不能改变任务本身的行为。</b> 装饰器运行在每个异步任务的外层，
 * 一旦它吞掉异常或漏记耗时，问题会以「异步任务悄悄失败」「指标对不上」的形式出现， 且因为异步不影响接口响应，很难被第一时间发现。
 *
 * @author jiancai.zhong
 */
class AsyncTaskMetricsDecoratorTest {

	/**
	 * 被测装饰器，用内存版注册表避免依赖真实监控后端。
	 */
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	/**
	 * 被测装饰器实例。
	 */
	private final AsyncTaskMetricsDecorator decorator = new AsyncTaskMetricsDecorator(registry);

	/**
	 * 任务正常执行后应记录一次耗时。
	 */
	@Test
	@DisplayName("任务执行后记录一次耗时")
	void recordsTimerAfterTask() {
		decorator.decorate(() -> {
		}).run();

		assertThat(registry.get(AsyncTaskMetricsDecorator.TIMER_NAME).timer().count()).isEqualTo(1);
	}

	/**
	 * 任务确实被原样执行（装饰器不能把任务「吞掉」）。
	 */
	@Test
	@DisplayName("原任务被真正执行")
	void runsOriginalTask() {
		AtomicBoolean executed = new AtomicBoolean();

		decorator.decorate(() -> executed.set(true)).run();

		assertThat(executed).isTrue();
	}

	/**
	 * 任务抛异常时仍要记录耗时，且异常原样向上抛。
	 *
	 * <p>
	 * 用 {@code try-finally} 而非 {@code try-catch} 的效果就在这里：异常路径同样记账，
	 * 但异常不被吞掉——吞掉会让异步任务「明明失败了却显示成功」，是最危险的埋点写法。
	 */
	@Test
	@DisplayName("任务抛异常：记录耗时且异常不丢失")
	void recordsTimerEvenWhenTaskThrows() {
		Runnable task = decorator.decorate(() -> {
			throw new IllegalStateException("模拟异步任务失败");
		});

		assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class).hasMessage("模拟异步任务失败");
		assertThat(registry.get(AsyncTaskMetricsDecorator.TIMER_NAME).timer().count()).isEqualTo(1);
	}

	/**
	 * 每次执行累加计时次数，而不是只记最后一次。
	 */
	@Test
	@DisplayName("多次执行累加计时次数")
	void accumulatesAcrossRuns() {
		Runnable task = decorator.decorate(() -> {
		});

		task.run();
		task.run();
		task.run();

		assertThat(registry.get(AsyncTaskMetricsDecorator.TIMER_NAME).timer().count()).isEqualTo(3);
	}

	/**
	 * 指标名是外部约定的查询入口，不能被随意改动。
	 */
	@Test
	@DisplayName("指标名符合约定")
	void timerNameMatchesConvention() {
		assertThat(AsyncTaskMetricsDecorator.TIMER_NAME).isEqualTo("app.async.task");
	}
}
