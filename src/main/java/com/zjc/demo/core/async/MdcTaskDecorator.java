package com.zjc.demo.core.async;

import org.slf4j.MDC;
import java.util.Map;
import org.springframework.core.task.TaskDecorator;

/**
 * 把 {@link MDC} 上下文透传到异步线程，让 {@code @Async} 任务里的日志也能带上 traceId。
 *
 * @author jiancai.zhong
 */
public class MdcTaskDecorator implements TaskDecorator {

    /**
     * 用提交时刻的 MDC 快照包装任务。
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
