package com.zjc.demo.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 全局配置：跨域、拦截器注册、静态资源等 Web 层横切设置的唯一入口。
 *
 * <p>
 * <b>职责划分提醒（改配置前先想清楚放哪一层）：</b>
 * <ul>
 * <li>{@code Filter}（如 {@code TraceIdFilter}）：最外层，Servlet 级别，拿得到原始
 * request/response，适合链路 ID、包装请求体、编码设置；</li>
 * <li>{@code HandlerInterceptor}：DispatcherServlet 内部，拿得到目标 Handler，
 * 适合鉴权、限流、幂等这类需要知道"命中哪个 Controller"的逻辑；</li>
 * <li>{@code AOP}（如 {@code WebLogAspect}）：方法级别，适合日志、事务埋点。</li>
 * </ul>
 *
 * <p>
 * 本类保持零业务耦合：只提供跨域配置与拦截器注册<b>扩展点</b>，不注册任何业务拦截器。
 *
 * @author jiancai.zhong
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * 允许跨域的来源，生产环境必须收敛到具体域名，禁止使用 {@code *}。
	 */
	@Value("${app.cors.allowed-origins:*}")
	private String[] allowedOrigins;

	/**
	 * 跨域预检请求（OPTIONS）的缓存时长（秒），减少浏览器重复预检。
	 */
	@Value("${app.cors.max-age:3600}")
	private Long maxAge;

	/**
	 * 是否允许跨域携带凭证（Cookie / Authorization 头）。
	 *
	 * <p>
	 * 默认关闭。一旦开启，{@code allowedOrigins} 就<b>不能</b>是 {@code *}，否则 Spring 会在处理请求的瞬间抛
	 * {@code IllegalArgumentException}，且报错信息不指向配置位置——这是前端
	 * 联调阶段最常见的跨域坑，所以模板默认关着，需要时连同一行具体域名一起改。
	 */
	@Value("${app.cors.allow-credentials:false}")
	private boolean allowCredentials;

	/**
	 * 跨域配置。
	 *
	 * <p>
	 * 三个可选来源：{@code app.cors.allowed-origins}、{@code app.cors.max-age}、
	 * {@code app.cors.allow-credentials}，代码内均有默认值，不配也能跑；模板已在
	 * {@code config/application-pub.yaml} 显式列出，改配置即可生效。
	 * <b>生产环境务必把来源收敛到具体域名</b>，不要保留 {@code *}。
	 *
	 * @param registry 跨域注册表
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**").allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD").allowedHeaders("*")
				// 让前端能读取自定义的 X-Trace-Id 响应头，否则浏览器会屏蔽它
				.exposedHeaders("X-Trace-Id").allowCredentials(allowCredentials).maxAge(maxAge);
	}

	/**
	 * 拦截器注册扩展点。
	 *
	 * <p>
	 * 模板默认不注册任何拦截器，保持零业务耦合。业务需要时在此追加即可，例如：
	 *
	 * <pre>{@code
	 * @Resource
	 * private AuthInterceptor authInterceptor;
	 *
	 * @Override
	 * public void addInterceptors(InterceptorRegistry registry) {
	 * 	registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/auth/login")
	 * 			.order(1); // order 越小越先执行
	 * }
	 * }</pre>
	 *
	 * <p>
	 * 注意拦截器不要重复做已经在 {@code WebLogAspect} / {@code TraceIdFilter} 里做过的事（日志、链路
	 * ID），否则同一件事会重复执行两遍。
	 *
	 * @param registry 拦截器注册表
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 扩展点：业务拦截器在此注册
	}
}
