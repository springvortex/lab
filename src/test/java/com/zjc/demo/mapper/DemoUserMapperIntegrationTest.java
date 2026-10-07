package com.zjc.demo.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjc.demo.dto.DemoUserAgeGroup;
import com.zjc.demo.entity.DemoUser;

import jakarta.annotation.Resource;

/**
 * Mapper 层真库集成测试：打本地 PostgreSQL，验证 SQL 方言、分页、逻辑删除与乐观锁的真实行为。
 *
 * <p>
 * <b>为什么必须是真库：</b>分页方言、{@code timestamp} 与 {@code LocalDateTime} 的映射、
 * 逻辑删除追加的 {@code AND deleted = 0}、乐观锁的 {@code WHERE version = ?}，
 * 全都是「跑在别的数据库上才暴露」的行为。用 H2 或 Mockito 打桩都测不出来——
 * 它们能证明代码逻辑通顺，证明不了 PostgreSQL 上真的对。
 *
 * <p>
 * <b>数据隔离：</b>类上标了 {@link Transactional}，每个用例结束后 Spring 自动回滚，
 * 不会在库里留下垃圾数据，因此用户名可以随便生成、无需清理。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@Transactional
class DemoUserMapperIntegrationTest {

	/**
	 * 待测 Mapper。
	 */
	@Resource
	private DemoUserMapper demoUserMapper;

	/**
	 * 原生 JDBC 模板，用来验证「逻辑删除后行仍在库里」这类 MP 查询表达不了的事实。
	 */
	@Resource
	private JdbcTemplate jdbcTemplate;

	/**
	 * 造一个用户实体，用户名带随机前缀以避开唯一索引。
	 *
	 * @param prefix 用户名前缀，同时用于按前缀过滤
	 * @return 未落库的实体
	 */
	private static DemoUser newUser(String prefix) {
		DemoUser user = new DemoUser();
		user.setUsername(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
		user.setEmail("user@example.com");
		user.setAge(18);
		return user;
	}

	/**
	 * 插入：雪花 ID 由 MP 生成，审计字段由自动填充处理器写入，版本号与删除标记取表默认值。
	 */
	@Test
	@DisplayName("插入：生成雪花 ID + 自动填充审计字段")
	void insertGeneratesIdAndFillsAuditFields() {
		DemoUser user = newUser("insert");

		assertThat(demoUserMapper.insert(user)).isEqualTo(1);

		assertThat(user.getId()).isNotNull();
		assertThat(user.getCreateTime()).isNotNull();
		assertThat(user.getUpdateTime()).isEqualTo(user.getCreateTime());

		DemoUser stored = demoUserMapper.selectById(user.getId());
		assertThat(stored.getVersion()).isZero();
		assertThat(stored.getDeleted()).isZero();
		assertThat(stored.getUsername()).isEqualTo(user.getUsername());
	}

	/**
	 * 更新：乐观锁版本号必须自增，且新值真的落库。
	 *
	 * <p>
	 * <b>必须先 {@code selectById} 再更新，不能拿 insert 后那个对象直接更新。</b>
	 * 实测 SQL：insert 语句里压根不含 {@code version} 列（走数据库 {@code DEFAULT 0}），
	 * MP 也不会把默认值回写进实体，所以插入后 {@code getVersion()} 是 {@code null}。
	 * 而乐观锁在 version 为 {@code null} 时会<b>整段跳过</b>——生成的 SQL 是
	 * {@code UPDATE ... WHERE id=? AND deleted=0}，不带 {@code AND version=?}，
	 * 于是这条记录从第一次更新起就没有并发保护，且全程不报错。
	 */
	@Test
	@DisplayName("更新：version 自增 1 且新值落库")
	void updateIncrementsVersion() {
		DemoUser user = newUser("update");
		demoUserMapper.insert(user);
		assertThat(user.getVersion()).isNull();

		// 重新读一次，把库里的 version=0 取回内存，乐观锁才会真正参与
		DemoUser loaded = demoUserMapper.selectById(user.getId());
		assertThat(loaded.getVersion()).isZero();

		loaded.setUsername("update-renamed");
		assertThat(demoUserMapper.updateById(loaded)).isEqualTo(1);

		DemoUser stored = demoUserMapper.selectById(user.getId());
		assertThat(stored.getUsername()).isEqualTo("update-renamed");
		assertThat(stored.getVersion()).isEqualTo(1);
		assertThat(stored.getUpdateTime()).isNotNull();
	}

	/**
	 * 乐观锁：拿着旧版本号去更新，影响行数必须是 0（而不是把别人的改动覆盖掉）。
	 *
	 * <p>
	 * 这是乐观锁的核心保证。如果这里返回 1，说明 {@code @Version} 没生效，
	 * 并发写入会静默丢更新——不报错、不留痕，只能靠对账发现。
	 *
	 * <p>
	 * <b>为什么用 JdbcTemplate 而不是再 {@code selectById} 一次来造「两份数据」：</b>
	 * 同一事务内 MyBatis 的一级缓存（默认 {@code localCacheScope=SESSION}）会让两次相同的
	 * {@code selectById} 返回<b>同一个对象实例</b>——改了「第一份」等于改了「第二份」，
	 * 根本造不出版本差。用原生 SQL 直接在库里把 version 加 1，才是真的在模拟另一个事务先提交。
	 */
	@Test
	@DisplayName("乐观锁：旧版本号更新影响 0 行")
	void staleVersionUpdateAffectsZeroRows() {
		DemoUser user = newUser("lock");
		demoUserMapper.insert(user);

		DemoUser stale = demoUserMapper.selectById(user.getId());
		assertThat(stale.getVersion()).isZero();

		// 模拟另一个事务抢先提交：绕开 MP，直接把库里的 version 推到 1
		jdbcTemplate.update("update demo_user set version = version + 1 where id = ?", user.getId());

		stale.setUsername("lock-second");
		assertThat(demoUserMapper.updateById(stale)).isZero();

		assertThat(demoUserMapper.selectById(user.getId()).getUsername()).isEqualTo(user.getUsername());
	}

	/**
	 * 逻辑删除：MP 查询查不到，但行仍然在库里、{@code deleted} 被置为 1。
	 */
	@Test
	@DisplayName("逻辑删除：查询不可见但物理行仍在")
	void logicDeleteKeepsRowInTable() {
		DemoUser user = newUser("delete");
		demoUserMapper.insert(user);

		assertThat(demoUserMapper.deleteById(user.getId())).isEqualTo(1);
		assertThat(demoUserMapper.selectById(user.getId())).isNull();

		Integer deleted = jdbcTemplate.queryForObject("select deleted from demo_user where id = ?", Integer.class,
				user.getId());
		assertThat(deleted).isEqualTo(1);
	}

	/**
	 * 分页：总记录数与实际返回条数分离，{@code total} 由 COUNT 查询得出。
	 */
	@Test
	@DisplayName("分页：total 为总条数、records 为当前页")
	void pageReturnsTotalAndCurrentSlice() {
		String prefix = "page-" + UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < 3; i++) {
			demoUserMapper.insert(newUser(prefix));
		}

		LambdaQueryWrapper<DemoUser> wrapper = new LambdaQueryWrapper<DemoUser>().likeRight(DemoUser::getUsername, prefix);
		Page<DemoUser> page = demoUserMapper.selectPage(new Page<>(1, 2), wrapper);

		assertThat(page.getTotal()).isEqualTo(3L);
		assertThat(page.getRecords()).hasSize(2);
		assertThat(page.getPages()).isEqualTo(2L);
	}

	/**
	 * 分页：单页条数被 {@code maxLimit} 截断，防止前端传超大 size 拖垮数据库。
	 */
	@Test
	@DisplayName("分页：超大 size 被 maxLimit 截断")
	void oversizedPageSizeIsTruncated() {
		demoUserMapper.insert(newUser("limit"));

		Page<DemoUser> page = demoUserMapper.selectPage(new Page<>(1, 100_000), null);

		assertThat(page.getSize()).isEqualTo(500L);
	}

	/**
	 * 自定义 SQL（XML）：动态条件生效，且分页插件会自动改写这条手写 SQL。
	 */
	@Test
	@DisplayName("自定义 SQL：动态条件 + 自动分页")
	void selectByConditionAppliesFiltersAndPagination() {
		String prefix = "xml-" + UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < 3; i++) {
			DemoUser user = newUser(prefix);
			user.setAge(20);
			demoUserMapper.insert(user);
		}
		DemoUser young = newUser(prefix);
		young.setAge(10);
		demoUserMapper.insert(young);

		// 只按前缀过滤：4 条
		assertThat(demoUserMapper.selectByCondition(new Page<>(1, 10), prefix, null).getTotal()).isEqualTo(4L);
		// 再加年龄条件：只剩 3 条
		assertThat(demoUserMapper.selectByCondition(new Page<>(1, 10), prefix, 18).getTotal()).isEqualTo(3L);

		// 分页插件对自定义 SQL 同样生效
		IPage<DemoUser> page = demoUserMapper.selectByCondition(new Page<>(1, 2), prefix, null);
		assertThat(page.getTotal()).isEqualTo(4L);
		assertThat(page.getRecords()).hasSize(2);
	}

	/**
	 * 自定义 SQL：关键字为空时 {@code <where>} 里的条件整段不拼，等于查全部。
	 */
	@Test
	@DisplayName("自定义 SQL：条件全为空时不过滤")
	void selectByConditionWithNoFilterReturnsAll() {
		demoUserMapper.insert(newUser("nofilter"));

		long total = demoUserMapper.selectByCondition(new Page<>(1, 10), null, null).getTotal();

		assertThat(total).isPositive();
	}

	/**
	 * 自定义 SQL：聚合结果映射到自定义 VO，下划线列名转驼峰字段。
	 */
	@Test
	@DisplayName("自定义 SQL：聚合结果映射到 VO")
	void selectAgeGroupSummaryMapsToVo() {
		DemoUser user = newUser("agegroup");
		user.setAge(30);
		demoUserMapper.insert(user);

		List<DemoUserAgeGroup> groups = demoUserMapper.selectAgeGroupSummary();

		assertThat(groups).isNotEmpty();
		assertThat(groups).allSatisfy(group -> {
			assertThat(group.getAgeGroup()).isNotBlank();
			assertThat(group.getUserCount()).isPositive();
		});
		assertThat(groups).anySatisfy(group -> assertThat(group.getAgeGroup()).isEqualTo("青年"));
	}
}
