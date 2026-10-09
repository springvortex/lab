package com.zjc.demo.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体，对应 {@code demo.demo_user} 表。
 *
 * <p>
 * 三个 MP 特性字段都在这里体现：{@code id} 用雪花号（{@code assign_id}，由全局配置决定，不必写
 * {@code @TableId(type = ...)}）、{@code version} 参与乐观锁、{@code deleted} 参与逻辑删除、
 * {@code createTime}/{@code updateTime} 由 {@code MybatisPlusMetaObjectHandler} 自动填充。
 *
 * <p>
 * ⚠️ 自动填充要求「实体字段标了 {@code @TableField(fill = ...)}」且「处理器按同名属性赋值」<b>成对出现</b>，
 * 少一边就静默不填。
 *
 * @author jiancai.zhong
 */
@Getter
@Setter
@TableName("demo_user")
public class DemoUser {

	/**
	 * 主键，雪花号。id 类型由全局配置 {@code id-type: assign_id} 决定，无需在注解上重复声明。
	 */
	@TableId
	private Long id;

	/**
	 * 登录名，库上有 {@code uk_demo_user_username} 唯一约束（{@code WHERE deleted = 0}），重复插入会抛异常。
	 */
	private String username;

	/**
	 * 邮箱，可空。
	 */
	private String email;

	/**
	 * 年龄，可空。
	 */
	private Integer age;

	/**
	 * 乐观锁版本号。UPDATE 时框架自动追加 {@code AND version = ?} 并自增，冲突时影响行数为 0。
	 */
	@Version
	private Integer version;

	/**
	 * 逻辑删除标记：0 未删、1 已删。写成 1 后普通查询自动追加 {@code deleted = 0} 条件。
	 */
	@TableLogic
	private Integer deleted;

	/**
	 * 创建时间，仅在 INSERT 时填充。
	 */
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	/**
	 * 更新时间，INSERT 与 UPDATE 都会刷新。
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

}
