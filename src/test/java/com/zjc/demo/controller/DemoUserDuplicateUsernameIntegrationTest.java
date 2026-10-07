package com.zjc.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjc.demo.entity.DemoUser;
import com.zjc.demo.mapper.DemoUserMapper;

import jakarta.annotation.Resource;

/**
 * 重复用户名 → 409 的端到端验证。
 *
 * <p>
 * <b>为什么必须走真实 HTTP：</b>这条链路上有三处都可能断——MyBatis-Spring 会不会把
 * SQLState {@code 23505} 翻译成 {@code DuplicateKeyException}、Controller 的
 * {@code try-catch} 接不接得住、{@code BusinessException} 会不会被全局处理器转成 409。
 * 任何一处没接上，表现都是「500 服务内部错误」。只测某一层证明不了整条链。
 *
 * <p>
 * <b>为什么不加 {@code @Transactional}：</b>PostgreSQL 里一条语句报错会让整个事务进入
 * aborted 状态，后续语句全部失败。要让「第一次插入成功、第二次冲突」各自独立提交/回滚，
 * 就不能把它们塞进同一个事务，因此这里改为在 {@code @AfterEach} 里显式清理。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoUserDuplicateUsernameIntegrationTest {

	/**
	 * 走完整 MVC 链路的 MockMvc。
	 */
	@Resource
	private MockMvc mockMvc;

	/**
	 * 用于清理本用例写入的数据。
	 */
	@Resource
	private DemoUserMapper demoUserMapper;

	/**
	 * 本用例创建的用户名。
	 */
	private String username;

	/**
	 * 清理本用例写入的记录，避免污染后续测试与手动验证时的数据。
	 */
	@AfterEach
	void cleanUp() {
		if (username != null) {
			demoUserMapper.delete(new LambdaQueryWrapper<DemoUser>().eq(DemoUser::getUsername, username));
			username = null;
		}
	}

	/**
	 * 同一个用户名插入两次，第二次必须返回 409 并指出是哪个字段的值重复。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("POST /api/users：用户名重复返回 409 且提示明确")
	void duplicateUsernameReturns409() throws Exception {
		username = "dup-" + UUID.randomUUID().toString().substring(0, 8);
		String body = "{\"username\":\"" + username + "\",\"email\":\"dup@example.com\",\"age\":18}";

		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.message").value("用户名「" + username + "」已存在"));
	}
}
