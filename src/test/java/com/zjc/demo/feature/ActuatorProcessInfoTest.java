package com.zjc.demo.feature;

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
 * {@code /actuator/info} 的 process 段（Spring Boot 4.1 新增字段）。
 *
 * <p>
 * {@code management.info.process.enabled} 默认 <b>false</b>，模板里显式打开。
 * 打开后 info 端点会多出 {@code process} 段，包含 {@code uptime}（运行时长）、
 * {@code startTime}、{@code currentTime}、{@code timezone}、{@code locale}、
 * {@code workingDirectory} 六个字段——排查「容器时区不对」「跑在哪个工作目录」
 * 这类环境问题时非常省事，不需要再登录机器。
 *
 * <p>
 * 注意它是运维端点：会暴露服务器相关信息，公网环境务必配合鉴权或只在内网暴露
 * （模板只在 dev/test 打开，生产按需）。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorProcessInfoTest {

    /**
     * 走完整 MVC 链路的 MockMvc。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * info 端点必须包含 process 段与其中的时间相关字段。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("GET /actuator/info：含 process 段")
    void infoContainsProcessSection() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.process.uptime").exists())
                .andExpect(jsonPath("$.process.startTime").exists())
                .andExpect(jsonPath("$.process.timezone").exists())
                .andExpect(jsonPath("$.process.workingDirectory").exists());
    }
}
