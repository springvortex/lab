package com.zjc.demo.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ApiResponseConstant} 的单元测试。
 *
 * <p>
 * 核心是守住「枚举的 {@code code} 必须是合法 HTTP 状态码」这条约定：一旦有人加了 {@code 10001}
 * 这类编码，本用例会立刻失败，而不是等到线上响应变成 500 才发现。
 *
 * @author jiancai.zhong
 */
class ApiResponseConstantTest {

	/**
	 * 所有枚举项的状态码都必须落在 100~599，且文案非空。
	 */
	@Test
	@DisplayName("所有枚举项：状态码在 100~599、文案非空")
	void allCodesAreValidHttpStatusCodes() {
		for (ApiResponseConstant value : ApiResponseConstant.values()) {
			assertThat(value.code()).as("枚举 %s 的状态码必须是合法 HTTP 状态码", value.name()).isBetween(100, 599);
			assertThat(value.message()).as("枚举 %s 的文案不能为空", value.name()).isNotBlank();
		}
	}

	/**
	 * 抽查关键枚举项的具体取值，防止无意改动破坏前后端约定。
	 */
	@Test
	@DisplayName("关键枚举项取值符合约定")
	void keyCodesMatchConvention() {
		assertThat(ApiResponseConstant.SUCCESS.code()).isEqualTo(200);
		assertThat(ApiResponseConstant.SUCCESS.message()).isEqualTo("操作成功");
		assertThat(ApiResponseConstant.FAILURE.code()).isEqualTo(400);
		assertThat(ApiResponseConstant.PARAM_INVALID.code()).isEqualTo(400);
		assertThat(ApiResponseConstant.BAD_REQUEST.code()).isEqualTo(400);
		assertThat(ApiResponseConstant.UNAUTHORIZED.code()).isEqualTo(401);
		assertThat(ApiResponseConstant.FORBIDDEN.code()).isEqualTo(403);
		assertThat(ApiResponseConstant.NOT_FOUND.code()).isEqualTo(404);
		assertThat(ApiResponseConstant.METHOD_NOT_ALLOWED.code()).isEqualTo(405);
		assertThat(ApiResponseConstant.REQUEST_TIMEOUT.code()).isEqualTo(408);
		assertThat(ApiResponseConstant.CONFLICT.code()).isEqualTo(409);
		assertThat(ApiResponseConstant.UNSUPPORTED_MEDIA_TYPE.code()).isEqualTo(415);
		assertThat(ApiResponseConstant.INTERNAL_ERROR.code()).isEqualTo(500);
		assertThat(ApiResponseConstant.SERVICE_UNAVAILABLE.code()).isEqualTo(503);
	}

	/**
	 * 通过 {@link ErrorCodeConstant} 接口访问，验证契约接口可用（业务方以接口类型接收枚举的场景）。
	 */
	@Test
	@DisplayName("以 ErrorCodeConstant 接口类型访问同样生效")
	void accessibleThroughErrorCodeConstantInterface() {
		ErrorCodeConstant errorCode = ApiResponseConstant.CONFLICT;

		assertThat(errorCode.code()).isEqualTo(409);
		assertThat(errorCode.message()).isEqualTo("数据冲突");
	}

	/**
	 * 每个枚举项都必须带国际化 key，且用「模块.语义」命名。
	 *
	 * <p>
	 * 漏配 key 不会报错，只会让该响应码在所有语言下都用固定中文兜底文案——即国际化对它失效。
	 * 这条断言把「新增枚举忘了加 key」拦在构建期。
	 *
	 * <p>
	 * key 的实际取词由 {@code MessageUtilsTest#everyEnumMessageKeyResolves} 校验：
	 * 本类只保证「枚举侧声明了」，那边保证「资源文件里真的有」。
	 */
	@Test
	@DisplayName("所有枚举项都带 response.* 格式的国际化 key")
	void allEntriesDeclareMessageKey() {
		for (ApiResponseConstant value : ApiResponseConstant.values()) {
			assertThat(value.messageKey()).as("枚举 %s 缺少国际化 key", value.name()).isNotBlank()
					.startsWith("response.");
		}
	}

	/**
	 * 契约接口的 {@code messageKey()} 默认返回 {@code null}，表示「该枚举不使用国际化」。
	 *
	 * <p>
	 * <b>这条默认实现是「旧自定义枚举零改动兼容」的关键：</b>业务方现有枚举不覆写该方法时，
	 * 框架会回落到 {@code message()} 的固定文案，行为与引入国际化之前完全一致。
	 * 若有人把默认值改成 {@code code()} 的字符串形式，资源文件里就得写 {@code 404=资源不存在}
	 * 这类键，而多个枚举共用一个状态码时会互相覆盖（400 就有三项），语义直接串掉。
	 */
	@Test
	@DisplayName("契约接口的 messageKey 默认返回 null")
	void defaultMessageKeyIsNull() {
		ErrorCodeConstant withoutI18n = new ErrorCodeConstant() {
			@Override
			public int code() {
				return 400;
			}

			@Override
			public String message() {
				return "未启用国际化的提示";
			}
		};

		assertThat(withoutI18n.messageKey()).as("默认实现必须返回 null（不使用国际化）").isNull();
	}
}
