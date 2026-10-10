package com.zjc.demo.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表实体基类，承载每张表都有、且由服务端掌管的公共字段。
 *
 * <p>
 * MyBatis-Plus 沿继承链读取字段与注解，因此子类不必重复声明。两个约束：
 * <b>表里必须有对应列</b>（否则 SELECT 直接报 column does not exist），
 * <b>入参 DTO 不要继承本类</b>（否则客户端能改主键、传 {@code deleted=1}）。
 *
 * @author jiancai.zhong
 */
@Data
public class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，雪花号，由 MyBatis-Plus 本地生成。
     */
    @TableId
    private Long id;

    /**
     * 创建人。待接入登录态后由自动填充器写入。
     */
    private Long createBy;

    /**
     * 创建时间，仅在 INSERT 时填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人。待接入登录态。
     */
    private Long updateBy;

    /**
     * 更新时间，UPDATE 时刷新。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0 未删、1 已删。建表必须是 {@code NOT NULL DEFAULT 0}，为 {@code null} 时不生效且不报错。
     */
    @TableLogic
    private Integer deleted;
}
