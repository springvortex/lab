package com.zjc.demo.core.config;

import com.zjc.demo.core.client.TraceIdPropagationInterceptor;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code RestClient} 配置：让所有出站调用自动带上链路 ID。
 *
 * @author jiancai.zhong
 */
@Configuration
public class RestClientConfig {

    /**
     * 链路透传拦截器。
     *
     * @return 链路透传拦截器
     */
    @Bean
    public TraceIdPropagationInterceptor traceIdPropagationInterceptor() {
        return new TraceIdPropagationInterceptor();
    }

    /**
     * 把拦截器挂到 Boot 自动配置的每个 {@code RestClient.Builder} 上。
     *
     * @param traceIdInterceptor 链路透传拦截器
     * @return RestClient 自定义器
     */
    @Bean
    public RestClientCustomizer traceIdRestClientCustomizer(TraceIdPropagationInterceptor traceIdInterceptor) {
        return builder -> builder.requestInterceptor(traceIdInterceptor);
    }
}
