package com.zjc.demo.config;

import com.zjc.demo.async.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步执行配置：开启 {@code @Async} 并把链路上下文透传到异步线程。
 *
 * <p>
 * 这里只做了两件事，其余全部交给 Spring Boot 的默认值：
 * <ol>
 * <li>{@code @EnableAsync}：不加这个注解 {@code @Async} 完全不生效，且<b>不报错</b>——
 * 方法会变成同步执行，是最容易漏掉的一步；</li>
 * <li>注册 {@link TaskDecorator} Bean：Boot 的任务执行自动配置会收集容器里的
 * {@code TaskDecorator} 并自动应用到它创建的执行器上，因此<b>不需要</b>自己定义
 * {@code @Async} 的执行器 Bean，也不会覆盖 Boot 对虚拟线程等默认配置的适配。</li>
 * </ol>
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
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
