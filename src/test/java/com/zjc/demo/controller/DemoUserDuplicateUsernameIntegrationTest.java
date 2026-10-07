package com.zjc.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

/**
 * 重复用户名 → 409 的端到端验证。
 *
 * <p>
 * <b>为什么必须走真实 HTTP：</b>这条链路上有三处都可能断——驱动报的重复键错误会不会被
 * MyBatis-Spring 翻译成 {@code DuplicateKeyException}、Controller 的 {@code try-catch}
 * 接不接得住、{@code BusinessException} 会不会被全局处理器转成 409。
 * 任何一处没接上，表现都是「500 服务内部错误」。只测某一层证明不了整条链。
 *
 * <p>
 * <b>为什么这里能安心用 {@code @Transactional}：</b>MySQL 的一条语句报错只让<b>该语句</b>失败，
 * 事务本身仍然可用，后面的语句照常执行，测试结束时统一回滚即可。
 * （PostgreSQL 不同：语句报错会把整个事务置为 aborted，后续语句全部拒绝，
 * 那种情况下才必须让两次插入各自独立提交、再手工清理。）
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoUserDuplicateUsernameIntegrationTest {

	/**
	 * 走完整 MVC 链路的 MockMvc。
	 */
	@Resource
	private MockMvc mockMvc;

	/**
	 * 同一个用户名插入两次，第二次必须返回 409 并指出是哪个字段的值重复。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("POST /api/users：用户名重复返回 409 且提示明确")
	void duplicateUsernameReturns409() throws Exception {
		String username = "dup-" + UUID.randomUUID().toString().substring(0, 8);
		String body = "{\"username\":\"" + username + "\",\"email\":\"dup@example.com\",\"age\":18}";

		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.message").value("用户名「" + username + "」已存在"));
	}
}
