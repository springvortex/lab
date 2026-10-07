package com.zjc.demo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * {@link PageResult} 单元测试：不连数据库，只验证转换逻辑与边界。
 *
 * <p>
 * 用 Mockito 打桩 {@link IPage} 而不去真查库，是因为这里要验证的是「转换」这一层，
 * 与 SQL 是否正确无关；SQL 那部分由 {@code DemoUserMapperIntegrationTest} 用真库守着。
 *
 * @author jiancai.zhong
 */
class PageResultTest {

	/**
	 * 构造一个指定了记录与计数值的分页对象。
	 *
	 * @param records 当前页记录，可为 {@code null}
	 * @param total   总记录数
	 * @return 打桩后的分页对象
	 */
	@SuppressWarnings("unchecked")
	private static IPage<String> mockPage(List<String> records, long total) {
		IPage<String> page = Mockito.mock(IPage.class);
		Mockito.when(page.getRecords()).thenReturn(records);
		Mockito.when(page.getTotal()).thenReturn(total);
		Mockito.when(page.getCurrent()).thenReturn(2L);
		Mockito.when(page.getSize()).thenReturn(5L);
		Mockito.when(page.getPages()).thenReturn(4L);
		return page;
	}

	/**
	 * 传入 {@code null} 时返回空结果，而不是抛 {@code NullPointerException}。
	 */
	@Test
	@DisplayName("of(null)：返回空结果而不是抛空指针")
	void ofNullReturnsEmptyResult() {
		PageResult<String> result = PageResult.of(null);

		assertThat(result.getRecords()).isEmpty();
		assertThat(result.getTotal()).isZero();
		assertThat(result.getCurrent()).isZero();
		assertThat(result.getSize()).isZero();
		assertThat(result.getPages()).isZero();
	}

	/**
	 * 正常分页对象：记录与计数逐项拷贝。
	 */
	@Test
	@DisplayName("of(page)：记录与分页计数逐项拷贝")
	void ofPageCopiesAllFields() {
		PageResult<String> result = PageResult.of(mockPage(List.of("a", "b"), 17L));

		assertThat(result.getRecords()).containsExactly("a", "b");
		assertThat(result.getTotal()).isEqualTo(17L);
		assertThat(result.getCurrent()).isEqualTo(2L);
		assertThat(result.getSize()).isEqualTo(5L);
		assertThat(result.getPages()).isEqualTo(4L);
	}

	/**
	 * {@code records} 为 {@code null} 时退化为空列表，保证前端不必判空。
	 */
	@Test
	@DisplayName("of(page)：records 为 null 时退化为空列表")
	void ofPageWithNullRecordsReturnsEmptyList() {
		PageResult<String> result = PageResult.of(mockPage(null, 0L));

		assertThat(result.getRecords()).isNotNull().isEmpty();
	}

	/**
	 * 新建实例默认即为空结果，避免构造出 {@code records} 为 {@code null} 的半成品。
	 */
	@Test
	@DisplayName("无参构造：默认空列表而非 null")
	void defaultInstanceHasEmptyRecords() {
		assertThat(new PageResult<String>().getRecords()).isNotNull().isEmpty();
	}
}
