package com.zjc.demo.core.config;

import org.apache.ibatis.annotations.Mapper;
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
 * 数据源、{@code SqlSessionFactory}、事务管理器都已自动装配，<b>不需要</b>自己写 {@code DataSource} 或
 * {@code SqlSessionFactoryBean}。这里只负责两件自动配置做不了的事：
 * <ol>
 * <li>告诉 MyBatis 去哪个包下找 Mapper 接口（自动配置只认 {@code @Mapper} 注解，逐个标太啰嗦）；</li>
 * <li>注册拦截器链，启用分页与乐观锁。</li>
 * </ol>
 *
 * <p>
 * <b>Mapper 扫描策略：通配包名 + {@code @Mapper} 注解收窄。</b>
 *
 * <p>
 * 只写一条 {@code @MapperScan("com.zjc.demo.**.mapper")} 就覆盖所有模块， 各模块把 Mapper
 * 放在自己的 {@code <模块>/mapper} 包下即可：{@code com.zjc.demo.jasypt.mapper}、
 * {@code com.zjc.demo.user.mapper}……<b>新增模块不必回来改这里</b>。
 *
 * <p>
 * <b>{@code *} 与 {@code **} 的差别（2026-10-09 实测）：</b>
 *
 * <table border="1">
 * <caption>通配符匹配范围对照</caption>
 * <tr>
 * <th>写法</th>
 * <th>{@code user.mapper}（一层）</th>
 * <th>{@code user.admin.mapper}（两层）</th>
 * </tr>
 * <tr>
 * <td>{@code com.zjc.demo.*.mapper}</td>
 * <td>扫到</td>
 * <td><b>扫不到</b></td>
 * </tr>
 * <tr>
 * <td>{@code com.zjc.demo.**.mapper}</td>
 * <td>扫到</td>
 * <td>扫到</td>
 * </tr>
 * </table>
 *
 * <p>
 * {@code *} 只匹配<b>一层</b>包名，{@code **} 匹配<b>任意层级</b>。选 {@code **} 是为了模块内部 再分层
 * （如 {@code user/admin/mapper}）时也不会漏扫。两者都<b>不是</b>「正则」写法， 也<b>不能</b>写成
 * {@code com.zjc.demo.**}（会连非 mapper 的包一起扫，虽然过不了 {@code @Mapper} 过滤，但没必要扩大范围）。
 *
 * <p>
 * ⚠️ <b>{@code annotationClass = Mapper.class} 不能省。</b>MyBatis 扫描器的候选判定是
 * {@code isInterface() && isIndependent()}，<b>默认收下扫描范围内所有接口</b>；不写
 * {@code annotationClass}，业务接口（如 {@code JasyptService}）也会被当成 Mapper 注册，启动期即失败。
 * 加上之后 include 过滤器才变成「带 {@code @Mapper} 注解」。
 *
 * <p>
 * 因此每个 Mapper 接口<b>都要标 {@code @Mapper}</b>：
 *
 * <pre>{@code
 * import org.apache.ibatis.annotations.Mapper;
 *
 * @Mapper
 * public interface XxxMapper extends BaseMapper<XxxEntity> {
 * }
 * }</pre>
 *
 * <p>
 * <b>实测证据（2026-10-09，release/v1.0.0，PostgreSQL 18）：</b>工程内放三个 Mapper 接口 ——
 * {@code jasypt.mapper.EncryptedConfigMapper}、{@code user.mapper.DemoUserMapper}、
 * {@code user.admin.mapper.AdminUserMapper}（两层），另加一个<b>故意不标 {@code @Mapper}</b> 的
 * {@code user.admin.mapper.NoAnnotationMapper}。启动后容器里注册的 Bean 为：
 *
 * <pre>
 * com.zjc.demo.jasypt.mapper.EncryptedConfigMapper    [&#64;Mapper=true]
 * com.zjc.demo.user.admin.mapper.AdminUserMapper      [&#64;Mapper=true]
 * com.zjc.demo.user.mapper.DemoUserMapper             [&#64;Mapper=true]
 * </pre>
 *
 * <p>
 * 即：{@code **} 确实扫到了两层子包，而<b>没标 {@code @Mapper} 的那个完全没进容器</b>，
 * 证明 {@code annotationClass} 收窄真实生效。
 *
 * <p>
 * 配套约定（改了要一起改，否则静默失效）：
 * <ul>
 * <li>{@code mybatis-plus.mapper-locations} 保持默认的「classpath* 加 mapper 目录通配」， XML
 * 统一放 {@code src/main/resources/mapper/} 下即可，与模块包结构无关；</li>
 * <li>⚠️ <b>{@code type-aliases-package} 的通配粒度必须与 Mapper 扫描的粒度「对齐」，这是实测踩出来的坑。</b>
 * 当前值 {@code com.zjc.demo.*.entity} 只匹配<b>一层</b>，于是 {@code user.admin.entity.AdminUser} 这类
 * 两层子包<b>不会</b>被注册成别名。此时 XML 里写短类名 {@code resultType="AdminUser"} 会在<b>启动期</b>直接炸：
 *
 * <pre>
 * org.apache.ibatis.type.TypeException: Could not resolve type alias 'AdminUser'.
 * Cause: java.lang.ClassNotFoundException: Cannot find class: AdminUser
 * </pre>
 *
 * 改成全限定名 {@code resultType="com.zjc.demo.user.admin.entity.AdminUser"} 立刻正常 ——
 * 说明不是 Mapper 没扫到，而是别名没注册。 <b>结论：Mapper 用 {@code **.mapper} 时，别名也应写成
 * {@code com.zjc.demo.**.entity}，两者粒度保持一致</b>，否则「Mapper 扫到了、XML 里短类名解析不了」
 * 这种一半生效的状态最难查。</li>
 * <li>别名的坑只影响<b>短类名</b>写法；{@code resultType} 用全限定名永远安全，代价是冗长。</li>
 * </ul>
 *
 * <p>
 * <b>使用示例：</b>
 *
 * <pre>{@code
 * // 分页：page() 返回的 IPage 里已有 total / pages，不必再手写 count 查询
 * Page<XxxEntity> page = new Page<>(1, 10);
 * xxxService.page(page, wrapper);
 * }</pre>
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>拦截器<b>有顺序要求</b>：官方文档明确要求「多个插件时把分页插件放到执行链的<b>最后面</b>」， 否则 COUNT SQL
 * 可能统计不准确。所以下面先加乐观锁、后加分页；</li>
 * <li>{@code PaginationInnerInterceptor} 必须显式指定 {@link DbType}，否则 MP 要靠 JDBC
 * 元数据猜数据库类型，多数据源或连接池未建连时会猜错。 单数据源场景下务必写死，别偷懒；</li>
 * <li>{@code maxLimit} 是防呆阀：前端传 {@code size=100000} 时会被静默截断，
 * 而不是真去查十万条拖垮数据库；</li>
 * <li>自 MP <b>3.5.9</b> 起 {@code PaginationInnerInterceptor} 被从 starter 中拆分出去，
 * 必须额外引入 {@code mybatis-plus-jsqlparser}，否则这个类根本不存在（编译期就报错）。
 * 少了它的后果不是报错，而是分页「看起来能跑但查的是全表」。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Configuration
@MapperScan(basePackages = "com.zjc.demo.**.mapper", annotationClass = Mapper.class)
public class MybatisPlusConfig {

	/**
	 * 单页最大条数，防止前端传超大 {@code size} 一次拉爆内存与网络。
	 *
	 * <p>
	 * 超过该值时 MP 会静默截断到本值，不报错——需要严格拒答的场景请在 Controller 层另行校验 并返回 400。
	 */
	public static final long MAX_PAGE_SIZE = 500L;

	/**
	 * 注册 MyBatis-Plus 拦截器链，启用分页（PostgreSQL 方言）与乐观锁。
	 *
	 * <p>
	 * 两个拦截器都只在「条件满足」时改写 SQL：分页拦截 {@code IPage} 参数的查询， 乐观锁拦截实体带 {@code @Version} 字段的
	 * UPDATE，其余 SQL 原样透传。
	 *
	 * @return 装配好分页与乐观锁的拦截器链
	 */
	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

		// 乐观锁：实体字段标 @Version 后，UPDATE 会自动追加 AND version = ? 并做 version + 1
		interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

		// 分页放最后：官方要求，避免 COUNT SQL 统计不准
		PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
		pagination.setMaxLimit(MAX_PAGE_SIZE);
		interceptor.addInnerInterceptor(pagination);

		return interceptor;
	}
}
