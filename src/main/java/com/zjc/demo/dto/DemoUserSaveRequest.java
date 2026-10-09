package com.zjc.demo.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 修改用户的入参。
 *
 * <p>
 * <b>为什么不直接用实体 {@code DemoUser} 接参数：</b>实体里带了 {@code id}、{@code version}、
 * {@code deleted}、{@code createTime} 这些由服务端掌管的字段。用实体接参等于把「客户端能改哪些字段」
 * 的决定权交给前端——一旦有人传了 {@code version} 或 {@code deleted}，就是在绕过乐观锁与逻辑删除。
 * 入参类只收客户端真正该填的字段，是最小权限原则。
 *
 * <p>
 * <b>使用示例（在 Controller 里转成实体）：</b>
 *
 * <pre>{@code
 * DemoUser user = new DemoUser();
 * BeanUtils.copyProperties(request, user); // 注意 Spring 的参数顺序是（源, 目标）
 * demoUserService.save(user);
 * }</pre>
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li><b>用 {@code BeanUtils.copyProperties} 是按属性名隐式拷贝，没有编译期保护</b>：
 * 入参字段改名、或实体字段改名，都不会报错，只会<b>静默拷不过去</b>（目标字段保持默认值）。
 * 一旦发现「传上来的值没落库」，先核对两边的字段名是否还一致；</li>
 * <li><b>不要用 {@code org.apache.commons.beanutils.BeanUtils}</b>——那个包的
 * {@code copyProperties} 参数顺序是<b>反过来</b>的 {@code (目标, 源)}，抄过来就静默拷反方向， 而且它需要额外引入
 * commons-beanutils 依赖；</li>
 * <li>校验注解要生效，Controller 参数上必须写 {@code @Valid}（或 {@code @Validated}），
 * 漏了注解不会报错，只是<b>静默不校验</b>；</li>
 * <li>请求体上的校验失败抛 {@code MethodArgumentNotValidException}， 方法参数上的校验失败抛
 * {@code HandlerMethodValidationException}，两者都由 {@code GlobalExceptionHandler}
 * 转成 400，不要自己 try-catch；</li>
 * <li>{@code age} 是包装类型 {@code Integer}：用 {@code int} 时「不传」会被默认值 0 吃掉，
 * 无法区分「没传」与「传了 0」。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Data
public class DemoUserSaveRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 用户名，必填，最长 64 个字符。
	 */
	@NotBlank(message = "用户名不能为空")
	@Size(max = 64, message = "用户名不能超过 64 个字符")
	private String username;

	/**
	 * 邮箱，选填；填了就必须符合邮箱格式。
	 *
	 * <p>
	 * {@code @Email} 对 {@code null} 直接放行，所以「选填」语义天然成立，无需额外处理。
	 */
	@Email(message = "邮箱格式不正确")
	@Size(max = 128, message = "邮箱不能超过 128 个字符")
	private String email;

	/**
	 * 年龄，选填，取值 0~150。
	 */
	@Min(value = 0, message = "年龄不能小于 0")
	@Max(value = 150, message = "年龄不能大于 150")
	private Integer age;
}
