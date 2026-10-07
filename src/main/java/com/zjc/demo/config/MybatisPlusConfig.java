package com.zjc.demo.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * MyBatis-Plus 插件配置：Mapper 扫描 + 拦截器链。
 *
 * <p>
 * Boot 4 下 MyBatis 的自动配置由 {@code mybatis-plus-spring-boot4-starter} 提供，
 * 数据源、{@code SqlSessionFactory}、事务管理器都已自动装配，<b>不需要</b>自己写
 * {@code DataSource} 或 {@code SqlSessionFactoryBean}。这里只负责两件自动配置做不了的事：
 * <ol>
 * <li>告诉 MyBatis 去哪个包下找 Mapper 接口（自动配置只认 {@code @Mapper} 注解，逐个标太啰嗦）；</li>
 * <li>注册拦截器链，启用分页与乐观锁。</li>
 * </ol>
 *
 * <p>
 * <b>使用示例：</b>
 *
 * <pre>{@code
 * // 分页：page() 返回的 IPage 里已有 total / pages，不必再手写 count 查询
 * Page<DemoUser> page = new Page<>(1, 10);
 * demoUserService.page(page, wrapper);
 * }</pre>
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>拦截器<b>有顺序要求</b>：官方文档明确要求「多个插件时把分页插件放到执行链的<b>最后面</b>」，
 * 否则 COUNT SQL 可能统计不准确。所以下面先加乐观锁、后加分页；</li>
 * <li>{@code PaginationInnerInterceptor} 必须显式指定 {@link DbType}，否则 MP 要靠 JDBC
 * 元数据猜数据库类型，多数据源或连接池未建连时会猜错。 单数据源场景下务必写死，别偷懒；</li>
 * <li>{@code maxLimit} 是防呆阀：前端传 {@code size=100000} 时会被静默截断， 而不是真去查十万条拖垮数据库；</li>
 * <li>自 MP <b>3.5.9</b> 起 {@code PaginationInnerInterceptor} 被从 starter 中拆分出去，
 * 必须额外引入 {@code mybatis-plus-jsqlparser}，否则这个类根本不存在（编译期就报错）。
 * 少了它的后果不是报错，而是分页「看起来能跑但查的是全表」。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Configuration
@MapperScan("com.zjc.demo.mapper")
public class MybatisPlusConfig {

	/**
	 * 单页最大条数，防止前端传超大 {@code size} 一次拉爆内存与网络。
	 *
	 * <p>
	 * 超过该值时 MP 会静默截断到本值，不报错——需要严格拒答的场景请在 Controller 层另行校验
	 * 并返回 400。
	 */
	public static final long MAX_PAGE_SIZE = 500L;

	/**
	 * 注册 MyBatis-Plus 拦截器链，启用分页（MySQL 方言）与乐观锁。
	 *
	 * <p>
	 * 两个拦截器都只在「条件满足」时改写 SQL：分页拦截 {@code IPage} 参数的查询，
	 * 乐观锁拦截实体带 {@code @Version} 字段的 UPDATE，其余 SQL 原样透传。
	 *
	 * @return 装配好分页与乐观锁的拦截器链
	 */
	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

		// 乐观锁：实体字段标 @Version 后，UPDATE 会自动追加 AND version = ? 并做 version + 1
		interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

		// 分页放最后：官方要求，避免 COUNT SQL 统计不准
		PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
		pagination.setMaxLimit(MAX_PAGE_SIZE);
		interceptor.addInnerInterceptor(pagination);

		return interceptor;
	}
}
