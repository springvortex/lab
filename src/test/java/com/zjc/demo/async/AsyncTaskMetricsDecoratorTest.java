package com.zjc.demo.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.zjc.demo.constant.TraceConstant;
import com.zjc.demo.support.AsyncTraceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link AsyncTaskMetricsDecorator} 与「多个 {@code TaskDecorator} 组合生效」的测试。
 *
 * <p>
 * 分两层：
 * <ol>
 * <li><b>单元层</b>：直接 {@code new} 出装饰器、自己传入注册表，验证「记录耗时」「任务抛异常也记录」
 * 两个分支，确定性最好；</li>
 * <li><b>集成层</b>：验证容器里两个装饰器（{@code MdcTaskDecorator} +
 * {@code AsyncTaskMetricsDecorator}）同时生效。</li>
 * </ol>
 *
 * <p>
 * <b>集成层为什么需要「有界等待」而不是直接断言：</b>任务装饰器包在提交给执行器的任务<b>最外层</b>，
 * 而 {@code CompletableFuture} 是在内层任务里完成的。因此
 * {@code future.get()} 返回时，外层 {@code finally} 里那句「记录耗时」很可能还没执行——
 * 直接断言会偶发失败（本项目实际遇到过，是真实的时序陷阱，不是测试写错）。
 * 生产代码不受影响（埋点晚几微秒无所谓），但测试必须尊重它。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
class AsyncTaskMetricsDecoratorTest {

    /**
     * 测试专用异步服务（必须独立成 Bean，{@code @Async} 靠代理生效）。
     */
    @Autowired
    private AsyncTraceService asyncTraceService;

    /**
     * 指标注册表，用于读取异步任务耗时指标。
     */
    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * 装饰器单元行为：直接构造，不依赖上下文。
     */
    @Nested
    @DisplayName("装饰器单元行为")
    class DecoratorUnitBehavior {

        /**
         * 任务正常结束时记录一次耗时。
         */
        @Test
        @DisplayName("正常任务：记录一次耗时")
        void recordsDurationForNormalTask() {
            MeterRegistry registry = new SimpleMeterRegistry();
            AsyncTaskMetricsDecorator decorator = new AsyncTaskMetricsDecorator(registry);

            decorator.decorate(() -> {
                // 什么都不做，只验证埋点被执行
            }).run();

            assertThat(registry.get(AsyncTaskMetricsDecorator.TIMER_NAME).timer().count()).isEqualTo(1);
        }

        /**
         * 任务抛异常时也要记录耗时，且异常必须原样抛出（埋点不能吞掉业务异常）。
         */
        @Test
        @DisplayName("异常任务：仍记录耗时且不吞异常")
        void recordsDurationAndRethrowsOnFailure() {
            MeterRegistry registry = new SimpleMeterRegistry();
            AsyncTaskMetricsDecorator decorator = new AsyncTaskMetricsDecorator(registry);

            Runnable failing = decorator.decorate(() -> {
                throw new IllegalStateException("业务任务失败");
            });

            assertThatThrownBy(failing::run).isInstanceOf(IllegalStateException.class);
            assertThat(registry.get(AsyncTaskMetricsDecorator.TIMER_NAME).timer().count()).isEqualTo(1);
        }
    }

    /**
     * 集成层：两个装饰器在容器里共存并同时生效。
     */
    @Nested
    @DisplayName("组合装饰器（容器内真实生效）")
    class CompositeInContext {

        /**
         * MDC 透传与指标埋点必须同时生效。
         *
         * @throws Exception 异步任务执行失败
         */
        @Test
        @DisplayName("MDC 透传与指标埋点同时生效")
        void bothDecoratorsTakeEffect() throws Exception {
            MDC.put(TraceConstant.MDC_KEY, "0f1e2d3c4b5a69788796a5b4c3d2e1f0");
            double countBefore = asyncTaskCount();
            String traceIdInAsyncThread;
            try {
                traceIdInAsyncThread = asyncTraceService.currentTraceId().get(5, TimeUnit.SECONDS);
            } finally {
                MDC.clear();
            }

            // 装饰器一：链路透传
            assertThat(traceIdInAsyncThread).isEqualTo("0f1e2d3c4b5a69788796a5b4c3d2e1f0");
            // 装饰器二：指标埋点（有界等待，原因见类注释）
            assertThat(waitForAsyncTaskCountAbove(countBefore)).isGreaterThan(countBefore);
        }
    }

    /**
     * 在 2 秒内等待异步任务指标超过给定值。
     *
     * @param threshold 期望超过的次数
     * @return 观察到的最新值，超时则返回最后一次读到的值
     * @throws InterruptedException 等待被中断
     */
    private double waitForAsyncTaskCountAbove(double threshold) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        double current = asyncTaskCount();
        while (current <= threshold && System.nanoTime() < deadline) {
            Thread.sleep(10);
            current = asyncTaskCount();
        }
        return current;
    }

    /**
     * 读取异步任务耗时指标的执行次数。
     *
     * @return 已记录的异步任务次数，指标尚未注册时返回 0
     */
    private double asyncTaskCount() {
        Timer timer = meterRegistry.find(AsyncTaskMetricsDecorator.TIMER_NAME).timer();
        return timer == null ? 0 : timer.count();
    }
}
