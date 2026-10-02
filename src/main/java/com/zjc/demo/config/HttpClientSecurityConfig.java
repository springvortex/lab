package com.zjc.demo.config;

import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 出站 HTTP 地址过滤配置：用 {@link InetAddressFilter} 给「服务端请求伪造」上锁
 * （Spring Boot 4.1 新能力）。
 *
 * <p>
 * <b>要防的是什么：</b>只要接口里存在「由用户提供 URL 或域名」的出站调用
 * （图片转存、Webhook 回调、URL 预览、回调通知……），攻击者就能把它当成内网跳板：
 * 让你去请求 {@code http://169.254.169.254/latest/meta-data/}（云厂商元数据服务），
 * 从而拿到临时凭证；或者扫内网端口、探测 {@code http://localhost:8080/actuator/env}
 * 这类只在内网可达的地址。这类攻击就是 SSRF。
 *
 * <p>
 * <b>怎么生效：</b>只需把过滤器声明成 Bean（<b>必须唯一</b>）。Spring Boot 的
 * {@code HttpClientAutoConfiguration} 会通过 {@code ObjectProvider} 取它，
 * 注入到自动配置的 {@code HttpClientSettings} 里，<b>阻塞式与响应式客户端一并生效</b>——
 * {@code RestClient}、{@code RestTemplate}、{@code WebClient} 都跑不掉，不需要逐个客户端配置。
 *
 * <p>
 * <b>注意 {@code not(...)} 的语义：</b>{@code InetAddressFilter.not(x)} 表示「允许不匹配 x
 * 的地址」，也就是<b>拦截 x</b>。所以下面两行读作「拦截链路本地地址、拦截 0.0.0.0/8」。
 *
 * <p>
 * <b>为什么不直接写 {@code InetAddressFilter.not(InetAddressFilter.internalAddresses())}：</b>
 * 那样会把回环（127.0.0.0/8）和私网（10/8、172.16/12、192.168/16）整段封掉。
 * 如果服务本身要调用内网的其他系统，这个「一刀切」会当场把正常业务打死。
 * 正确姿势是先列白名单再排除，或像这里一样只封真正不该被访问的地址段：
 *
 * <pre>{@code
 * // 更严格的做法：只允许公网地址
 * InetAddressFilter.not(InetAddressFilter.internalAddresses()).and("203.0.113.0/24")
 * }</pre>
 *
 * @author jiancai.zhong
 */
@Configuration(proxyBeanMethods = false)
public class HttpClientSecurityConfig {

    /**
     * 出站地址过滤器：拦截链路本地地址与 {@code 0.0.0.0/8}。
     *
     * <p>
     * {@code 169.254.0.0/16} 是链路本地地址段，云环境里最著名的 SSRF 目标
     * {@code 169.254.169.254}（元数据服务）就在其中；{@code 0.0.0.0/8} 语义模糊、没有正当用途，
     * 一并封掉。命中拦截时客户端会抛与地址相关的异常，而不是把请求发出去。
     *
     * @return 出站地址过滤器
     */
    @Bean
    public InetAddressFilter outboundInetAddressFilter() {
        return InetAddressFilter.not("169.254.0.0/16")
                .andNot("0.0.0.0/8");
    }
}
