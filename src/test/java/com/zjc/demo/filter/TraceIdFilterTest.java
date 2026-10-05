package com.zjc.demo.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.zjc.demo.constant.TraceConstant;

/**
 * {@link TraceIdFilter} 的单元测试。
 *
 * <p>
 * 覆盖三件事：traceId 的沿用与生成规则、写入 MDC 与响应头的时机、以及请求结束后必须清理 MDC
 * （虚拟线程与线程池复用下，不清理会把上一个请求的 traceId 串到下一个请求）。
 *
 * @author jiancai.zhong
 */
class TraceIdFilterTest {

	/**
	 * 被测过滤器，无成员状态，可直接 new。
	 */
	private final TraceIdFilter filter = new TraceIdFilter();

	/**
	 * 构造一个带指定 {@code X-Trace-Id} 请求头的请求。
	 *
	 * @param headerValue 请求头值，{@code null} 表示不带该头
	 * @return 模拟请求
	 */
	private static MockHttpServletRequest requestWithTraceHeader(String headerValue) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
		if (headerValue != null) {
			request.addHeader(TraceConstant.REQUEST_HEADER_NAME, headerValue);
		}
		return request;
	}

	/**
	 * 清理 MDC，防止用例之间互相影响。
	 */
	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	/**
	 * 执行过滤器，并记录过滤链执行期间 MDC 里的 traceId。
	 *
	 * @param request  模拟请求
	 * @param captured 用于接收过滤链内 MDC 取值的容器
	 * @return 响应头里的 traceId
	 * @throws Exception 过滤器抛出的异常
	 */
	private String runFilter(MockHttpServletRequest request, AtomicReference<String> captured) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> captured.set(MDC.get(TraceConstant.MDC_KEY)));
		return response.getHeader(TraceConstant.HEADER_NAME);
	}

	/**
	 * 没有上游 traceId 时本地生成：32 位十六进制，并同步写入 MDC 与响应头。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("无上游 traceId：本地生成 32 位十六进制")
	void generatesTraceIdWhenHeaderMissing() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();

		String traceId = runFilter(requestWithTraceHeader(null), inChain);

		assertThat(traceId).hasSize(32).matches("[0-9a-f]{32}");
		assertThat(inChain.get()).isEqualTo(traceId);
		assertThat(MDC.get(TraceConstant.MDC_KEY)).isNull();
	}

	/**
	 * 过滤链执行期间 MDC 必须有值，否则业务日志拿不到 traceId。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("过滤链执行期间 MDC 可见")
	void mdcIsVisibleDuringChain() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();

		runFilter(requestWithTraceHeader(null), inChain);

		assertThat(inChain.get()).isNotNull();
	}

	/**
	 * 上游下发的合法 traceId 必须原样沿用，用于串起 Nginx / 网关链路。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("上游合法 traceId：原样沿用")
	void reusesUpstreamTraceId() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();

		String traceId = runFilter(requestWithTraceHeader("upstream-12345"), inChain);

		assertThat(traceId).isEqualTo("upstream-12345");
		assertThat(inChain.get()).isEqualTo("upstream-12345");
	}

	/**
	 * 空白请求头视为未提供，必须回退到本地生成而不是把空白写进日志。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("空白 traceId：回退到本地生成")
	void blankHeaderFallsBackToGenerated() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();

		String traceId = runFilter(requestWithTraceHeader("   "), inChain);

		assertThat(traceId).hasSize(32).matches("[0-9a-f]{32}");
	}

	/**
	 * 超长 traceId 截断到 {@link TraceConstant#MAX_LENGTH} 位，防止超长请求头污染日志。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("超长 traceId：截断到 64 位")
	void overlongTraceIdIsTruncated() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();
		String tooLong = "a".repeat(100);

		String traceId = runFilter(requestWithTraceHeader(tooLong), inChain);

		assertThat(traceId).hasSize(TraceConstant.MAX_LENGTH).isEqualTo("a".repeat(64));
	}

	/**
	 * 刚好等于上限的 traceId 不截断（边界值）。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("边界值：长度 64 不截断")
	void exactlyMaxLengthIsKept() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();
		String boundary = "b".repeat(TraceConstant.MAX_LENGTH);

		assertThat(runFilter(requestWithTraceHeader(boundary), inChain)).isEqualTo(boundary);
	}

	/**
	 * 含换行符的 traceId 必须被丢弃并回退到本地生成，否则攻击者可伪造日志行（Log Injection）。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("非法字符（换行）：丢弃并回退到本地生成")
	void illegalCharactersAreRejected() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();

		String traceId = runFilter(requestWithTraceHeader("abc\r\n[INFO] 伪造日志行"), inChain);

		assertThat(traceId).hasSize(32).matches("[0-9a-f]{32}");
		assertThat(traceId).isNotEqualTo("abc");
	}

	/**
	 * 允许的字符集（字母数字与 {@code _ . -}）必须被接受。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("合法字符集 _.- 与字母数字：接受")
	void allowedCharacterSetIsAccepted() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();
		String value = "Abc_123.xyz-789";

		assertThat(runFilter(requestWithTraceHeader(value), inChain)).isEqualTo(value);
	}

	/**
	 * 首尾空白先 trim 再校验，避免「带空格的合法值」被误判为非法。
	 *
	 * @throws Exception 过滤器抛出的异常
	 */
	@Test
	@DisplayName("首尾空白被去除后仍合法")
	void surroundingWhitespaceIsTrimmed() throws Exception {
		AtomicReference<String> inChain = new AtomicReference<>();

		assertThat(runFilter(requestWithTraceHeader("  abc-123  "), inChain)).isEqualTo("abc-123");
	}

	/**
	 * 请求处理抛异常时，MDC 同样必须被清理，不能把 traceId 残留在线程上。
	 */
	@Test
	@DisplayName("过滤链抛异常：MDC 仍被清理")
	void mdcIsClearedEvenWhenChainThrows() {
		MockHttpServletRequest request = requestWithTraceHeader(null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		try {
			filter.doFilter(request, response, (req, res) -> {
				throw new IllegalStateException("模拟业务异常");
			});
		} catch (Exception e) {
			assertThat(e).isInstanceOf(IllegalStateException.class);
		}

		assertThat(MDC.get(TraceConstant.MDC_KEY)).isNull();
	}
}
