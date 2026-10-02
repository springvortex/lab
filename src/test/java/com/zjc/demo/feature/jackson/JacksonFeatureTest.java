package com.zjc.demo.feature.jackson;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Jackson 3 读/写特性开关的集成测试。
 *
 * <p>
 * 为什么这些开关值得写测试：它们都是<b>静默</b>生效的开关——配错了不报错，
 * 只是行为退回默认值。而它们防的又是安全类问题（重复字段覆盖、解析深度攻击），
 * 一旦哪天配置被误删，全靠测试发现。
 *
 * <p>
 * 实测两个开关的默认值（读 Jackson 3.1.0 的 {@code enabledByDefault()} 得到）：
 * {@code STRICT_DUPLICATE_DETECTION} 默认 <b>false</b>、
 * {@code WRITE_BIGDECIMAL_AS_PLAIN} 默认 <b>false</b>，所以这俩开关确实改变了行为，
 * 不是「配了也白配」。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class JacksonFeatureTest {

    /**
     * 走完整 MVC 链路的 MockMvc。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 重复字段会被拒绝。
     *
     * <p>
     * 请求体里出现两个 {@code orderId} 属于非法报文：不同解析器取值规则不同，
     * 攻击者可借此让网关与业务代码看到不同的值。开启严格重复检测后直接 400。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("重复字段的 JSON 被拒绝（400）")
    void duplicateFieldsAreRejected() throws Exception {
        mockMvc.perform(post("/demo/jackson/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"orderId\":\"A001\",\"orderId\":\"B999\",\"amount\":1}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * {@code BigDecimal} 按普通小数输出，不是科学计数法。
     *
     * <p>
     * 输入 {@code 1.0E+7}（科学计数法写法，值与 10000000 相同），
     * 输出应为 {@code 10000000}。金额字段出现科学计数法会让对账脚本、前端展示踩坑。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("BigDecimal 不用科学计数法输出")
    void bigDecimalIsWrittenAsPlain() throws Exception {
        mockMvc.perform(post("/demo/jackson/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"orderId\":\"A001\",\"amount\":1.0E+7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(10000000));
    }
}
