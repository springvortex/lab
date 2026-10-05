package com.zjc.demo.async;

import java.util.concurrent.TimeUnit;

import org.springframework.core.task.TaskDecorator;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 给异步任务打耗时埋点的任务装饰器。
 *
 * <p>
 * 它与 {@link MdcTaskDecorator} 是两个独立的关注点（一个管链路透传、一个管指标埋点），
 * 由 {@code AsyncConfig} 用 {@code CompositeTaskDecorator} 组装成一个 Bean 后生效
 * ——Boot 3 下容器里只能有一个 {@code TaskDecorator} Bean，详见 {@code AsyncConfig} 的说明。
 *
 * <p>
 * <b>顺序为什么重要（细节，容易踩）：</b>{@code CompositeTaskDecorator} 是
 * <i>依次包装</i>的——按顺序遍历装饰器列表，每个装饰器包住上一个的结果。
 * 因此<b>列表里最后一个装饰器是最外层</b>，它最先执行、最后收尾。
 * 顺序在两种情况下会出问题：
 * <ul>
 * <li>两个装饰器都往 MDC 写键：外层若是「用快照整体覆盖 MDC」的装饰器，
 * 内层刚写进去的键会被覆盖掉；</li>
 * <li>耗时埋点如果被放在最内层，测到的是「去掉外层开销之后」的时间。
 * 埋点通常应放最外层，测到端到端耗时。</li>
 * </ul>
 * 本装饰器只读时间、不碰 MDC，因此与 {@link MdcTaskDecorator} 不存在冲突；
 * 在 {@code AsyncConfig} 的组合列表里放在最后，让它成为最外层。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>埋点失败不应该让业务任务失败，因此这里只记录时间、不抛异常；</li>
 * <li>耗时用 {@code System.nanoTime()} 计算（单调时钟，不受系统时间调整影响），
 * 不要用 {@code currentTimeMillis()}。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
public class AsyncTaskMetricsDecorator implements TaskDecorator {

    /**
     * 异步任务耗时指标名。
     *
     * <p>
     * 接上 Prometheus 后可直接查 {@code app_async_task_seconds}，
     * 用来发现「异步任务悄悄变慢」这类问题——异步任务不影响接口响应时间，
     * 不埋点的话变慢很难被发现。
     */
    public static final String TIMER_NAME = "app.async.task";

    /**
     * 指标注册表，由 Spring Boot 自动配置提供（项目引入了 actuator 就有）。
     */
    private final MeterRegistry meterRegistry;

    /**
     * 构造装饰器。
     *
     * @param meterRegistry 指标注册表
     */
    public AsyncTaskMetricsDecorator(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

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
