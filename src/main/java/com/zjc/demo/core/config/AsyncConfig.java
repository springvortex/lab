package com.zjc.demo.core.config;

import com.zjc.demo.core.async.AsyncTaskMetricsDecorator;
import com.zjc.demo.core.async.MdcTaskDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步执行配置：开启 {@code @Async}，并把链路上下文透传到异步线程。
 *
 * @author jiancai.zhong
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 链路上下文透传装饰器。注册成 Bean 后 Boot 会自动应用到它创建的执行器上。
     *
     * @return MDC 透传装饰器
     */
    @Bean
    @Order(0)
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * 异步任务耗时埋点装饰器，与上一个并列生效。{@code @Order} 值最大，是最外层。
     *
     * @param meterRegistry 指标注册表
     * @return 耗时埋点装饰器
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    TaskDecorator asyncTaskMetricsDecorator(MeterRegistry meterRegistry) {
        return new AsyncTaskMetricsDecorator(meterRegistry);
    }
}
