package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

import com.zjc.demo.async.AsyncTaskMetricsDecorator;
import com.zjc.demo.async.MdcTaskDecorator;

/**
 * {@link AsyncConfig} 的测试。
 *
 * <p>
 * <b>重点验证「两个装饰器都存在且顺序正确」</b>，而不是「Bean 能不能创建」：
 * <ul>
 * <li>少了 {@code @EnableAsync}，{@code @Async} 会退化成同步执行，<b>不报错</b>；</li>
 * <li>顺序反了不会报错，但指标测到的是「去掉链路透传开销之后」的时间，或链路上下文被覆盖。</li>
 * </ul>
 * 这两件都属于「配置写错但看不出来」的类型，只能靠断言钉死。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
class AsyncConfigTest {

	/**
	 * 容器里收集到的全部 {@link TaskDecorator}，由 Boot 的任务执行自动配置装配。
	 */
	@Autowired
	private List<TaskDecorator> taskDecorators;

	/**
	 * 链路透传与指标埋点两个装饰器必须同时在容器里。
	 *
	 * <p>
	 * 这是 Boot 4 的能力：注册多个 {@code TaskDecorator} Bean 不再冲突，由 Boot 组装成
	 * {@code CompositeTaskDecorator}。Boot 3 及以前出现两个会直接抛
	 * {@code NoUniqueBeanDefinitionException}。
	 */
	@Test
	@DisplayName("链路透传与指标埋点两个装饰器共存")
	void bothDecoratorsAreRegistered() {
		assertThat(taskDecorators).hasSize(2);
		assertThat(taskDecorators).anyMatch(MdcTaskDecorator.class::isInstance)
				.anyMatch(AsyncTaskMetricsDecorator.class::isInstance);
	}

	/**
	 * 指标埋点必须是<b>最外层</b>（{@code @Order} 值最大），这样它测到的是端到端耗时。
	 *
	 * <p>
	 * <b>为什么这条断言值得单独写：</b>{@code CompositeTaskDecorator} 的语义是「依次包装」——
	 * 列表里<b>最后一个装饰器是最外层</b>，最先执行、最后收尾。顺序写反时不会报错，
	 * 只会让指标少算了链路透传的开销，属于典型的「数据看着对、其实有偏差」。
	 *
	 * <p>
	 * 这里依赖 Spring 按 {@code @Order} 排序后的注入顺序，直接断言最后一个元素的类型。
	 */
	@Test
	@DisplayName("指标埋点排在最外层（列表末位）")
	void metricsDecoratorIsOutermost() {
		assertThat(taskDecorators.get(taskDecorators.size() - 1)).isInstanceOf(AsyncTaskMetricsDecorator.class);
	}

	/**
	 * 链路透传排在内层。
	 *
	 * <p>
	 * 与上一条互为镜像：透传只读提交时的 MDC、不写额外键，因此放在内层不会被指标埋点干扰；
	 * 反过来若指标在内层，它的执行时间会被排除在统计之外。
	 */
	@Test
	@DisplayName("链路透传排在内层（列表首位）")
	void mdcDecoratorIsInnermost() {
		assertThat(taskDecorators.get(0)).isInstanceOf(MdcTaskDecorator.class);
	}

	/**
	 * 装饰器的排序值必须符合「透传 0 < 指标 LOWEST_PRECEDENCE」的约定。
	 *
	 * <p>
	 * 通过 {@code AsyncConfig} 的 {@code @Order} 注解读出实际值，防止有人改成同值或写反——
	 * 同值时排序结果不确定，表现为「有时对有时错」。
	 */
	@Test
	@DisplayName("两个 @Order 排序值保持 0 < LOWEST_PRECEDENCE 的相对关系")
	void orderValuesAreRelative() throws NoSuchMethodException {
		int mdcOrder = orderOf(AsyncConfig.class.getDeclaredMethod("mdcTaskDecorator"));
		int metricsOrder = orderOf(AsyncConfig.class.getDeclaredMethod("asyncTaskMetricsDecorator",
				io.micrometer.core.instrument.MeterRegistry.class));

		assertThat(mdcOrder).isEqualTo(0);
		assertThat(metricsOrder).isEqualTo(Ordered.LOWEST_PRECEDENCE);
		assertThat(metricsOrder).isGreaterThan(mdcOrder);
	}

	/**
	 * 读取方法上 {@code @Order} 的取值。
	 *
	 * @param method 目标方法
	 * @return 排序值
	 */
	private static int orderOf(java.lang.reflect.Method method) {
		org.springframework.core.annotation.Order order = method
				.getAnnotation(org.springframework.core.annotation.Order.class);
		assertThat(order).as("%s 必须标注 @Order", method.getName()).isNotNull();
		return order.value();
	}
}
