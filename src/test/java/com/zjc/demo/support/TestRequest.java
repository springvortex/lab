package com.zjc.demo.support;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 测试专用请求体，用于触发各类参数校验失败场景。
 *
 * <p>
 * 只存在于 {@code src/test}，不会被生产代码引用。
 *
 * <p>
 * 访问器由 Lombok 的 {@code @Data} 生成：这两个字段的注释即对外说明， 不必再为 4 个样板 getter/setter 各写一份
 * javadoc。注意本项目约定—— <b>不要写 {@code {@link}} 指向 Lombok 生成的方法</b>，javadoc 只解析源码会报
 * {@code reference not found}，需要引用时写 {@code {@code name}} 这类字面量。
 *
 * <p>
 * 这里用 {@code @Data} 而不只是 {@code @Getter} / {@code @Setter}，是因为它是<b>纯测试夹具</b>：
 * 不参与 JSON 序列化契约、不需要保留无参构造器之外的语义，多出来的 {@code equals} / {@code hashCode} /
 * {@code toString} 反而方便在断言里直接比较整个对象。 生产类不要照抄这个选择，见项目约定中对 {@code @Data} 的限制。
 *
 * @author jiancai.zhong
 */
@Data
public class TestRequest {

	/**
	 * 名称，用于触发 {@code @NotBlank} 校验失败
	 */
	@NotBlank(message = "名称不能为空")
	private String name;

	/**
	 * 年龄，用于触发 {@code @Min} 校验失败
	 */
	@Min(value = 1, message = "年龄必须大于 0")
	private Integer age;
}
