package com.zjc.demo.support;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 测试专用请求体，用于触发各类参数校验失败场景。
 *
 * <p>
 * 只存在于 {@code src/test}，不会被生产代码引用。
 *
 * <p>
 * 访问器由 Lombok 的 {@code @Getter} / {@code @Setter} 生成：这两个字段的注释即对外说明，
 * 不必再为 4 个样板 getter/setter 各写一份 javadoc。注意本项目约定——
 * <b>不要写 {@code {@link}} 指向 Lombok 生成的方法</b>，javadoc 只解析源码会报
 * {@code reference not found}，需要引用时写 {@code {@code name}} 这类字面量。
 *
 * @author jiancai.zhong
 */
@Getter
@Setter
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
