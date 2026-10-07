package com.zjc.demo.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

/**
 * 演示用实体：对应 MySQL 表 {@code demo_user}。
 *
 * <p>
 * 它是「MyBatis-Plus 注解能力」的集中展示，几个关键注解的含义与代价：
 * <ul>
 * <li>{@code @TableName}：类名与表名不一致时必写，否则 MP 按类名转下划线去猜表名；</li>
 * <li>{@code @TableId(type = IdType.ASSIGN_ID)}：雪花 ID 由 MP 在本地生成，
 * <b>不与数据库交互</b>，因此批量插入不需要逐条回查主键（表列也就不需要 AUTO_INCREMENT）。代价是 ID 较长（19 位），
 * 传到 JS 会超出 {@code Number} 安全整数上限——本项目已用 Jackson 的
 * {@code Long → String} 全局转换接住（见 {@code JacksonConfig}）；</li>
 * <li>{@code @Version}：乐观锁。更新时自动追加 {@code AND version = ?} 并 {@code version + 1}，
 * 影响行数为 0 即代表发生并发冲突，需要业务侧重试或提示；</li>
 * <li>{@code @TableLogic}：逻辑删除。{@code removeById} 变成
 * {@code UPDATE ... SET deleted = 1}，查询自动追加 {@code AND deleted = 0}。</li>
 * </ul>
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>时间字段用 {@code LocalDateTime}（无时区），与 MySQL 的 {@code datetime} 列对应。
 * 若列类型是 {@code timestamp}（MySQL 的 timestamp 会随时区换算），请改用 {@code Instant}；</li>
 * <li>{@code createTime} / {@code updateTime} 的填充逻辑在
 * {@code MybatisPlusMetaObjectHandler}，<b>字段注解与处理器必须成对配置</b>才生效；</li>
 * <li>{@code @TableField(fill = ...)} 只影响 MP 的 {@code insert} / {@code update} 方法，
 * 手写 XML 的 SQL 不会触发填充。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Data
@TableName("demo_user")
public class DemoUser implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 主键，雪花 ID，由 MyBatis-Plus 在插入前生成。
	 */
	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	/**
	 * 用户名，表上带唯一约束。
	 */
	private String username;

	/**
	 * 邮箱。
	 */
	private String email;

	/**
	 * 年龄。
	 */
	private Integer age;

	/**
	 * 乐观锁版本号，每次更新自增 1。
	 *
	 * <p>
	 * 表中设为 {@code NOT NULL DEFAULT 0}：值为 {@code null} 时乐观锁拦截器会直接跳过，
	 * 等于该条记录失去了并发保护，且不报错。
	 */
	@Version
	private Integer version;

	/**
	 * 逻辑删除标记，0 未删除、1 已删除。
	 */
	@TableLogic
	private Integer deleted;

	/**
	 * 创建时间，插入时自动填充。
	 */
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	/**
	 * 更新时间，插入与更新时均自动填充。
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;
}
