package com.zjc.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;
import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.constant.TraceConstant;

/**
 * 端到端 MVC 集成测试：从真实 HTTP 请求出发，验证统一响应、异常处理、traceId 链路与跨域。
 *
 * <p>
 * <b>为什么必须有这一层：</b>单元测试只能证明「处理器拿到异常后返回什么」，证明不了
 * 「异常会不会被路由到这个处理器」。而本项目所有关键约定——{@code code} 即 HTTP 状态码、 响应体统一结构、traceId
 * 自动填充——只有在 DispatcherServlet 参与时才真正成立。 示例：Spring 6.1 起 {@code @RequestParam}
 * 上的约束注解抛的是 {@code HandlerMethodValidationException}，它<b>不会</b>进入本项目的任何
 * {@code @ExceptionHandler}，只有真实请求才能暴露这一点。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

	/**
	 * 32 位十六进制 traceId 的格式校验。
	 */
	private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

	/**
	 * 走完整 MVC 链路的 MockMvc，已挂载 {@code TraceIdFilter} 等全部 Filter Bean。
	 */
	@Autowired
	private MockMvc mockMvc;

	/**
	 * Boot 自动配置的 {@code RestClient.Builder}（原型作用域），用于验证出站链路透传。
	 */
	@Autowired
	private RestClient.Builder restClientBuilder;

	/**
	 * 从响应体中读取指定 JSON 字段。
	 *
	 * @param result MVC 执行结果
	 * @param path   JSON 路径，例如 {@code $.traceId}
	 * @return 字段值的字符串形式
	 * @throws Exception 结果读取失败
	 */
	private static String bodyField(MvcResult result, String path) throws Exception {
		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		Object value = JsonPath.read(body, path);
		return value == null ? null : value.toString();
	}

	/**
	 * 正常接口与统一响应结构。
	 */
	@Nested
	@DisplayName("正常响应与 traceId 链路")
	class NormalResponse {

		/**
		 * {@code GET /hello} 必须返回统一结构的 200 响应，并自动带上 traceId。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /hello：统一结构 + 自动生成 traceId")
		void helloReturnsUnifiedResponse() throws Exception {
			MvcResult result = mockMvc.perform(get("/hello")).andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.code").value(200))
					.andExpect(jsonPath("$.message").value("操作成功"))
					.andExpect(jsonPath("$.data").value("hello SpringVortexDemo!")).andReturn();

			String traceId = bodyField(result, "$.traceId");
			assertThat(traceId).matches(TRACE_ID_PATTERN);
			assertThat(result.getResponse().getHeader(TraceConstant.HEADER_NAME)).isEqualTo(traceId);
		}

		/**
		 * 上游通过请求头下发的 traceId 必须被沿用，且响应体与响应头保持一致。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("上游 traceId 被沿用并与响应体一致")
		void upstreamTraceIdIsReused() throws Exception {
			MvcResult result = mockMvc
					.perform(get("/hello").header(TraceConstant.REQUEST_HEADER_NAME, "upstream-abc-123"))
					.andExpect(status().isOk()).andReturn();

			assertThat(bodyField(result, "$.traceId")).isEqualTo("upstream-abc-123");
			assertThat(result.getResponse().getHeader(TraceConstant.HEADER_NAME)).isEqualTo("upstream-abc-123");
		}

		/**
		 * 超出 JS 安全整数范围的 Long 必须序列化为字符串。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("Long 序列化为字符串，规避 JS 精度截断")
		void longIsSerializedAsString() throws Exception {
			mockMvc.perform(get("/test/long-value")).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value("1234567890123456789"));
		}

		/**
		 * 超长返回值不会影响接口本身，切面只做日志截断。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("超长返回值：接口正常返回，切面日志截断")
		void bigResultStillReturnsOk() throws Exception {
			mockMvc.perform(get("/test/big-result")).andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true));
		}

		/**
		 * 静态资源 favicon 被正常提供，不会产生 404 告警。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /favicon.ico：静态资源正常返回")
		void faviconIsServed() throws Exception {
			mockMvc.perform(get("/favicon.ico")).andExpect(status().isOk());
		}
	}

	/**
	 * 浏览器与 iOS 自动请求、但服务端没有的静态资源，统一走 404。
	 */
	@Nested
	@DisplayName("静态资源 404")
	class StaticResource {

		/**
		 * iOS 主屏图标不存在时返回 404，且不产生 WARN 日志。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("apple-touch-icon：404 且不告警")
		void appleTouchIconReturns404() throws Exception {
			mockMvc.perform(get("/apple-touch-icon.png")).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value(404));
		}

		/**
		 * 任意未知路径也返回统一结构的 404。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("未知路径：统一结构 404")
		void unknownPathReturns404() throws Exception {
			mockMvc.perform(get("/nope")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404))
					.andExpect(jsonPath("$.success").value(false));
		}
	}

	/**
	 * 各类客户端错误都必须返回正确的 HTTP 状态码（而不是一律 200）。
	 */
	@Nested
	@DisplayName("客户端错误：状态码如实透出")
	class ClientErrors {

		/**
		 * 请求方法不支持时返回 405。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("POST /hello：405")
		void methodNotAllowedReturns405() throws Exception {
			mockMvc.perform(post("/hello")).andExpect(status().isMethodNotAllowed())
					.andExpect(jsonPath("$.code").value(405));
		}

		/**
		 * 缺少必填查询参数时返回 400 并指出参数名。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("缺少必填参数：400")
		void missingParameterReturns400() throws Exception {
			mockMvc.perform(get("/test/param")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.message").value("缺少必填参数: name"));
		}

		/**
		 * 请求体校验失败时返回 400 并聚合字段级提示。
		 *
		 * <p>
		 * 不校验多条提示的拼接顺序：Spring 不保证 {@code getFieldErrors()} 的顺序， 断言顺序会让用例变得脆弱。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("请求体校验失败：400 且带字段提示")
		void beanValidationFailureReturns400() throws Exception {
			MvcResult result = mockMvc
					.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"\",\"age\":0}"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400)).andReturn();

			assertThat(bodyField(result, "$.message")).contains("name: 名称不能为空").contains("age: 年龄必须大于 0");
		}

		/**
		 * JSON 语法错误时返回 400，且提示信息来自解析异常。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("JSON 语法错误：400")
		void malformedJsonReturns400() throws Exception {
			mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
		}

		/**
		 * Content-Type 不支持时返回 415。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("Content-Type 不支持：415")
		void unsupportedMediaTypeReturns415() throws Exception {
			mockMvc.perform(post("/test/validate").contentType(MediaType.TEXT_PLAIN).content("not-json"))
					.andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
		}

		/**
		 * 业务异常携带的合法状态码必须如实透出（枚举构造 → 409）。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("业务异常：合法码如实透出（409）")
		void businessExceptionCarriesStatus() throws Exception {
			mockMvc.perform(get("/test/business-enum")).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.message").value("数据冲突"))
					.andExpect(jsonPath("$.success").value(false));
		}

		/**
		 * 业务异常携带自定义状态码与文案。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("业务异常：自定义码与文案透出")
		void businessExceptionWithCustomCode() throws Exception {
			mockMvc.perform(get("/test/business").param("code", "403").param("message", "无权访问该资源"))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
					.andExpect(jsonPath("$.message").value("无权访问该资源"));
		}

		/**
		 * 非 HTTP 语义的业务码兜底为 500，避免把非法状态码写进 HTTP 响应行。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("业务异常：非法码兜底为 500")
		void illegalBusinessCodeFallsBackTo500() throws Exception {
			mockMvc.perform(get("/test/business").param("code", "10001").param("message", "非法码"))
					.andExpect(status().isInternalServerError());
		}
	}

	/**
	 * 服务端错误与信息泄露防护。
	 */
	@Nested
	@DisplayName("服务端错误")
	class ServerErrors {

		/**
		 * 未预期异常返回 500，且响应体不能包含原始异常信息（防信息泄露）。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("未预期异常：500 且不泄露异常细节")
		void unexpectedExceptionDoesNotLeakDetails() throws Exception {
			MvcResult result = mockMvc.perform(get("/test/boom")).andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.code").value(500))
					.andExpect(jsonPath("$.message").value(ApiResponseConstant.INTERNAL_ERROR.message())).andReturn();

			String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
			assertThat(body).doesNotContain("模拟未预期异常").doesNotContain("IllegalStateException");
		}

		/**
		 * 500 响应同样要带 traceId，方便用户报障时直接用该 ID 捞日志。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("500 响应仍带 traceId")
		void serverErrorStillCarriesTraceId() throws Exception {
			MvcResult result = mockMvc.perform(get("/test/boom")).andReturn();

			assertThat(bodyField(result, "$.traceId")).matches(TRACE_ID_PATTERN);
		}
	}

	/**
	 * 跨域配置必须真正生效，否则前端联调会被浏览器拦截。
	 */
	@Nested
	@DisplayName("跨域 CORS")
	class Cors {

		/**
		 * 预检请求返回放行头，且暴露 {@code X-Trace-Id} 供前端读取。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("预检请求：放行来源 + 暴露 X-Trace-Id")
		void preflightIsAllowed() throws Exception {
			MvcResult result = mockMvc
					.perform(options("/hello").header(HttpHeaders.ORIGIN, "https://frontend.example.com")
							.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
					.andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
					.andReturn();

			assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
					.contains(TraceConstant.HEADER_NAME);
		}
	}

	/**
	 * 运维端点：健康检查与探针。
	 */
	@Nested
	@DisplayName("Actuator")
	class Actuator {

		/**
		 * 健康检查端点可用，供 K8s 探针与负载均衡使用。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /actuator/health：UP")
		void healthEndpointIsUp() throws Exception {
			mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("UP"));
		}
	}

	/**
	 * 异步线程的链路透传：{@code @Async} 曾是该模板唯一的链路断点。
	 */
	@Nested
	@DisplayName("异步线程链路透传")
	class AsyncTracePropagation {

		/**
		 * {@code @Async} 线程里读到的 traceId 必须与发起请求的 traceId 一致。
		 *
		 * <p>
		 * 这条用例是 P1「MDC 透传自动化」的验收标准：在此之前 {@code @Async} 线程的日志 只能打出缺省的
		 * {@code -}，无法与触发它的请求关联。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("@Async 线程 traceId 与请求一致")
		void asyncThreadSeesSameTraceId() throws Exception {
			MvcResult result = mockMvc.perform(get("/test/async-trace")).andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true)).andReturn();

			assertThat(bodyField(result, "$.data")).isEqualTo(bodyField(result, "$.traceId"));
		}
	}

	/**
	 * 出站链路透传：验证 {@code RestClientCustomizer} 真的把拦截器挂到了 Boot 自动配置的 builder 上。
	 *
	 * <p>
	 * 用 {@code MockRestServiceServer} 绑定真实 builder 并断言请求头，而不是只断言 「customizer Bean
	 * 存在」——后者证明不了拦截器真的被执行。
	 */
	@Nested
	@DisplayName("出站调用链路透传")
	class OutgoingTracePropagation {

		/**
		 * Boot 自动配置的 builder 发出的请求必须携带当前 MDC 里的 traceId。
		 */
		@Test
		@DisplayName("RestClient 出站请求自动带上 X-Trace-Id")
		void restClientPropagatesTraceId() {
			MDC.put(TraceConstant.MDC_KEY, "4f3c2b1a7e9d4c2b8f6a1d0e5c3b7a92");
			try {
				MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
				RestClient client = restClientBuilder.baseUrl("http://downstream-service").build();

				server.expect(requestTo("http://downstream-service/api/users")).andExpect(method(HttpMethod.GET))
						// 限定名调用：与 MockMvcResultMatchers.header 静态导入重名
						.andExpect(MockRestRequestMatchers.header(TraceConstant.HEADER_NAME,
								"4f3c2b1a7e9d4c2b8f6a1d0e5c3b7a92"))
						.andRespond(withSuccess());

				client.get().uri("/api/users").retrieve().toBodilessEntity();
				server.verify();
			} finally {
				MDC.clear();
			}
		}
	}

	/**
	 * 接口文档：验证 springdoc 与 Spring Boot 4 的版本搭配真的能用。
	 *
	 * <p>
	 * 版本选错（比如用 springdoc 2.x 配 Boot 4）不会编译报错，只在运行期表现为 文档接口 500 或
	 * ClassNotFound，因此必须有运行期验收。
	 */
	@Nested
	@DisplayName("接口文档 OpenAPI")
	class OpenApi {

		/**
		 * OpenAPI 描述文档可访问，且取自 {@code OpenApiConfig} 的元信息。
		 *
		 * <p>
		 * 顺带断言加解密接口出现在总览里。注意「总览里有」<b>不等于</b>「Swagger UI 上看得见」—— 后者取决于有没有分组，见
		 * {@link #jasyptGroupIsScoped()}。两条一起看才完整。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /v3/api-docs：返回 OpenAPI 描述")
		void apiDocsIsAvailable() throws Exception {
			mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.openapi").exists())
					.andExpect(jsonPath("$.info.title").value("SpringVortexDemo API"))
					.andExpect(jsonPath("$.info.version").value("0.0.1"))
					.andExpect(jsonPath("$.paths['/api/jasypt/encrypt']").exists());
		}

		/**
		 * Swagger UI 页面可访问。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /swagger-ui/index.html：页面可访问")
		void swaggerUiIsAvailable() throws Exception {
			mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		}

		/**
		 * 示例接口分组只包含示例路径，且共享服务级元信息。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /v3/api-docs/demo：只含示例接口")
		void demoGroupIsScoped() throws Exception {
			mockMvc.perform(get("/v3/api-docs/demo")).andExpect(status().isOk())
					.andExpect(jsonPath("$.info.title").value("SpringVortexDemo API"))
					.andExpect(jsonPath("$.paths['/hello']").exists())
					.andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
		}

		/**
		 * 运维端点分组只包含 Actuator 路径，与示例分组互不串味。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /v3/api-docs/actuator：只含运维端点")
		void actuatorGroupIsScoped() throws Exception {
			mockMvc.perform(get("/v3/api-docs/actuator")).andExpect(status().isOk())
					.andExpect(jsonPath("$.paths['/actuator/health']").exists())
					.andExpect(jsonPath("$.paths['/hello']").doesNotExist());
		}

		/**
		 * 加解密接口必须有独立分组，否则 Swagger UI 的下拉框里翻不到它。
		 *
		 * <p>
		 * <b>这条用例在防什么：</b>Swagger UI 的下拉框只列分组、不列总览页。新写一个 Controller 却忘了在
		 * {@code OpenApiConfig} 里加分组时，接口在 {@code /v3/api-docs} 总览里看得到、 在 UI
		 * 页面上却完全找不到——只断言「总览里有」是抓不住这个问题的。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("GET /v3/api-docs/jasypt：分组存在且只含加解密接口")
		void jasyptGroupIsScoped() throws Exception {
			mockMvc.perform(get("/v3/api-docs/jasypt")).andExpect(status().isOk())
					.andExpect(jsonPath("$.info.title").value("SpringVortexDemo API"))
					.andExpect(jsonPath("$.paths['/api/jasypt/encrypt']").exists())
					.andExpect(jsonPath("$.paths['/api/jasypt/decrypt']").exists())
					.andExpect(jsonPath("$.paths['/hello']").doesNotExist());
		}
	}

	/**
	 * Spring 6.1+ 的方法参数校验走向。
	 *
	 * <p>
	 * {@code @RequestParam} 上直接挂约束注解时抛的是 {@code HandlerMethodValidationException}，
	 * 而<b>不是</b> {@code ConstraintViolationException}。本用例是这条链路唯一的守卫：
	 * 少处理这一个异常，客户端传错参数就会得到 500 + ERROR 堆栈，而不是 400。
	 */
	@Nested
	@DisplayName("方法参数校验（Spring 6.1+ 行为）")
	class MethodParameterValidation {

		/**
		 * 参数上直接挂约束注解时，必须返回统一结构的 400，而不是 500。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("空参数：400 且提示参数名")
		void blankRequestParamReturns400() throws Exception {
			mockMvc.perform(get("/test/validated-param").param("name", " ")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.message").value("name: 名称不能为空"))
					.andExpect(jsonPath("$.success").value(false));
		}
	}
}
