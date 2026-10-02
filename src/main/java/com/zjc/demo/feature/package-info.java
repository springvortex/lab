/**
 * Spring Boot 4 新能力演示包。
 *
 * <p>
 * <b>{@link org.jspecify.annotations.NullMarked} 的作用：</b>本包（含子包）下所有类型默认
 * <b>非空</b>，方法返回值、参数、字段都不允许为 {@code null}；确实可能为空的地方必须显式标注
 * {@link org.jspecify.annotations.Nullable}。
 *
 * <p>
 * 这是 Spring Framework 7 全面换装 JSpecify 之后的配套用法：Spring 自身的 API 已经全部按
 * JSpecify 标注，IDE 与静态分析工具能据此报出「可能为 null 却直接解引用」的问题。项目里按包
 * 逐个启用，可以避免一次性改造整个代码库——先给新包加 {@code @NullMarked}，老代码慢慢迁。
 *
 * <p>
 * <b>注意：</b>{@code @NullMarked} 只影响编译期与静态分析，<b>运行期不校验</b>，
 * 传了 {@code null} 也不会抛异常。要运行期兜底仍需靠 {@code Objects.requireNonNull}
 * 或 Bean Validation 注解。
 *
 * @author jiancai.zhong
 */
@NullMarked
package com.zjc.demo.feature;

import org.jspecify.annotations.NullMarked;
