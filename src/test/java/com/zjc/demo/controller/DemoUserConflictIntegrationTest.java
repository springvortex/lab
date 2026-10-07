package com.zjc.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.zjc.demo.entity.DemoUser;
import com.zjc.demo.service.DemoUserService;

import jakarta.annotation.Resource;

/**
 * 乐观锁冲突分支测试：验证 {@code updateById} 影响 0 行时接口返回 409。
 *
 * <p>
 * <b>为什么要单独开一个类：</b>真实并发在单线程测试里凑不出来——Controller 是先
 * {@code getById} 再 {@code updateById}，两步之间插不进「别人先改了一版」这个动作。
 * 这里用 {@link MockitoBean} 把 Service 换成打桩实现，直接让 {@code updateById}
 * 返回 {@code false}，从而精确命中「更新影响 0 行」这条分支。
 *
 * <p>
 * 换 Bean 会导致 Spring 新建一份测试上下文（缓存 key 不同），所以这类测试要少而精。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoUserConflictIntegrationTest {

	/**
	 * 被 Mockito 替换掉的用户服务。
	 */
	@MockitoBean
	private DemoUserService demoUserService;

	/**
	 * 走完整 MVC 链路的 MockMvc。
	 */
	@Resource
	private MockMvc mockMvc;

	/**
	 * 更新影响 0 行（版本已被并发改过）时返回 409，提示调用方重试。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("PUT /api/users/{id}：乐观锁冲突返回 409")
	void conflictingUpdateReturns409() throws Exception {
		DemoUser user = new DemoUser();
		user.setId(1L);
		user.setUsername("stale");

		Mockito.when(demoUserService.getById(1L)).thenReturn(user);
		Mockito.when(demoUserService.updateById(Mockito.any(DemoUser.class))).thenReturn(false);

		mockMvc.perform(put("/api/users/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"new-name\"}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.success").value(false));
	}
}
