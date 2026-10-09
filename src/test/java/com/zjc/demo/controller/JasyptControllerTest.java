package com.zjc.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.zjc.demo.service.JasyptService;

/**
 * {@link JasyptController} 的端到端接口测试。
 *
 * <p>
 * 走真实 MVC 链路而不是直接调 Controller 方法，是为了同时验证三件事：参数校验注解是否真的生效、
 * 异常是否被路由到正确的处理器、响应结构是否符合统一约定。单元测试证明不了这些。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@AutoConfigureMockMvc
class JasyptControllerTest {

	/**
	 * 走完整 MVC 链路的 MockMvc。
	 */
	@Autowired
	private MockMvc mockMvc;

	/**
	 * 用真实的加解密服务反解接口返回的密文，验证接口产出的是「能用的密文」而不只是「一串看起来像密文的字符串」。
	 *
	 * <p>
	 * 注入 Service 而不是任何工具类：接口与 Service 共用同一套 {@code jasypt.encryptor.*} 配置，
	 * 若接口侧被换成了另一个加密器 Bean（或有人手工 new 了一个），这条用例会立刻变红。
	 */
	@Autowired
	private JasyptService jasyptService;

	/**
	 * 加密接口。
	 */
	@Nested
	@DisplayName("GET /api/jasypt/encrypt")
	class Encrypt {

		/**
		 * 接口返回的密文必须是可解密的——用本项目的加密器反解回来等于原文。
		 *
		 * <p>
		 * 只断言「返回值不等于明文」是不够的：接口完全可以返回一串随机字符串而依然通过。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("返回可解密的密文，且响应结构符合统一约定")
		void returnsDecryptableCipherText() throws Exception {
			String body = mockMvc.perform(get("/api/jasypt/encrypt").param("plainText", "my-db-password"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.message").value("操作成功"))
					.andExpect(jsonPath("$.traceId").exists()).andReturn().getResponse()
					.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

			String cipherText = JsonPath.read(body, "$.data");
			assertThat(cipherText).isNotEqualTo("my-db-password");
			assertThat(jasyptService.decrypt(cipherText)).isEqualTo("my-db-password");
		}

		/**
		 * 明文为空串时必须返回 400，而不是把空值丢给加密器、最终变成 500。
		 *
		 * <p>
		 * {@code @NotBlank} 挂在 {@code @RequestParam} 上，抛的是
		 * {@code HandlerMethodValidationException} （Spring 6.1+ 行为），这条用例同时守卫了它的处理器还在。
		 *
		 * <p>
		 * ⚠️ 提示里的参数名是<b>方法名限定的</b> {@code encrypt.plainText}，不是单纯的 {@code plainText}。
		 * 实测如此：类上带 {@code @Validated} 时 Controller 被 AOP 代理，{@code getParameterName()}
		 * 给出的是限定名。去掉类上那个注解就会变回裸参数名——两种写法校验都会触发，差别只在文案。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("明文为空白：400 且带参数提示")
		void blankPlainTextReturns400() throws Exception {
			mockMvc.perform(get("/api/jasypt/encrypt").param("plainText", " ")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.message").value("encrypt.plainText: 明文不能为空"));
		}

		/**
		 * 完全不带参数时返回 400，提示缺少参数名。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("缺少 plainText：400 且提示参数名")
		void missingPlainTextReturns400() throws Exception {
			mockMvc.perform(get("/api/jasypt/encrypt")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400))
					.andExpect(jsonPath("$.message").value("缺少必填参数: plainText"));
		}
	}

	/**
	 * 解密接口。
	 */
	@Nested
	@DisplayName("GET /api/jasypt/decrypt")
	class Decrypt {

		/**
		 * 用加密接口产出的密文调解密接口，必须拿回原文——即两个接口共享同一套密钥与算法配置。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("encrypt 的产出能被 decrypt 解回原文")
		void roundTripThroughHttp() throws Exception {
			String plainText = "分账密钥@2026";
			String encryptedBody = mockMvc.perform(get("/api/jasypt/encrypt").param("plainText", plainText))
					.andExpect(status().isOk()).andReturn().getResponse()
					.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
			String cipherText = JsonPath.read(encryptedBody, "$.data");

			mockMvc.perform(get("/api/jasypt/decrypt").param("cipherText", cipherText)).andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").value(plainText));
		}

		/**
		 * 密文为空串时返回 400。
		 *
		 * <p>
		 * 同 {@code encrypt}：提示里的参数名是方法级限定的 {@code decrypt.cipherText}。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("密文为空白：400 且带参数提示")
		void blankCipherTextReturns400() throws Exception {
			mockMvc.perform(get("/api/jasypt/decrypt").param("cipherText", " ")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400))
					.andExpect(jsonPath("$.message").value("decrypt.cipherText: 密文不能为空"));
		}

		/**
		 * 缺省参数时返回 400。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("缺少 cipherText：400 且提示参数名")
		void missingCipherTextReturns400() throws Exception {
			mockMvc.perform(get("/api/jasypt/decrypt")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400))
					.andExpect(jsonPath("$.message").value("缺少必填参数: cipherText"));
		}

		/**
		 * 传入不是密文的字符串时返回 500，且响应体不泄露 jasypt 的异常细节。
		 *
		 * <p>
		 * 这是刻意记录的行为：密钥不匹配、密文被截断都属于「服务端密钥配置出了问题」， 不是调用方参数错误，因此走 500 而不是 400。响应体只需给出通用提示，
		 * 具体原因（含密文本身）留在服务端日志里。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("非法密文：500 且不泄露异常细节")
		void invalidCipherTextReturns500WithoutLeak() throws Exception {
			String body = mockMvc.perform(get("/api/jasypt/decrypt").param("cipherText", "not-a-cipher-text"))
					.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500)).andReturn()
					.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

			assertThat(body).doesNotContain("EncryptionOperationNotPossibleException")
					.doesNotContain("DecryptionException");
		}
	}

	/**
	 * 配置项透明解密接口。
	 */
	@Nested
	@DisplayName("GET /api/jasypt/demo")
	class Demo {

		/**
		 * {@code application.yaml} 里的 {@code demo} 写的是 {@code ENC(密文)}， 接口要能把它解成明文吐出来。
		 *
		 * <p>
		 * <b>这条用例在防什么：</b>{@code ENC(...)} 的透明解密发生在 {@code Environment} 层， 配置漏了
		 * {@code include: jasypt}（或在错误的地方改了算法）时，注入到的就是那一串 {@code ENC(...)} 原文——接口照样
		 * 200，只是返回的东西不对。断言「响应里不带 ENC(」 正是为了抓这种静默失效。
		 *
		 * <p>
		 * 不断言具体明文：密文是人工生成的，明文由配置者决定，写死在测试里会让改一次配置就得改测试。
		 *
		 * @throws Exception MVC 调用失败
		 */
		@Test
		@DisplayName("返回解密后的配置值，而不是 ENC(...) 原文")
		void returnsDecryptedConfigValue() throws Exception {
			String body = mockMvc.perform(get("/api/jasypt/demo")).andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.code").value(200))
					.andExpect(jsonPath("$.data").isNotEmpty()).andReturn().getResponse()
					.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

			assertThat(body).doesNotContain("ENC(");
		}
	}

}
