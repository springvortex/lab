package com.zjc.demo.core.async;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.TimeUnit;
import org.springframework.core.task.TaskDecorator;

/**
 * 给异步任务打耗时埋点。与 {@code MdcTaskDecorator} 并列生效——Boot 4 起允许注册多个
 * {@code TaskDecorator}，自动组装成组合装饰器。
 *
 * @author jiancai.zhong
 */
@RequiredArgsConstructor
public class AsyncTaskMetricsDecorator implements TaskDecorator {

    /**
     * 指标名，接上 Prometheus 后可查 {@code app_async_task_seconds}。
     */
    public static final String TIMER_NAME = "app.async.task";

    /**
     * 指标注册表，由 actuator 自动配置提供。
     */
    private final MeterRegistry meterRegistry;

    /**
     * 用计时逻辑包装任务。耗时用 {@code nanoTime()} 计算，不受系统时间调整影响。
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
