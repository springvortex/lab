package com.zjc.demo.config;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

/**
 * MyBatis-Plus 字段自动填充：统一维护审计字段，业务代码不必手工 set 时间。
 *
 * <p>
 * 生效方式是「实体字段上标 {@code @TableField(fill = ...)} + 本处理器按字段名赋值」：
 * 字段标了但处理器没赋值 → 该字段为 {@code null} 写库；处理器赋值了但字段没标 → 不会生效。
 * <b>两处必须成对出现</b>，这是 MP 自动填充最常见的踩坑点。
 *
 * <p>
 * <b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;TableField(fill = FieldFill.INSERT)
 * private LocalDateTime createTime;
 *
 * &#64;TableField(fill = FieldFill.INSERT_UPDATE)
 * private LocalDateTime updateTime;
 * }</pre>
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>这里用的是 {@code strictXxxFill}，<b>不会</b>覆盖已有值：字段非空时直接跳过，
 * 所以「回补历史数据 / 导入数据时手动指定时间」这类场景不会被抹掉；</li>
 * <li>{@code strictXxxFill} 对「实体里根本没有这个字段」的情况静默跳过，不抛异常，
 * 因此给新表加字段时忘了改实体不会炸，但也<b>不会有任何提示</b>；</li>
 * <li>{@code LocalDateTime} 不包含时区信息，写入 {@code timestamp} 列时按 JVM 默认时区解释。
 * 请务必保持 {@code spring.jackson.time-zone} 与 JVM 时区一致，详见 README 的时区说明。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

	/**
	 * 插入时填充：创建时间与更新时间都取当前时间。
	 *
	 * <p>
	 * 两次调用之间不重新取时间，保证同一条记录的 {@code create_time} 与 {@code update_time}
	 * 完全一致，避免「刚插入的记录两个时间差几毫秒」这种说不清的数据。
	 *
	 * @param metaObject 当前插入实体的元数据对象
	 */
	@Override
	public void insertFill(MetaObject metaObject) {
		LocalDateTime now = LocalDateTime.now();
		strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
		strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
	}

	/**
	 * 更新时填充：只刷新更新时间，创建时间保持不变。
	 *
	 * @param metaObject 当前更新实体的元数据对象
	 */
	@Override
	public void updateFill(MetaObject metaObject) {
		strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
	}
}
