package com.zjc.demo.feature.httpclient;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 声明式 HTTP 客户端示例（Spring Boot 4.0 新能力：HTTP Service Clients）。
 *
 * <p>
 * <b>以前怎么做：</b>手写调用代码——拼 URL、塞路径参数、选 {@code RestClient}/{@code RestTemplate}、
 * 处理返回类型转换、处理异常。一个下游服务就要写一个几十行的封装类，
 * 而且参数名、路径模板这些「对方接口的定义」散落在代码里，对方一改就得靠人肉比对。
 *
 * <p>
 * <b>现在怎么做：</b>只写一个 <b>接口</b>，用注解描述对方接口长什么样，
 * 实现由 Spring 在运行期生成（JDK 动态代理）。本地调用一个 Java 方法，
 * 底层自动变成一次 HTTP 请求。声明和实现彻底分开，接口即文档。
 *
 * <p>
 * <b>注册方式：</b>加 {@code @ImportHttpServices} 注解即可（见
 * {@link HttpServiceClientConfig}），不需要写 {@code @Bean} 方法，也不需要工厂方法样板代码。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>这里只写「相对路径」。服务地址（base URL）由配置项
 * {@code spring.http.serviceclient.catalog.base-url} 提供，
 * 因此同一份代码在 dev/test/prod 指向不同环境，不用改代码；</li>
 * <li>{@code @PathVariable} 等注解来自 {@code org.springframework.web.bind.annotation}，
 * 与写 Controller 时用的是同一套，不需要学新注解；</li>
 * <li>默认底层是 {@code RestClient}（Boot 4 起不再推荐 {@code RestTemplate}）。
 * 出站超时、链路透传、SSRF 过滤都由「Boot 自动配置的 {@code RestClient.Builder}」统一接管，
 * 所以本接口不需要重复配置这些横切能力。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@HttpExchange("/demo/catalog")
public interface CatalogClient {

    /**
     * 按 ID 查询商品。
     *
     * <p>
     * 等价于发起 {@code GET {base-url}/demo/catalog/{id}}。
     *
     * @param id 商品 ID
     * @return 商品信息；下游返回非 2xx 时由底层客户端抛异常，统一交给
     *         {@code GlobalExceptionHandler} 处理
     */
    @GetExchange("/{id}")
    CatalogItem findById(@PathVariable String id);
}
