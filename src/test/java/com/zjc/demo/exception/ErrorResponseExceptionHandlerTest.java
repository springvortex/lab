package com.zjc.demo.exception;

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
 * 「框架自带状态码异常」的处理测试（{@link GlobalExceptionHandler} 中的
 * {@code ErrorResponseException} 分支）。
 *
 * <p>
 * <b>为什么单独测这一类：</b>Spring 框架层用 {@code ResponseStatusException} /
 * {@code ErrorResponseException} 表达 4xx / 5xx（API 版本管理、静态资源、方法不支持等都用它）。
 * 如果全局处理器只有一个兜底 {@code Exception} 分支，这些异常会被改写成 500，
 * 状态码语义丢失，调用方和 APM 都会误判。本项目在接入 API 版本管理时实际踩到过这个坑：
 * 请求里少一个版本头，返回的不是 400 而是 500。
 *
 * <p>
 * 三条用例对应分支：4xx 带原因、4xx 不带原因（消息回退到状态码短语）、5xx（隐藏细节）。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorResponseExceptionHandlerTest {

    /**
     * 走完整 MVC 链路的 MockMvc。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 4xx 且带原因：状态码与原因都要如实透出，方便联调定位。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("4xx 带原因：状态码与原因如实透出")
    void conflictKeepsStatusAndReason() throws Exception {
        mockMvc.perform(get("/test/status-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单状态冲突"));
    }

    /**
     * 4xx 不带原因：消息回退到 HTTP 状态码短语，不能是 {@code null}。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("4xx 不带原因：消息回退到状态码短语")
    void notFoundWithoutReasonFallsBackToPhrase() throws Exception {
        mockMvc.perform(get("/test/status-no-reason"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not Found"));
    }

    /**
     * 5xx：仍然按兜底策略隐藏细节，避免内部信息泄露。
     *
     * @throws Exception 请求执行失败
     */
    @Test
    @DisplayName("5xx：隐藏内部细节")
    void serverErrorHidesDetails() throws Exception {
        mockMvc.perform(get("/test/status-server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("服务内部错误"));
    }
}
