package com.zjc.demo.system.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 新增 / 修改系统用户的入参。
 *
 * <p>
 * 刻意不继承 {@code BaseEntity}：基类里的 id、deleted、审计字段由服务端掌管，暴露给前端等于让客户端
 * 能绕开逻辑删除。{@code password} 留空表示不修改密码。
 *
 * @author jiancai.zhong
 */
@Getter
@Setter
public class SysUserDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录名，必填。
     */
    @NotBlank(message = "登录名不能为空")
    @Size(max = 64, message = "登录名不能超过 64 个字符")
    private String username;

    /**
     * 密码，新增时必填，修改时留空表示不改动。
     */
    @Size(max = 128, message = "密码不能超过 128 个字符")
    private String password;

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname;

    @Size(max = 64, message = "真实姓名不能超过 64 个字符")
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Size(max = 255, message = "头像地址不能超过 255 个字符")
    private String avatar;

    /**
     * 性别：0 未知 / 1 男 / 2 女。
     */
    private Integer gender;

    private LocalDate birthday;

    /**
     * 状态：0 禁用 / 1 启用 / 2 锁定，留空时服务端取 1。
     */
    private Integer status;

    private Long deptId;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
