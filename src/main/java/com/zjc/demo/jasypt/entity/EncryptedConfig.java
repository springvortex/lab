package com.zjc.demo.jasypt.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 加密配置项实体，对应 {@code demo.encrypted_config} 表。
 *
 * <p>
 * 用于存放「明文不落库」的敏感配置：{@code cipherValue} 存密文，读取时由业务侧解密。 仅演示 Mapper
 * 扫描，不实现真实解密链路。
 *
 * @author jiancai.zhong
 */
@Getter
@Setter
@TableName("encrypted_config")
public class EncryptedConfig {

	/**
	 * 主键，雪花号。
	 */
	@TableId
	private Long id;

	/**
	 * 配置键，库上有唯一约束，同名配置只允许一条。
	 */
	private String configKey;

	/**
	 * 配置值密文。
	 */
	private String cipherValue;

	/**
	 * 备注说明。
	 */
	private String remark;

	/**
	 * 逻辑删除标记：0 未删、1 已删。
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
