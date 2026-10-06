package com.zjc.demo.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.constant.TraceConstant;
import com.zjc.demo.support.TestErrorCodeConstant;

/**
 * {@link ApiResponse} 静态工厂方法与状态码解析的单元测试。
 *
 * <p>
 * 重点覆盖「{@code code} 即 HTTP 状态码」这条核心约定的边界： {@code null}、合法值、非法值三种输入必须分别得到
 * 500、原值、500。
 *
 * @author jiancai.zhong
 */
class ApiResponseTest {

	/**
	 * 每个用例结束后清理 MDC，避免 traceId 串到其他用例。
	 */
	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	/**
	 * 验证默认构造出的响应对象：成功标志为 {@code null}、状态码与文案取成功默认值。
	 */
	@Test
	@DisplayName("默认构造：code=200、message=操作成功、success 为空")
	void defaultConstructorUsesSuccessDefaults() {
		ApiResponse<String> response = new ApiResponse<>();

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("操作成功");
		assertThat(response.getData()).isNull();
		assertThat(response.getTimestamp()).isNotNull();
		assertThat(response.isSuccess()).isFalse();
	}

	/**
	 * 覆盖 traceId 的自动填充：MDC 有值时必须原样带出，没有值时必须为 {@code null}。
	 */
	@Test
	@DisplayName("traceId 从 MDC 自动读取，无 MDC 时为 null")
	void traceIdIsReadFromMdc() {
		assertThat(new ApiResponse<Void>().getTraceId()).isNull();

		MDC.put(TraceConstant.MDC_KEY, "4f3c2b1a7e9d4c2b8f6a1d0e5c3b7a92");
		assertThat(new ApiResponse<Void>().getTraceId()).isEqualTo("4f3c2b1a7e9d4c2b8f6a1d0e5c3b7a92");
	}

	/**
	 * 验证 traceId 可被显式覆盖，供 MQ 消费等非 HTTP 场景回填上游链路 ID。
	 */
	@Test
	@DisplayName("traceId 可被 setter 显式覆盖")
	void traceIdCanBeOverridden() {
		MDC.put(TraceConstant.MDC_KEY, "from-mdc");
		ApiResponse<Void> response = new ApiResponse<>();
		response.setTraceId("from-upstream");

		assertThat(response.getTraceId()).isEqualTo("from-upstream");
	}

	/**
	 * 验证 {@code toResponseEntity()} 把 {@code code} 透出为真实 HTTP 状态码，并保留响应体。
	 */
	@Test
	@DisplayName("toResponseEntity：HTTP 状态码与 code 一致、响应体为自身")
	void toResponseEntityCarriesStatusAndBody() {
		ApiResponse<Void> failure = ApiResponse.<Void>failure(ApiResponseConstant.NOT_FOUND);
		ResponseEntity<ApiResponse<Void>> entity = failure.toResponseEntity();

		assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(entity.getBody()).isSameAs(failure);

		ResponseEntity<ApiResponse<String>> success = ApiResponse.success("ok").toResponseEntity();
		assertThat(success.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(success.getBody()).isNotNull();
		assertThat(success.getBody().getData()).isEqualTo("ok");
	}

	/**
	 * 验证 {@code isSuccess()} 对 {@code null} 的防御：不能因为拆箱而抛 NPE。
	 */
	@Test
	@DisplayName("isSuccess：null 视为失败、false 视为失败、true 视为成功")
	void isSuccessHandlesNullAndBoolean() {
		ApiResponse<Void> response = new ApiResponse<>();
		assertThat(response.isSuccess()).isFalse();

		response.setSuccess(false);
		assertThat(response.isSuccess()).isFalse();

		response.setSuccess(true);
		assertThat(response.isSuccess()).isTrue();
	}

	/**
	 * 验证四个 success 工厂方法各自的字段填充。
	 */
	@Nested
	@DisplayName("success 工厂方法")
	class SuccessFactories {

		/**
		 * {@code success()} 不带数据，成功标志为 true。
		 */
		@Test
		void successWithoutData() {
			ApiResponse<Void> response = ApiResponse.success();

			assertThat(response.isSuccess()).isTrue();
			assertThat(response.getCode()).isEqualTo(200);
			assertThat(response.getMessage()).isEqualTo("操作成功");
			assertThat(response.getData()).isNull();
		}

		/**
		 * {@code success(data)} 携带业务数据。
		 */
		@Test
		void successWithData() {
			ApiResponse<String> response = ApiResponse.success("payload");

			assertThat(response.isSuccess()).isTrue();
			assertThat(response.getData()).isEqualTo("payload");
		}

		/**
		 * {@code successMessage(message)} 只改文案，不带数据。
		 */
		@Test
		void successWithMessageOnly() {
			ApiResponse<Void> response = ApiResponse.successMessage("创建成功");

			assertThat(response.isSuccess()).isTrue();
			assertThat(response.getMessage()).isEqualTo("创建成功");
			assertThat(response.getData()).isNull();
		}

		/**
		 * {@code success(message, data)} 同时设置文案与数据。
		 */
		@Test
		void successWithMessageAndData() {
			ApiResponse<Integer> response = ApiResponse.success("查询成功", 42);

			assertThat(response.isSuccess()).isTrue();
			assertThat(response.getMessage()).isEqualTo("查询成功");
			assertThat(response.getData()).isEqualTo(42);
		}
	}

	/**
	 * 验证七个 failure 工厂方法各自的字段填充。
	 */
	@Nested
	@DisplayName("failure 工厂方法")
	class FailureFactories {

		/**
		 * {@code failure()} 使用默认失败码 400。
		 */
		@Test
		void failureWithoutArgs() {
			ApiResponse<Void> response = ApiResponse.failure();

			assertThat(response.isSuccess()).isFalse();
			assertThat(response.getCode()).isEqualTo(400);
			assertThat(response.getMessage()).isEqualTo("操作失败");
		}

		/**
		 * {@code failure(data)} 携带错误明细数据，状态码仍为默认 400。
		 */
		@Test
		void failureWithData() {
			ApiResponse<String> response = ApiResponse.failure("字段明细");

			assertThat(response.getCode()).isEqualTo(400);
			assertThat(response.getData()).isEqualTo("字段明细");
		}

		/**
		 * {@code failureMessage(message)} 只改文案。
		 */
		@Test
		void failureWithMessageOnly() {
			ApiResponse<Void> response = ApiResponse.failureMessage("参数不合法");

			assertThat(response.getCode()).isEqualTo(400);
			assertThat(response.getMessage()).isEqualTo("参数不合法");
		}

		/**
		 * {@code failure(code, message)} 指定状态码与文案。
		 */
		@Test
		void failureWithCodeAndMessage() {
			ApiResponse<Void> response = ApiResponse.failure(401, "未登录");

			assertThat(response.getCode()).isEqualTo(401);
			assertThat(response.getMessage()).isEqualTo("未登录");
			assertThat(response.isSuccess()).isFalse();
		}

		/**
		 * {@code failure(HttpStatus, message)} 避免手写状态码数字。
		 */
		@Test
		void failureWithHttpStatus() {
			ApiResponse<Void> response = ApiResponse.failure(HttpStatus.FORBIDDEN, "无权限");

			assertThat(response.getCode()).isEqualTo(403);
			assertThat(response.getMessage()).isEqualTo("无权限");
		}

		/**
		 * {@code failure(ApiResponseConstant)} 取枚举自带的状态码与文案。
		 */
		@Test
		void failureWithEnum() {
			// 必须带显式类型见证：否则 failure(T) 与 failure(ApiResponseConstant) 在 T 被推成
			// ApiResponseConstant 时重载等价，编译器无法判定谁更具体。
			ApiResponse<Void> response = ApiResponse.<Void>failure(ApiResponseConstant.UNSUPPORTED_MEDIA_TYPE);

			assertThat(response.getCode()).isEqualTo(415);
			assertThat(response.getMessage()).isEqualTo("不支持的请求体类型");
		}

		/**
		 * {@code failure(ErrorCodeConstant)} 支持业务自定义枚举。
		 *
		 * <p>
		 * 用测试枚举验证两条分支：覆写了 {@code messageKey()} 的（走国际化）与未覆写的
		 * （{@code messageKey()} 为 {@code null}，直接用固定文案）。
		 */
		@Test
		void failureWithCustomErrorCodeEnum() {
			ApiResponse<Void> localized = ApiResponse.<Void>failure(TestErrorCodeConstant.USER_NOT_FOUND);
			assertThat(localized.getCode()).isEqualTo(404);
			assertThat(localized.isSuccess()).isFalse();

			ApiResponse<Void> plain = ApiResponse.<Void>failure(TestErrorCodeConstant.STOCK_NOT_ENOUGH);
			assertThat(plain.getCode()).isEqualTo(409);
			assertThat(plain.getMessage()).isEqualTo("库存不足");
		}

		/**
		 * <b>无 Spring 容器时也必须给出完整文案，不能把 message key 漏到响应体里。</b>
		 *
		 * <p>
		 * 本类是不起容器的纯单元测试，{@code MessageUtils} 的静态消息源可能尚未被
		 * {@code I18nConfig} 回填。此时若不做回落，响应体的 {@code message} 会变成
		 * {@code response.not-found} 这样的标识符——调用方看不懂，排查时也容易误判成配置错误。
		 *
		 * <p>
		 * 这条用例是「回落规则」的守卫：它保证 {@code ApiResponse} 的文案正确性
		 * <b>不依赖测试执行顺序</b>（早期版本靠其他测试先起容器才能通过，单独跑本类会红）。
		 */
		@Test
		@DisplayName("无容器时回落枚举固定文案，不外泄 message key")
		void fallsBackToEnumMessageWithoutContainer() {
			ApiResponse<Void> response = ApiResponse.<Void>failure(ApiResponseConstant.NOT_FOUND);

			assertThat(response.getMessage()).isEqualTo("资源不存在").doesNotStartWith("response.");
		}

		/**
		 * 默认构造与工厂方法在无容器时同样给出完整文案。
		 */
		@Test
		@DisplayName("无容器时默认文案也不外泄 message key")
		void defaultMessageNeverLeaksKeyWithoutContainer() {
			assertThat(new ApiResponse<Void>().getMessage()).isEqualTo("操作成功").doesNotStartWith("response.");
			assertThat(ApiResponse.success().getMessage()).isEqualTo("操作成功");
			assertThat(ApiResponse.failure().getMessage()).isEqualTo("操作失败");
		}
	}

	/**
	 * 验证状态码解析的三条分支：{@code null}、合法值、非法值。
	 */
	@Nested
	@DisplayName("状态码解析与兜底")
	class StatusResolution {

		/**
		 * {@code null} 兜底为 500。
		 */
		@Test
		void nullCodeFallsBackTo500() {
			assertThat(ApiResponse.resolveStatus(null)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		/**
		 * 合法 HTTP 状态码原样解析。
		 */
		@Test
		void validCodeIsResolved() {
			assertThat(ApiResponse.resolveStatus(404)).isEqualTo(HttpStatus.NOT_FOUND);
		}

		/**
		 * 非 HTTP 语义的编码（如 10001）兜底为 500，而不是把请求打挂。
		 */
		@Test
		void illegalCodeFallsBackTo500() {
			assertThat(ApiResponse.resolveStatus(10001)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(ApiResponse.resolveStatus(0)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		/**
		 * {@code httpStatus()} 应委托给 {@code resolveStatus()}，实例侧同样要兜底。
		 */
		@Test
		void instanceHttpStatusDelegatesToResolveStatus() {
			assertThat(ApiResponse.success().httpStatus()).isEqualTo(HttpStatus.OK);

			ApiResponse<Void> response = ApiResponse.failure(10001, "非法码");
			assertThat(response.getCode()).isEqualTo(10001);
			assertThat(response.httpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
