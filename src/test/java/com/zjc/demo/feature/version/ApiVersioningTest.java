package com.zjc.demo.feature.version;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 版本管理的集成测试。
 *
 * <p>
 * 版本路由只在真实 MVC 链路里成立（要靠请求头解析版本再匹配 Handler），
 * 因此这里走完整的 MockMvc 而不是调 Controller 方法。测试要点：
 * <ul>
 * <li>同一个 URL + 不同版本头 → 命中不同方法；</li>
 * <li>不带版本头 → 不命中版本化接口（本示例没配默认版本）；</li>
 * <li>版本不在 supported 清单里 → 400，而不是静默降级。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiVersioningTest {

    /**
     * 版本号请求头，与 {@code spring.mvc.apiversion.use.header} 配置一致。
     */
    private static final String VERSION_HEADER = "X-API-Version";

    /**
     * 走完整 MVC 链路的 MockMvc。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * v1：响应体只有订单号与金额。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("版本 1.0：返回 v1 结构")
    void versionOneReturnsShortPayload() throws Exception {
        mockMvc.perform(get("/api/version-demo/orders/A001").header(VERSION_HEADER, "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("A001"))
                .andExpect(jsonPath("$.data.amount").value("199.00"))
                .andExpect(jsonPath("$.data.currency").doesNotExist());
    }

    /**
     * v2：同一个 URL，响应体多出币种与状态。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("版本 2.0：同一 URL 返回 v2 结构")
    void versionTwoReturnsExtendedPayload() throws Exception {
        mockMvc.perform(get("/api/version-demo/orders/A001").header(VERSION_HEADER, "2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("A001"))
                .andExpect(jsonPath("$.data.currency").value("CNY"))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    /**
     * 不带版本头时落到配置的默认版本（1.0），老客户端因此不用改。
     *
     * <p>
     * 这里反过来验证了一条重要经验：启用版本管理时<b>必须</b>配
     * {@code spring.mvc.apiversion.default}。否则没带版本头的请求会抛
     * {@code MissingApiVersionException}，连 {@code /hello} 这种完全没版本化的接口
     * 都会变成 500——本项目在接入这个特性时实际踩到过。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("不带版本头：落到默认版本 1.0")
    void missingVersionHeaderFallsBackToDefault() throws Exception {
        mockMvc.perform(get("/api/version-demo/orders/A001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("A001"))
                .andExpect(jsonPath("$.data.currency").doesNotExist());
    }

    /**
     * 请求了未声明的版本时返回 400，避免调用方以为自己拿到了数据。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("未支持的版本：返回 400")
    void unsupportedVersionIsRejected() throws Exception {
        mockMvc.perform(get("/api/version-demo/orders/A001").header(VERSION_HEADER, "9.9"))
                .andExpect(status().isBadRequest());
    }
}
