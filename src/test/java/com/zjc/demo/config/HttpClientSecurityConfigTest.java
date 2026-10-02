package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.InetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.InetAddressFilter;

/**
 * {@link HttpClientSecurityConfig} 出站地址过滤器的单元测试。
 *
 * <p>
 * <b>{@code matches} 的语义必须先说清楚，否则配置会写反：</b>
 * {@code matches(address) == true} 表示<b>允许放行</b>，{@code false} 表示被拦截。
 * 因此 {@code InetAddressFilter.not("169.254.0.0/16")} 的含义是
 * 「放行不在 169.254/16 里的地址」，也就是<b>拦截</b>该网段。
 *
 * <p>
 * 这层测试防的是「配置看起来对、实际拦错了对象」：把 {@code not} 少写一个，
 * 过滤器就会变成「只放行元数据服务」，安全加固直接反成安全漏洞，而且<b>不会有任何报错</b>。
 *
 * @author jiancai.zhong
 */
class HttpClientSecurityConfigTest {

    /**
     * 被测过滤器。
     */
    private final InetAddressFilter filter = new HttpClientSecurityConfig().outboundInetAddressFilter();

    /**
     * 云元数据服务地址必须被拦截——这是 SSRF 最经典、危害最大的目标。
     *
     * @throws Exception 地址解析失败
     */
    @Test
    @DisplayName("拦截 169.254.169.254（云元数据服务）")
    void blocksCloudMetadataEndpoint() throws Exception {
        assertThat(filter.matches(InetAddress.getByName("169.254.169.254"))).isFalse();
    }

    /**
     * 链路本地网段整段拦截，而不只是那一个著名地址。
     *
     * @throws Exception 地址解析失败
     */
    @Test
    @DisplayName("拦截整个链路本地网段")
    void blocksWholeLinkLocalRange() throws Exception {
        assertThat(filter.matches(InetAddress.getByName("169.254.1.1"))).isFalse();
        assertThat(filter.matches(InetAddress.getByName("0.1.2.3"))).isFalse();
    }

    /**
     * 普通公网地址与内网地址都必须放行，否则会把正常业务打死。
     *
     * <p>
     * 内网地址这条尤其重要：模板刻意<b>没有</b>用
     * {@code InetAddressFilter.not(InetAddressFilter.internalAddresses())}，
     * 因为它会把回环与私网整段封掉，服务一旦要调用内网系统就会全线失败。
     *
     * @throws Exception 地址解析失败
     */
    @Test
    @DisplayName("放行公网地址与内网地址")
    void allowsPublicAndPrivateAddresses() throws Exception {
        assertThat(filter.matches(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(filter.matches(InetAddress.getByName("10.1.2.3"))).isTrue();
        assertThat(filter.matches(InetAddress.getByName("127.0.0.1"))).isTrue();
    }
}
