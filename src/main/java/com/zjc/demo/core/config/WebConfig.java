package com.zjc.demo.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层全局配置：跨域 + 拦截器注册扩展点。
 *
 * @author jiancai.zhong
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 允许跨域的来源，生产必须收敛到具体域名。
     */
    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    /**
     * 预检请求缓存时长（秒）。
     */
    @Value("${app.cors.max-age:3600}")
    private Long maxAge;

    /**
     * 是否允许跨域携带凭证。开启后 {@code allowedOrigins} 不能是 {@code *}，否则请求时抛
     * {@code IllegalArgumentException}，且报错不指向配置位置。
     */
    @Value("${app.cors.allow-credentials:false}")
    private boolean allowCredentials;

    /**
     * 跨域配置。
     *
     * @param registry 跨域注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD").allowedHeaders("*")
                // 不暴露的话浏览器读不到 X-Trace-Id
                .exposedHeaders("X-Trace-Id").allowCredentials(allowCredentials).maxAge(maxAge);
    }

    /**
     * 拦截器注册扩展点，需要鉴权 / 限流时在此追加。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 扩展点：业务拦截器在此注册
    }
}
