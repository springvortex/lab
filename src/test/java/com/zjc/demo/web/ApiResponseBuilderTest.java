package com.zjc.demo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.zjc.demo.constant.TraceConstant;

/**
 * {@link ApiResponse} 的 Lombok {@code @Builder} 链式构建器的单元测试。
 *
 * <p>
 * 重点是 {@code @Builder.Default} 的三条分支：{@code code} / {@code message} /
 * {@code traceId} 未被 显式设置时，必须保留字段的初始值（成功时的 200、默认文案、MDC 里的 traceId），而不是被置为
 * {@code null}。
 *
 * @author jiancai.zhong
 */
class ApiResponseBuilderTest {

	/**
	 * 什么都不设置时，{@code code} 与 {@code message} 必须回落到默认值，{@code success} 为 false。
	 */
	@Test
	@DisplayName("build：未设置 code/message 时保留默认值")
	void buildKeepsDefaultsWhenNothingSet() {
		ApiResponse<Object> response = ApiResponse.builder().build();

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("操作成功");
		assertThat(response.isSuccess()).isFalse();
		assertThat(response.getData()).isNull();
	}

	/**
	 * 只设置 code：message 仍走默认值。
	 */
	@Test
	@DisplayName("build：只设置 code 时 message 保留默认值")
	void buildWithCodeOnly() {
		ApiResponse<Object> response = ApiResponse.builder().code(201).build();

		assertThat(response.getCode()).isEqualTo(201);
		assertThat(response.getMessage()).isEqualTo("操作成功");
	}

	/**
	 * 只设置 message：code 仍走默认值。
	 */
	@Test
	@DisplayName("build：只设置 message 时 code 保留默认值")
	void buildWithMessageOnly() {
		ApiResponse<Object> response = ApiResponse.builder().message("自定义文案").build();

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("自定义文案");
	}

	/**
	 * {@code success(true)} 置成功态，随后可继续链式叠加字段；{@code code} / {@code message} 未显式设置时
	 * 取 {@code @Builder.Default} 的默认值 200 与「操作成功」。
	 */
	@Test
	@DisplayName("success(true)：成功态 + 链式追加 data，其余字段保留默认值")
	void successTrueInitializesSuccessAndAllowsChaining() {
		ApiResponse<String> response = ApiResponse.<String>builder().success(true).data("payload").build();

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("操作成功");
		assertThat(response.getData()).isEqualTo("payload");
	}

	/**
	 * {@code success(false)} 置失败态；未显式设置 {@code code} / {@code message} 时仍是「成功默认值」
	 * 200 与「操作成功」——成功标志与状态码文案彼此独立，失败语义的默认值由 {@code failure} 系列 工厂方法负责组装。
	 */
	@Test
	@DisplayName("success(false)：失败态，但 code/message 仍取 @Builder.Default")
	void successFalseKeepsBuilderDefaults() {
		ApiResponse<String> response = ApiResponse.<String>builder().success(false).build();

		assertThat(response.isSuccess()).isFalse();
		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("操作成功");
	}

	/**
	 * 全部 setter 逐个显式设置，且 {@code success(true)} 可单独控制成功标志。
	 */
	@Test
	@DisplayName("逐个 setter：success/code/message/data 全部生效")
	void allSettersAreApplied() {
		ApiResponse<Integer> response = ApiResponse.<Integer>builder().success(true).code(206).message("部分内容").data(7)
				.build();

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getCode()).isEqualTo(206);
		assertThat(response.getMessage()).isEqualTo("部分内容");
		assertThat(response.getData()).isEqualTo(7);
	}

	/**
	 * {@code code(null)} 与 {@code message(null)} 等价于未设置，必须回落到默认值而非置空。
	 *
	 * <p>
	 * 默认值由 {@code @Builder} 所标注的那个构造器手工回填（{@code code == null ? 200 : code}）， 而非
	 * {@code @Builder.Default}。原因是 {@code @Builder.Default} 只支持类级 {@code @Builder}，
	 * 而本类为了封死 {@code timestamp} 把 {@code @Builder} 挪到了构造器上。
	 */
	@Test
	@DisplayName("build：显式传 null 等价于未设置，回落默认值")
	void buildWithExplicitNullsFallsBackToDefaults() {
		ApiResponse<Object> response = ApiResponse.builder().code(null).message(null).build();

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("操作成功");
	}

	/**
	 * {@code traceId} 不在构造器参数里，因此构建器无法设置它；实例创建时从 {@link MDC} 自动读取。
	 */
	@Test
	@DisplayName("build：traceId 从 MDC 自动读取，构建器无法设置")
	void buildReadsTraceIdFromMdc() {
		MDC.put(TraceConstant.MDC_KEY, "6b1f0d2e9a4c4f0b8d3e5a7c1b2d4f60");
		try {
			ApiResponse<Object> response = ApiResponse.builder().build();
			assertThat(response.getTraceId()).isEqualTo("6b1f0d2e9a4c4f0b8d3e5a7c1b2d4f60");
		} finally {
			MDC.clear();
		}
	}

	/**
	 * 未设置 {@code traceId} 且 MDC 也为空时，{@code traceId} 为 {@code null}。
	 */
	@Test
	@DisplayName("build：无 MDC 时 traceId 为 null")
	void buildLeavesTraceIdNullWithoutMdc() {
		MDC.clear();
		ApiResponse<Object> response = ApiResponse.builder().build();

		assertThat(response.getTraceId()).isNull();
	}

	/**
	 * <b>{@code timestamp} 不可被外部覆盖</b>——三条入口逐一验证。
	 *
	 * <p>
	 * 这是本类改用 Lombok 时最需要守住的约束。封堵方案是「{@code final} + 构造器级 {@code @Builder} + 不用
	 * {@code @AllArgsConstructor}」：
	 * <ol>
	 * <li>构建器<b>没有</b> {@code timestamp(Long)} 方法（反射断言）；</li>
	 * <li>类里<b>没有</b> {@code setTimestamp}（反射断言）；</li>
	 * <li>唯一的公开构造器签名是 {@code (Boolean, Integer, String, T)}，不含时间戳；</li>
	 * <li>构建出的对象时间戳由字段初始值提供，是个非空毫秒值。</li>
	 * </ol>
	 *
	 * <p>
	 * 对照：若把 {@code @Builder} 标在类上并配 {@code @Builder.Default}，Lombok 会生成
	 * {@code timestamp(Long)} 与 {@code setTimestamp}，约束即被打破。
	 */
	@Test
	@DisplayName("timestamp 不可覆盖：无构建器方法、无 setter、构造器不含该参数")
	void timestampCannotBeOverriddenFromOutside() {
		MDC.clear();

		Set<String> builderMethodNames = Arrays.stream(ApiResponse.ApiResponseBuilder.class.getMethods())
				.map(Method::getName).collect(Collectors.toSet());
		assertThat(builderMethodNames).doesNotContain("timestamp");

		Set<String> instanceMethodNames = Arrays.stream(ApiResponse.class.getMethods()).map(Method::getName)
				.collect(Collectors.toSet());
		assertThat(instanceMethodNames).doesNotContain("setTimestamp");

		boolean hasConstructorTakingTimestamp = Arrays.stream(ApiResponse.class.getConstructors())
				.anyMatch(ctor -> Arrays.asList(ctor.getParameterTypes()).contains(Long.class));
		assertThat(hasConstructorTakingTimestamp).isFalse();

		ApiResponse<Object> response = ApiResponse.builder().build();
		assertThat(response.getTimestamp()).isNotNull().isPositive();
	}

	/**
	 * 构建器的参数方法恰好是「业务字段」：{@code success / code / message / data}，一个不多一个不少。
	 *
	 * <p>
	 * 排除 {@code build} 与 {@code toString} —— 它们是构建器自带的方法，不是字段入口。
	 */
	@Test
	@DisplayName("@Builder：字段方法只有 success/code/message/data")
	void builderExposesBusinessFieldsOnly() {
		Set<String> fieldMethods = Arrays.stream(ApiResponse.ApiResponseBuilder.class.getMethods())
				.filter(m -> m.getDeclaringClass() == ApiResponse.ApiResponseBuilder.class).map(Method::getName)
				.filter(name -> !"build".equals(name) && !"toString".equals(name)).collect(Collectors.toSet());

		assertThat(fieldMethods).containsExactlyInAnyOrder("success", "code", "message", "data");
	}
}
