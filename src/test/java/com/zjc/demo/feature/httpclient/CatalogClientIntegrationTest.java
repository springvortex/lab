package com.zjc.demo.feature.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * 声明式 HTTP 客户端的端到端测试：真实起服务、真实发 HTTP 请求。
 *
 * <p>
 * 与 {@link CatalogClientContractTest} 的分工：那边验证「请求内容对不对」（用
 * {@code MockRestServiceServer} 拦截），这边验证「整条链路通不通」——接口代理能发出去、
 * 下游能收到、响应能映射回来。
 *
 * <p>
 * <b>为什么这里手写 {@code HttpServiceProxyFactory}，而不是直接用注入的客户端：</b>
 * 随机端口是在 Web 服务器<b>启动之后</b>才写回 Environment 的，而自动配置的客户端 Bean
 * 在启动过程中就已经创建好了——所以 {@code base-url} 里写
 * {@code http://localhost:${local.server.port}} <b>解析不到</b>，会直接启动失败
 * （本项目实际踩过，别试）。测试里要连随机端口，只能在拿到端口后自己造一个客户端。
 * 生产代码不存在这个问题：base-url 是配置里的固定值（见
 * {@code spring.http.serviceclient.catalog.base-url}）。
 *
 * @author jiancai.zhong
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CatalogClientIntegrationTest {

    /**
     * 由 {@code @ImportHttpServices} 注册的声明式客户端。
     *
     * <p>
     * 它的 base-url 来自配置文件（模板里是 {@code http://localhost:${server.port}}），
     * 只在正常启动（端口已知）时可用，所以本测试只断言它<b>确实被注册了</b>，
     * 真正发请求用的是下面手工构造的客户端。
     */
    @Autowired
    private CatalogClient catalogClient;

    /**
     * 随机端口。
     */
    @LocalServerPort
    private int port;

    /**
     * {@code @ImportHttpServices} 必须把接口注册成可注入的 Bean。
     */
    @Test
    @DisplayName("注册：接口已被注册为 Bean")
    void clientIsRegisteredAsBean() {
        assertThat(catalogClient).isNotNull();
    }

    /**
     * 端到端：接口代理 → 真实 HTTP → 本应用的下游接口 → 反序列化成对象。
     */
    @Test
    @DisplayName("端到端：客户端接口能取到下游数据")
    void declarativeClientReturnsDownstreamData() {
        CatalogClient client = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(RestClient.create("http://localhost:" + port)))
                .build()
                .createClient(CatalogClient.class);

        CatalogItem item = client.findById("A001");

        assertThat(item.id()).isEqualTo("A001");
        assertThat(item.name()).isEqualTo("机械键盘");
    }

    /**
     * 顺带演示 {@code RestTestClient} 的另一种绑定方式：{@code bindToServer()}，
     * 对真实运行的服务发请求（{@code bindTo(mockMvc)} 见
     * {@code com.zjc.demo.feature.RestTestClientDemoTest}）。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("RestTestClient.bindToServer：访问真实服务")
    void restTestClientCanBindToRunningServer() throws Exception {
        RestTestClient client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        client.get().uri("/demo/catalog/{id}", "A002")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("A002")
                .jsonPath("$.name").isEqualTo("机械键盘");
    }
}
