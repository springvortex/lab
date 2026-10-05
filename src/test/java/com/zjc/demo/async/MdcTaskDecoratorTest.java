package com.zjc.demo.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * {@link MdcTaskDecorator} 的单元测试。
 *
 * <p>
 * 关键点在于「跑到<b>另一个线程</b>上验证」：如果在当前线程直接执行装饰后的任务， 拿到的还是同一个
 * MDC，等于什么都没验证。因此这里一律新建线程执行并 join 回主线程。
 *
 * @author jiancai.zhong
 */
class MdcTaskDecoratorTest {

	/**
	 * 被测装饰器。
	 */
	private final MdcTaskDecorator decorator = new MdcTaskDecorator();

	/**
	 * 在独立线程上执行任务并等待结束，用于真正验证跨线程透传。
	 *
	 * @param task 待执行任务
	 * @throws InterruptedException 线程等待被中断
	 */
	private static void runInAnotherThread(Runnable task) throws InterruptedException {
		Thread thread = new Thread(task);
		thread.start();
		thread.join(5000);
	}

	/**
	 * 清理 MDC，避免用例之间互相影响。
	 */
	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	/**
	 * 整个 MDC 上下文（不止 traceId）都应被透传。
	 *
	 * @throws InterruptedException 线程等待被中断
	 */
	@Test
	@DisplayName("异步线程能看到提交时的完整 MDC 上下文")
	void propagatesWholeContextMap() throws InterruptedException {
		MDC.put("traceId", "abc-123");
		MDC.put("userId", "u-001");
		Map<String, String> captured = new ConcurrentHashMap<>();

		runInAnotherThread(decorator.decorate(() -> captured.putAll(MDC.getCopyOfContextMap())));

		assertThat(captured).containsEntry("traceId", "abc-123").containsEntry("userId", "u-001");
		assertThat(MDC.get("traceId")).as("主线程上下文不应被任务修改").isEqualTo("abc-123");
	}

	/**
	 * MDC 为空时装饰器不能抛 NPE，任务必须照常执行。
	 *
	 * @throws InterruptedException 线程等待被中断
	 */
	@Test
	@DisplayName("MDC 为空时任务照常执行且不抛异常")
	void worksWithEmptyContext() throws InterruptedException {
		MDC.clear();
		AtomicBoolean executed = new AtomicBoolean();

		runInAnotherThread(decorator.decorate(() -> executed.set(true)));

		assertThat(executed).isTrue();
	}

	/**
	 * 任务结束后必须清理 MDC：虚拟线程与线程池都会复用线程，残留会串到下一个任务。
	 */
	@Test
	@DisplayName("任务结束后清理 MDC")
	void clearsMdcAfterTask() {
		MDC.put("traceId", "abc-123");

		decorator.decorate(() -> {
		}).run();

		assertThat(MDC.get("traceId")).isNull();
	}

	/**
	 * 任务抛异常时也要清理 MDC，且异常原样向上抛、不被吞掉。
	 */
	@Test
	@DisplayName("任务抛异常：清理 MDC 且异常不丢失")
	void clearsMdcEvenWhenTaskThrows() {
		MDC.put("traceId", "abc-123");
		Runnable task = decorator.decorate(() -> {
			throw new IllegalStateException("模拟异步任务失败");
		});

		assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class).hasMessage("模拟异步任务失败");
		assertThat(MDC.get("traceId")).isNull();
	}
}
