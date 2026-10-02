package com.zjc.demo.feature;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * {@code RestTestClient} 用法示例（Spring Framework 7 / Boot 4 新增的测试客户端）。
 *
 * <p>
 * <b>它解决的问题：</b>{@code MockMvc} 只能拿到响应体的字符串，要断言某个字段就得拷 JSON 路径
 * 字符串——字段名写错、结构改动都不会有编译期提示。{@code RestTestClient} 提供
 * <b>类型安全</b>的断言：{@code expectBody(YourDto.class)} 自动反序列化，
 * {@code returnResult(YourDto.class)} 直接拿到对象，重构时 IDE 能帮你改字段名。
 *
 * <p>
 * 三种绑定方式，按测试目标选：
 * <ul>
 * <li>{@code bindTo(MockMvc)}：走 MockMvc，不起真实服务，快——<b>最常用</b>，下面就是这种；</li>
 * <li>{@code bindToServer()}：对真实运行的服务发请求（见
 * {@code com.zjc.demo.feature.httpclient.CatalogClientIntegrationTest}）；</li>
 * <li>{@code bindToController(...)}：只装一个 Controller，不加载完整 Spring 上下文。</li>
 * </ul>
 *
 * <p>
 * <b>注意：</b>非响应式场景不要再用 {@code WebTestClient}——它是给 WebFlux 用的。
 * Spring Boot 4 还另有一个 {@code spring-boot-resttestclient} 模块，引入后可获得
 * {@code @AutoConfigureRestTestClient} 自动注入与 {@code TestRestTemplate}
 * （注意 {@code TestRestTemplate} 在 Boot 4 里也搬到了这个模块）。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestTestClientDemoTest {

    /**
     * 走完整 MVC 链路的 MockMvc。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 基于 MockMvc 的测试客户端：断言方式与 MockMvc 一致，但多了类型安全的取值能力。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("bindTo(MockMvc)：类型安全地断言与取值")
    void bindToMockMvcGivesTypedAccess() throws Exception {
        RestTestClient client = RestTestClient.bindTo(mockMvc).build();

        client.get().uri("/hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo("hello SpringVortexDemo!");
    }

    /**
     * 用 {@code returnResult(Class)} 把响应体直接反序列化成对象，避免手写 JSON 路径。
     *
     * <p>
     * 统一响应的 {@code data} 是字符串，因此这里用 {@code String.class} 承接；
     * 若 {@code data} 是对象，就换成对应的 DTO 类型。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("returnResult：把响应体映射成对象")
    void returnResultMapsBodyToObject() throws Exception {
        RestTestClient client = RestTestClient.bindTo(mockMvc).build();

        String body = client.get().uri("/hello")
                .exchange()
                .returnResult(String.class)
                .getResponseBody();

        assertThat(body).contains("hello SpringVortexDemo!");
    }
}
