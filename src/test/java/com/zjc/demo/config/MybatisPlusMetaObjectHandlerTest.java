package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.zjc.demo.entity.DemoUser;

import jakarta.annotation.Resource;

/**
 * {@link MybatisPlusMetaObjectHandler} 测试：验证审计字段的自动填充与「有值不覆盖」语义。
 *
 * <p>
 * <b>为什么必须起 Spring 容器：</b>MP 的 {@code strictInsertFill} 内部会
 * {@code tableInfo.isWithInsertFill()} —— 它先按实体类去查 {@code TableInfo} 缓存，
 * 而缓存只有在 Mapper 扫描完成（即容器启动）后才存在。脱离容器直接 new 处理器来调，
 * 拿到的是 {@code null} 并抛 {@code NullPointerException}，测不出真实行为。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
class MybatisPlusMetaObjectHandlerTest {

	/**
	 * 待测的自动填充处理器。
	 */
	@Resource
	private MybatisPlusMetaObjectHandler metaObjectHandler;

	/**
	 * 插入填充：创建时间与更新时间都要有值，且两者相等。
	 */
	@Test
	@DisplayName("插入填充：createTime 与 updateTime 都被赋值且相等")
	void insertFillSetsBothTimestamps() {
		DemoUser user = new DemoUser();

		metaObjectHandler.insertFill(SystemMetaObject.forObject(user));

		assertThat(user.getCreateTime()).isNotNull();
		assertThat(user.getUpdateTime()).isEqualTo(user.getCreateTime());
	}

	/**
	 * 严格填充遇到已有值时必须跳过，不能把「手动指定的时间」抹掉。
	 */
	@Test
	@DisplayName("插入填充：已有值不被覆盖")
	void insertFillKeepsExistingValue() {
		LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 0, 0);
		DemoUser user = new DemoUser();
		user.setCreateTime(fixed);
		user.setUpdateTime(fixed);

		metaObjectHandler.insertFill(SystemMetaObject.forObject(user));

		assertThat(user.getCreateTime()).isEqualTo(fixed);
		assertThat(user.getUpdateTime()).isEqualTo(fixed);
	}

	/**
	 * 更新填充：只刷新更新时间，创建时间必须保持不动。
	 */
	@Test
	@DisplayName("更新填充：只刷 updateTime，不动 createTime")
	void updateFillOnlyRefreshesUpdateTime() {
		LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
		DemoUser user = new DemoUser();
		user.setCreateTime(created);

		metaObjectHandler.updateFill(SystemMetaObject.forObject(user));

		assertThat(user.getCreateTime()).isEqualTo(created);
		assertThat(user.getUpdateTime()).isNotNull();
	}

	/**
	 * 更新填充同样遵循「有值不覆盖」。
	 */
	@Test
	@DisplayName("更新填充：已有 updateTime 不被覆盖")
	void updateFillKeepsExistingValue() {
		LocalDateTime fixed = LocalDateTime.of(2026, 2, 2, 0, 0);
		DemoUser user = new DemoUser();
		user.setUpdateTime(fixed);

		metaObjectHandler.updateFill(SystemMetaObject.forObject(user));

		assertThat(user.getUpdateTime()).isEqualTo(fixed);
	}
}
