package com.zjc.demo.config;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zjc.demo.client.TraceIdPropagationInterceptor;

/**
 * {@code RestClient} 配置：让所有出站调用自动带上链路 ID。
 *
 * <p>
 * 引入 {@code spring-boot-starter-restclient} 后，{@code RestClient.Builder} 由
 * Spring Boot 自动配置（原型作用域），自带消息转换器、SSL、可观测性等默认适配。本类<b>不再自己造 builder</b>， 而是注册
 * {@link RestClientCustomizer}——Boot 会把容器里的自定义器逐个应用到它创建的 每个 builder 上，因此业务侧注入
 * {@code RestClient.Builder} 就直接带链路透传，零额外代码。
 *
 * <p>
 * <b>用法示例（声明式 HTTP 客户端 / {@code @HttpExchange}）：</b>
 *
 * <pre>{@code
 * @Bean
 * UserApi userApi(RestClient.Builder builder) {
 * 	RestClient restClient = builder.baseUrl("http://user-service").build();
 * 	RestClientAdapter adapter = RestClientAdapter.create(restClient);
 * 	return HttpServiceProxyFactory.builderFor(adapter).build().createClient(UserApi.class);
 * }
 * }</pre>
 *
 * <p>
 * 若某个客户端需要自定义超时、拦截器等，注入 builder 后自行 {@code build()} 即可， 自定义器已经生效；想彻底接管则自己定义
 * {@code RestClient.Builder} Bean， 此时 Boot 的自动配置会让位，记得把
 * {@code TraceIdPropagationInterceptor} 一并挂上。
 *
 * @author jiancai.zhong
 */
@Configuration
public class RestClientConfig {

	/**
	 * 链路透传拦截器，单独注册便于业务自定义的 {@code RestClient} 也能复用。
	 *
	 * @return 链路透传拦截器
	 */
	@Bean
	public TraceIdPropagationInterceptor traceIdPropagationInterceptor() {
		return new TraceIdPropagationInterceptor();
	}

	/**
	 * 把链路透传拦截器挂到 Boot 自动配置的每个 {@code RestClient.Builder} 上。
	 *
	 * @param traceIdInterceptor 链路透传拦截器
	 * @return RestClient 自定义器
	 */
	@Bean
	public RestClientCustomizer traceIdRestClientCustomizer(TraceIdPropagationInterceptor traceIdInterceptor) {
		return builder -> builder.requestInterceptor(traceIdInterceptor);
	}
}
