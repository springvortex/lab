package com.zjc.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.zjc.demo.client.TraceIdPropagationInterceptor;

/**
 * 出站 HTTP 客户端配置：让所有出站调用自动带上链路 ID，并统一超时。
 *
 * <p>
 * <b>Boot 2 没有 {@code RestClient}。</b>{@code RestClient} 是 Spring Framework 6.1 才引入的，
 * Boot 2 / Spring 5 时代的出站客户端是 {@code RestTemplate}，因此本类是 Boot 3 分支里
 * {@code RestClientConfig} 的对应物。升级到 Boot 3 时可考虑换成 {@code RestClient} +
 * {@code RestClientCustomizer}，两者思路一致：都不自己造实例，而是注册一个定制器交给 Boot 应用。
 *
 * <p>
 * <b>为什么不需要自己定义 {@code RestTemplate} Bean：</b>Boot 的
 * {@code RestTemplateAutoConfiguration} 会提供 {@code RestTemplateBuilder}（原型作用域），
 * 并且它会把容器里<b>所有</b> {@link RestTemplateCustomizer} 逐个应用到它创建的每个
 * {@code RestTemplate} 上。所以业务侧注入 {@code RestTemplateBuilder} 自行 {@code build()}，
 * 拿到的实例就已经带链路透传与超时，零额外代码。
 *
 * <p>
 * <b>用法示例：</b>
 *
 * <pre>
 * &#64;Service
 * public class UserService {
 *     private final RestTemplate restTemplate;
 *
 *     public UserService(RestTemplateBuilder builder) {
 *         this.restTemplate = builder.rootUri("http://user-service").build();
 *     }
 * }
 * </pre>
 *
 * <p>
 * <b>超时为什么写在这里而不是配置文件：</b>Boot 2 <b>没有</b>
 * {@code spring.http.client.*} 这个配置前缀（那是 Boot 3 才加的，且 Boot 4 又改成了复数
 * {@code spring.http.clients.*}），所以出站超时只能通过 {@code RestTemplateBuilder#setConnectTimeout}
 * 这类 API 或本类的定制器设置。模板把它做成 {@code app.http.client.*} 自定义键，
 * 既保留了「改配置不用改代码」的习惯，也顺便把这个版本差异收敛在一处。
 *
 * @author jiancai.zhong
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 出站建连超时（毫秒），由 {@code app.http.client.connect-timeout} 配置。
     */
    @Value("${app.http.client.connect-timeout:3000}")
    private int connectTimeoutMillis;

    /**
     * 出站读响应超时（毫秒），由 {@code app.http.client.read-timeout} 配置。
     */
    @Value("${app.http.client.read-timeout:10000}")
    private int readTimeoutMillis;

    /**
     * 链路透传拦截器，单独注册便于业务自定义的 {@code RestTemplate} 也能复用。
     *
     * @return 链路透传拦截器
     */
    @Bean
    public TraceIdPropagationInterceptor traceIdPropagationInterceptor() {
        return new TraceIdPropagationInterceptor();
    }

    /**
     * 把链路透传拦截器挂到 Boot 自动配置的每个 {@code RestTemplate} 上，并统一设置超时。
     *
     * <p>
     * 超时用 {@code SimpleClientHttpRequestFactory} 直接替换请求工厂实现，不依赖
     * 传入实例原本的工厂类型，行为可预期；同时避免「不配超时 = 无限等待」——
     * 下游hang住会把上游线程池拖死，是生产上最常见的一类雪崩起因。
     *
     * @param traceIdInterceptor 链路透传拦截器
     * @return RestTemplate 自定义器
     */
    @Bean
    public RestTemplateCustomizer traceIdRestTemplateCustomizer(TraceIdPropagationInterceptor traceIdInterceptor) {
        return restTemplate -> {
            restTemplate.getInterceptors().add(traceIdInterceptor);
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeoutMillis);
            requestFactory.setReadTimeout(readTimeoutMillis);
            restTemplate.setRequestFactory(requestFactory);
        };
    }
}
