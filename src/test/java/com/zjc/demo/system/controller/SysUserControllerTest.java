package com.zjc.demo.system.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjc.demo.system.entity.SysUser;
import com.zjc.demo.system.service.SysUserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SysUserController} 切片测试，顺带验证 {@code GlobalExceptionHandler} 的关键分支。
 * Service 全部 mock，不碰数据库。
 *
 * @author jiancai.zhong
 */
@WebMvcTest(SysUserController.class)
@DisplayName("SysUserController 接口切片")
class SysUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Boot 4 移除了 @MockBean，用 Spring 自带的 @MockitoBean 替代 */
    @MockitoBean
    private SysUserService sysUserService;

    @Test
    @DisplayName("详情：200 且不回传密码")
    void detailStripsPassword() throws Exception {
        given(sysUserService.getById(1L)).willReturn(user(1L, "admin"));

        mockMvc.perform(get("/api/sys/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.password").value(nullValue()));
    }

    @Test
    @DisplayName("详情：不存在走 BusinessException → 404")
    void detailNotFound() throws Exception {
        given(sysUserService.getById(999L)).willReturn(null);

        mockMvc.perform(get("/api/sys/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    @DisplayName("新增：@NotBlank 失败 → 400，提示到具体字段")
    void createParamInvalid() throws Exception {
        mockMvc.perform(post("/api/sys/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("username: 登录名不能为空"));
    }

    @Test
    @DisplayName("新增：密码为空 → 400")
    void createPasswordRequired() throws Exception {
        mockMvc.perform(post("/api/sys/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"someone\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    @Test
    @DisplayName("分页：返回 PageResult 结构，密码剥空")
    void pageReturnsPageResult() throws Exception {
        Page<SysUser> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(user(1L, "admin")));
        given(sysUserService.searchByCondition(any(), any(), any(), any())).willReturn(page);

        mockMvc.perform(get("/api/sys/users").param("current", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"))
                .andExpect(jsonPath("$.data.records[0].password").value(nullValue()));
    }

    private SysUser user(long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("$2a$10$cipher");
        return user;
    }
}
