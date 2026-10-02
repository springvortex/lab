package com.zjc.demo.feature.httpclient;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 声明式 HTTP 客户端的注册配置。
 *
 * <p>
 * {@code @ImportHttpServices} 就是「把接口扫描出来、生成代理、注册成 Bean」的开关。
 * 一个注解替代了过去一整个 {@code @Configuration} 类里的工厂方法样板代码。
 *
 * <p>
 * <b>{@code group} 是干什么的：</b>它是这组客户端的配置前缀。上面写的
 * {@code group = "catalog"}，对应配置项
 * {@code spring.http.serviceclient.catalog.base-url}。同一组里的多个接口共享
 * base-url、超时、默认请求头等设置，所以「按下游服务分组」是最自然的用法。
 *
 * <pre>{@code
 * spring:
 *   http:
 *     serviceclient:
 *       catalog:
 *         base-url: http://localhost:8000
 *         connect-timeout: 3s
 *         read-timeout: 10s
 *         default-header:
 *           X-Client: spring-vortex-demo
 * }</pre>
 *
 * <p>
 * <b>两种注册写法（按需选）：</b>
 * <ul>
 * <li>{@code types = CatalogClient.class}：显式列出接口，一目了然，推荐；</li>
 * <li>{@code basePackages = "com.zjc.demo.feature.httpclient"}：按包扫描，
 * 接口多了以后省事，但「谁被注册了」需要靠包结构去推。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(types = CatalogClient.class, group = "catalog")
public class HttpServiceClientConfig {
}
