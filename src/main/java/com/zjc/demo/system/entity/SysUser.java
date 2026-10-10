package com.zjc.demo.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zjc.demo.common.constant.system.Gender;
import com.zjc.demo.common.constant.system.UserStatus;
import com.zjc.demo.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 系统用户实体，对应 {@code public.sys_user} 表。
 *
 * <p>
 * id、审计字段、逻辑删除继承自 {@link BaseEntity}。本表没有乐观锁字段，并发修改是「后写覆盖先写」；
 * {@code password} 不对外返回，由 Controller 剥离。
 *
 * @author jiancai.zhong
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /**
     * 序列化版本号。{@code static} 字段不会被继承，每个类都要自己声明。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录名，未删除的行唯一。
     */
    private String username;

    /**
     * 密码密文。
     */
    private String password;

    private String nickname;

    private String realName;

    private String phone;

    private String email;

    private String avatar;

    /**
     * 性别，取值见 {@link Gender}。
     */
    private Integer gender;

    private LocalDate birthday;

    /**
     * 状态，取值见 {@link UserStatus}。
     */
    private Integer status;

    private Long deptId;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private String remark;

}
