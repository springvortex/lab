package com.zjc.demo.jasypt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 加密配置项实体，对应 {@code encrypted_config} 表。{@code cipherValue} 存密文，
 * 读取时由业务侧解密。注意：本表目前没有建表语句，库中不存在。
 *
 * @author jiancai.zhong
 */
@Getter
@Setter
@TableName("encrypted_config")
public class EncryptedConfig {

    @TableId
    private Long id;

    /**
     * 配置键，库上有唯一约束。
     */
    private String configKey;

    /**
     * 配置值密文。
     */
    private String cipherValue;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
