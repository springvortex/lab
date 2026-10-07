package com.zjc.demo.web;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页响应结构，用于替换直接把 {@code IPage} 序列化给前端的做法。
 *
 * <p>
 * <b>为什么不直接返回 {@code IPage}：</b>{@code IPage} 的实现类 {@code Page} 上带着
 * {@code optimizeCountSql}、{@code searchCount}、{@code countId}、{@code maxLimit}
 * 这些纯内部字段，直接序列化会把它们一并吐给前端——既冗余，又等于把分页实现细节
 * 固化成了对外契约，以后换分页组件就会变成破坏性变更。
 *
 * <p>
 * <b>使用示例：</b>
 *
 * <pre>{@code
 * Page<DemoUser> page = new Page<>(current, size);
 * demoUserService.page(page, wrapper);
 * return ApiResponse.success(PageResult.of(page));
 * }</pre>
 *
 * <p>
 * <b>注意事项：</b>{@code records} 为空时返回空列表而非 {@code null}，
 * 前端不必写 {@code data.records && data.records.length} 这种防御判断。
 *
 * @param <T> 单条记录的类型
 * @author jiancai.zhong
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 当前页数据列表，不为 {@code null}。
	 */
	private List<T> records = List.of();

	/**
	 * 符合条件的总记录数。
	 */
	private long total;

	/**
	 * 当前页码，从 1 开始。
	 */
	private long current;

	/**
	 * 每页条数。
	 */
	private long size;

	/**
	 * 总页数。
	 */
	private long pages;

	/**
	 * 把 MyBatis-Plus 的分页对象转换为对外响应结构。
	 *
	 * <p>
	 * 传入 {@code null} 时返回空结果（各计数为 0），而不是抛
	 * {@code NullPointerException}——分页方法在异常分支下返回 {@code null} 是可能的，
	 * 让调用方少写一个判空。
	 *
	 * @param page MyBatis-Plus 分页结果，可为 {@code null}
	 * @param <T>  单条记录的类型
	 * @return 分页响应结构，永不为 {@code null}
	 */
	public static <T> PageResult<T> of(IPage<T> page) {
		PageResult<T> result = new PageResult<>();
		if (page == null) {
			return result;
		}
		result.records = page.getRecords() == null ? List.of() : page.getRecords();
		result.total = page.getTotal();
		result.current = page.getCurrent();
		result.size = page.getSize();
		result.pages = page.getPages();
		return result;
	}
}
