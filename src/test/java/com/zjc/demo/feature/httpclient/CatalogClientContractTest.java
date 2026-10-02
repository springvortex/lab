package com.zjc.demo.feature.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * {@link CatalogClient} 的契约测试：不启动服务器，验证「接口声明 → 实际发出的 HTTP 请求」。
 *
 * <p>
 * <b>为什么单独写这一层：</b>声明式客户端的价值就在于「注解写对了，请求就发对了」。
 * 如果只用 MockMvc 测自己的接口，验证不到出站请求的 URL、方法、请求体是否符合对方契约——
 * 而注解写错（路径模板拼错、方法用错）编译期完全看不出来，只有发出去的请求能证明。
 * 这里用 {@code MockRestServiceServer} 把出站请求拦下来断言，不依赖任何外部服务。
 *
 * <p>
 * <b>注意这里手写了 {@code HttpServiceProxyFactory}：</b>生产代码里这步由
 * {@code @ImportHttpServices} 自动完成（见 {@link HttpServiceClientConfig}），
 * 测试里手写是为了能换成被拦截的 {@code RestClient}，从而断言请求内容。
 *
 * @author jiancai.zhong
 */
class CatalogClientContractTest {

    /**
     * 接口声明的路径必须转成对下游的真实请求，且响应体能反序列化成 {@link CatalogItem}。
     */
    @Test
    @DisplayName("契约：路径模板与返回体映射正确")
    void requestMatchesDeclaredContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CatalogClient client = createClient(builder.baseUrl("http://catalog.example.com").build());

        server.expect(requestTo("http://catalog.example.com/demo/catalog/A001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"A001","name":"机械键盘","price":"399.00"}
                        """, MediaType.APPLICATION_JSON));

        CatalogItem item = client.findById("A001");

        assertThat(item.id()).isEqualTo("A001");
        assertThat(item.name()).isEqualTo("机械键盘");
        assertThat(item.price()).isEqualTo("399.00");
        server.verify();
    }

    /**
     * 直接用 {@code HttpServiceProxyFactory} 为接口生成代理。
     *
     * @param restClient 底层 HTTP 客户端
     * @return 接口代理
     */
    private static CatalogClient createClient(RestClient restClient) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(CatalogClient.class);
    }
}
