package com.zjc.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.annotation.Resource;

/**
 * {@link DemoUserController} 端到端测试：从真实 HTTP 请求出发，走完整的
 * Filter → DispatcherServlet → Controller → Service → Mapper → MySQL 链路。
 *
 * <p>
 * 与 {@code DemoUserMapperIntegrationTest} 的分工：那边验证「SQL 在 MySQL 上对不对」，
 * 这里验证「HTTP 语义对不对」——状态码是不是 {@code code}、参数校验失败是不是 400、
 * 资源不存在是不是 404。这些只有 DispatcherServlet 参与时才成立。
 *
 * <p>
 * 数据隔离同 Mapper 测试：类上 {@link Transactional}，用例结束自动回滚。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoUserControllerIntegrationTest {

	/**
	 * 用户接口根路径。
	 */
	private static final String USERS = "/api/users";

	/**
	 * 走完整 MVC 链路的 MockMvc。
	 */
	@Resource
	private MockMvc mockMvc;

	/**
	 * 通过真实 HTTP 请求新增一个用户。
	 *
	 * @param username 用户名
	 * @return 新记录主键（JSON 里是字符串，JS 精度安全的那种）
	 * @throws Exception MVC 调用失败
	 */
	private String createUser(String username) throws Exception {
		MvcResult result = mockMvc
				.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"" + username + "\",\"email\":\"user@example.com\",\"age\":18}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();

		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(body, "$.data");
	}

	/**
	 * 生成一个带随机后缀的用户名，避开唯一索引。
	 *
	 * @param prefix 前缀
	 * @return 全局唯一的用户名
	 */
	private static String randomUsername(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	/**
	 * 新增成功，返回的主键是可解析的数字，且以字符串形式返回（避免 JS 精度截断）。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("POST /api/users：返回雪花 ID（字符串形式）")
	void createReturnsSnowflakeIdAsString() throws Exception {
		String id = createUser(randomUsername("create"));

		assertThat(id).isNotBlank();
		assertThat(Long.parseLong(id)).isPositive();
	}

	/**
	 * 用户名缺失时由参数校验拦下，返回 400 且不进入业务代码。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("POST /api/users：用户名为空返回 400")
	void blankUsernameReturns400() throws Exception {
		mockMvc.perform(
				post(USERS).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\" \",\"age\":18}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value(400));
	}

	/**
	 * 详情接口返回落库后的字段，含乐观锁版本号与逻辑删除标记。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("GET /api/users/{id}：返回完整字段")
	void detailReturnsStoredFields() throws Exception {
		String username = randomUsername("detail");
		String id = createUser(username);

		mockMvc.perform(get(USERS + "/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value(username))
				.andExpect(jsonPath("$.data.email").value("user@example.com"))
				.andExpect(jsonPath("$.data.age").value(18)).andExpect(jsonPath("$.data.version").value(0))
				.andExpect(jsonPath("$.data.deleted").value(0))
				.andExpect(jsonPath("$.data.createTime").isNotEmpty());
	}

	/**
	 * 不存在的资源必须返回 404，而不是 200 + 空数据。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("GET /api/users/{id}：不存在时返回 404")
	void unknownIdReturns404() throws Exception {
		mockMvc.perform(get(USERS + "/{id}", "999999999999999999")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(404));
	}

	/**
	 * 分页查询：{@code total} 是过滤后的总条数，{@code records} 只有当前页。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("GET /api/users：按用户名过滤并分页")
	void pageFiltersByUsername() throws Exception {
		String prefix = randomUsername("page");
		createUser(prefix + "-a");
		createUser(prefix + "-b");
		createUser(prefix + "-c");

		mockMvc.perform(get(USERS).param("current", "1").param("size", "2").param("username", prefix))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
				.andExpect(jsonPath("$.data.pages").value(2)).andExpect(jsonPath("$.data.current").value(1))
				.andExpect(jsonPath("$.data.size").value(2)).andExpect(jsonPath("$.data.records.length()").value(2));
	}

	/**
	 * 页码从 1 开始，传 0 由方法参数校验拦成 400。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("GET /api/users：页码为 0 返回 400")
	void zeroPageNumberReturns400() throws Exception {
		mockMvc.perform(get(USERS).param("current", "0")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	/**
	 * 修改成功：用户名更新，乐观锁版本号自增。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("PUT /api/users/{id}：更新成功且 version 自增")
	void updateBumpsVersion() throws Exception {
		String id = createUser(randomUsername("update"));

		mockMvc.perform(put(USERS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"update-renamed\",\"age\":20}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(true));

		mockMvc.perform(get(USERS + "/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value("update-renamed"))
				.andExpect(jsonPath("$.data.age").value(20)).andExpect(jsonPath("$.data.version").value(1));
	}

	/**
	 * 修改不存在的资源返回 404，而不是静默返回 false。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("PUT /api/users/{id}：不存在时返回 404")
	void updateUnknownIdReturns404() throws Exception {
		mockMvc.perform(put(USERS + "/{id}", "999999999999999999").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"nobody\"}")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(404));
	}

	/**
	 * 自定义 SQL 查询接口：关键字过滤生效，且分页由插件改写手写 SQL。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("GET /api/users/search：自定义 SQL 的条件与分页")
	void searchUsesCustomSql() throws Exception {
		String prefix = randomUsername("search");
		createUser(prefix + "-a");
		createUser(prefix + "-b");
		createUser(prefix + "-c");

		mockMvc.perform(get(USERS + "/search").param("current", "1").param("size", "2").param("keyword", prefix))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.records.length()").value(2));
	}

	/**
	 * 自定义 SQL 聚合接口：返回年龄段与人数。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("GET /api/users/age-groups：自定义 SQL 聚合统计")
	void ageGroupsUsesCustomSql() throws Exception {
		createUser(randomUsername("agegroup"));

		// userCount 是 Long，被全局的 Long → String 规则序列化成字符串（如 "3"），不是数字
		mockMvc.perform(get(USERS + "/age-groups")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data[0].ageGroup").isNotEmpty())
				.andExpect(jsonPath("$.data[0].userCount").isNotEmpty());
	}

	/**
	 * 删除后详情接口返回 404：逻辑删除对查询侧生效。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("DELETE /api/users/{id}：删除后详情返回 404")
	void deleteThenDetailReturns404() throws Exception {
		String id = createUser(randomUsername("delete"));

		mockMvc.perform(delete(USERS + "/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(true));

		mockMvc.perform(get(USERS + "/{id}", id)).andExpect(status().isNotFound());
	}

	/**
	 * 删除不存在的资源返回 404。
	 *
	 * @throws Exception MVC 调用失败
	 */
	@Test
	@DisplayName("DELETE /api/users/{id}：不存在时返回 404")
	void deleteUnknownIdReturns404() throws Exception {
		mockMvc.perform(delete(USERS + "/{id}", "999999999999999999")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(404));
	}
}
