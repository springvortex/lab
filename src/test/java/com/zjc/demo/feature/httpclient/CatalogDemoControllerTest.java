package com.zjc.demo.feature.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link CatalogDemoController} 的测试：用 {@code @MockitoBean} 替换掉声明式客户端。
 *
 * <p>
 * <b>{@code @MockitoBean} 本身也是 Boot 4 的变化点：</b>它是 Spring Framework 7 引入的
 * 原生注解，取代了 Boot 自己的 {@code @MockBean} / {@code @SpyBean}
 * （老的 {@code @MockBean} 在 Boot 4 已废弃）。区别在于它归 Spring TestContext 管理，
 * 不依赖 Boot 的测试自动配置，因此在非 Boot 测试里也能用。
 *
 * <p>
 * 这里替换客户端而不是连真实下游：Controller 的职责只是「调客户端 + 包一层统一响应」，
 * 把出站调用换成桩，测试就只验证它自己的逻辑，不受网络与下游状态影响。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogDemoControllerTest {

    /**
     * 走完整 MVC 链路的 MockMvc。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 被替换的声明式客户端：所有出站调用都由本桩对象应答，不发真实请求。
     */
    @MockitoBean
    private CatalogClient catalogClient;

    /**
     * 代理接口必须把下游数据包进统一响应结构。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("代理接口：包装成统一响应返回")
    void proxyWrapsDownstreamDataIntoApiResponse() throws Exception {
        given(catalogClient.findById("A001")).willReturn(new CatalogItem("A001", "机械键盘", "399.00"));

        mockMvc.perform(get("/demo/catalog-proxy/A001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("A001"))
                .andExpect(jsonPath("$.data.name").value("机械键盘"));
    }

    /**
     * 「最近一次查询」在没查过时必须返回 {@code null}，而不是报错。
     *
     * <p>
     * 这条同时验证了 {@code @Nullable} 的用法：字段确实可能为空，
     * 调用方需要按「可能为空」处理。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("最近一次查询：没查过时 data 为 null")
    void lastQueriedIsNullBeforeAnyQuery() throws Exception {
        String body = mockMvc.perform(get("/demo/catalog/last-queried"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"data\":null");
    }

    /**
     * 调过下游之后，最近一次查询能读到数据。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("最近一次查询：调过下游后能读到数据")
    void lastQueriedIsUpdatedAfterQuery() throws Exception {
        mockMvc.perform(get("/demo/catalog/A003")).andExpect(status().isOk());

        mockMvc.perform(get("/demo/catalog/last-queried"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("A003"));
    }
}
