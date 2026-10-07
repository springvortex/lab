package com.zjc.demo.controller;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.dto.DemoUserAgeGroup;
import com.zjc.demo.dto.DemoUserSaveRequest;
import com.zjc.demo.entity.DemoUser;
import com.zjc.demo.exception.BusinessException;
import com.zjc.demo.service.DemoUserService;
import com.zjc.demo.web.ApiResponse;
import com.zjc.demo.web.PageResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;

/**
 * 演示用户接口：一套完整的单表 CRUD + 分页，用来验证 PostgreSQL 与 MyBatis-Plus 的集成。
 *
 * <p>
 * 严格遵守模板的三条 Controller 约定：只做「接收参数 → 调用 Service → 包装响应」，
 * 返回值统一用 {@link ApiResponse}，不写 try-catch（异常交给
 * {@code GlobalExceptionHandler}）——例外只有一个：新增接口捕获了
 * {@link DuplicateKeyException}，因为「用户名重复」是用户输入问题，
 * 走全局兜底会被报成 500，详见该方法的说明。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>「资源不存在」用 {@code BusinessException(ApiResponseConstant.NOT_FOUND)} 抛出，
 * 由全局处理器转成 HTTP 404。不要返回 {@code ApiResponse.success(null)}——
 * 那样前端拿到的是 200 + 空数据，无法区分「查不到」与「查到但值为空」；</li>
 * <li>修改接口先 {@code getById} 再 {@code updateById}，而不是直接
 * {@code update(entity)}：前者能区分「记录不存在」（404）与「并发冲突」（409），
 * 后者只能拿到一个 {@code false}；</li>
 * <li>{@code @Version} 乐观锁由 MP 自动追加 {@code WHERE version = ?}，
 * 更新影响行数为 0 即代表版本已被别人改过，这里转成 409 让调用方重试；</li>
 * <li><b>URL 路径完整写在每个方法的注解上，类上不挂 {@code @RequestMapping}。</b>
 * 好处是搜 {@code "/api/users"} 能一步定位到具体方法，不用先看类级前缀再拼字符串。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@RestController
@Slf4j
@Tag(name = "演示用户", description = "PostgreSQL + MyBatis-Plus 集成示例接口")
public class DemoUserController {

	/**
	 * 演示用户服务。
	 *
	 * <p>
	 * 本项目统一使用 {@code @Resource}（按名称注入）而非 {@code @Autowired}（按类型注入），
	 * 这样同一接口存在多个实现时，可通过字段名精确指定目标 Bean。
	 */
	@Resource
	private DemoUserService demoUserService;

	/**
	 * 新增用户，返回新记录的主键（雪花 ID）。
	 *
	 * <p>
	 * <b>这里刻意用 try-catch 包住 {@code save}，而不是交给全局异常兜底。</b>
	 * 用户名重复时数据库抛的是 {@link DuplicateKeyException}（SQLState 23505），
	 * 它既不是 {@link BusinessException} 也不是 Spring 的 4xx 异常，走全局兜底会被当成
	 * 服务端故障报成 <b>500「服务内部错误」</b>——排查方向被带偏，前端也只会弹一句没用的提示。
	 * 而「用户名已被占用」是典型的<u>用户输入问题</u>，理应给出 409 并说清是哪个值冲突。
	 *
	 * <p>
	 * <b>为什么不在插入前先查一次 {@code exists()}</b>：查完到插入之间存在时间窗，
	 * 并发下照样会撞唯一索引，所以「查重」只能减少冲突次数、不能替代冲突处理。
	 * 唯一索引是唯一可靠的防线，这里做的是冲突发生后给出可读的原因。
	 *
	 * @param request 用户入参，用户名必填
	 * @return 统一响应封装，{@code data} 为新记录主键
	 * @throws BusinessException 用户名已存在时抛出，携带 409 与「用户名「xxx」已存在」
	 */
	@PostMapping("/api/users")
	@Operation(summary = "新增用户")
	public ApiResponse<Long> create(@RequestBody @Valid DemoUserSaveRequest request) {
		DemoUser user = new DemoUser();
		// 按属性名拷贝，只覆盖两端同名的字段（username / email / age）
		BeanUtils.copyProperties(request, user);
		try {
			demoUserService.save(user);
		} catch (DuplicateKeyException e) {
			// 原始驱动报错（含约束名）只进日志，不外泄；响应里给调用方看得懂的原因
			log.warn("新增用户失败，用户名已存在: username={}", user.getUsername(), e);
			throw new BusinessException(ApiResponseConstant.CONFLICT.code(), "用户名「" + user.getUsername() + "」已存在");
		}
		return ApiResponse.success(user.getId());
	}

	/**
	 * 查询单个用户，不存在时返回 404。
	 *
	 * @param id 主键
	 * @return 统一响应封装，{@code data} 为用户信息
	 * @throws BusinessException 记录不存在或已被逻辑删除时抛出，携带 404
	 */
	@GetMapping("/api/users/{id}")
	@Operation(summary = "查询用户详情")
	public ApiResponse<DemoUser> detail(@PathVariable Long id) {
		return ApiResponse.success(requireUser(id));
	}

	/**
	 * 分页查询用户，支持按用户名模糊匹配。
	 *
	 * <p>
	 * 分页由 {@code PaginationInnerInterceptor} 拦截改写：它会先发一条 {@code COUNT}
	 * 再发一条带 {@code LIMIT / OFFSET} 的查询，结果里的 {@code total} / {@code pages}
	 * 都已算好，业务侧不需要自己写计数 SQL。
	 *
	 * @param current  页码，从 1 开始
	 * @param size     每页条数，超过 {@code MybatisPlusConfig#MAX_PAGE_SIZE} 会被 MP 截断
	 * @param username 用户名模糊匹配关键字，为空时不过滤
	 * @return 统一响应封装，{@code data} 为分页结果
	 */
	@GetMapping("/api/users")
	@Operation(summary = "分页查询用户")
	public ApiResponse<PageResult<DemoUser>> page(@RequestParam(defaultValue = "1") @Min(1) long current,
			@RequestParam(defaultValue = "10") @Min(1) long size,
			@RequestParam(required = false) String username) {
		LambdaQueryWrapper<DemoUser> wrapper = Wrappers.<DemoUser>lambdaQuery()
				// 条件构造器里传 boolean 开关，为 false 时该条件整段不拼进 SQL，省掉手写 if
				.like(StringUtils.hasText(username), DemoUser::getUsername, username)
				.orderByDesc(DemoUser::getId);
		IPage<DemoUser> page = demoUserService.page(new Page<>(current, size), wrapper);
		return ApiResponse.success(PageResult.of(page));
	}

	/**
	 * 按条件查询用户（自定义 SQL 版本）。
	 *
	 * <p>
	 * 与上面的 {@code /api/users} 的区别：那个走 {@code BaseMapper} 的 {@code page()}，
	 * 条件由 {@code LambdaQueryWrapper} 拼；这个走 {@code resources/mapper/DemoUserMapper.xml}
	 * 里的 {@code selectByCondition}，条件用 {@code <if>} 动态拼。
	 * <b>什么时候该换成自定义 SQL：</b>条件特别多且带分支、要多表 join、要用数据库专属函数
	 * （如 PostgreSQL 的 {@code ILIKE}），或者 Wrapper 拼出来的 SQL 性能不行。
	 *
	 * <p>
	 * 分页参数照常传 {@code IPage}，分页插件会自动改写这条自定义 SQL——
	 * XML 里不需要、也不应该自己写 {@code limit}。
	 *
	 * @param current  页码，从 1 开始
	 * @param size     每页条数
	 * @param keyword  用户名 / 邮箱的模糊匹配关键字，不传则不过滤
	 * @param minAge   最小年龄，不传则不过滤
	 * @return 统一响应封装，{@code data} 为分页结果
	 */
	@GetMapping("/api/users/search")
	@Operation(summary = "按条件查询用户（自定义 SQL）")
	public ApiResponse<PageResult<DemoUser>> search(@RequestParam(defaultValue = "1") @Min(1) long current,
			@RequestParam(defaultValue = "10") @Min(1) long size,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Integer minAge) {
		IPage<DemoUser> page = demoUserService.searchByCondition(new Page<>(current, size), keyword, minAge);
		return ApiResponse.success(PageResult.of(page));
	}

	/**
	 * 按年龄段统计人数（自定义 SQL + 自定义 VO）。
	 *
	 * <p>
	 * 聚合结果不对应任何实体，用 {@code DemoUserAgeGroup} 接。SQL 在
	 * {@code resources/mapper/DemoUserMapper.xml} 的 {@code selectAgeGroupSummary}。
	 *
	 * @return 统一响应封装，{@code data} 为各年龄段的人数
	 */
	@GetMapping("/api/users/age-groups")
	@Operation(summary = "按年龄段统计人数（自定义 SQL）")
	public ApiResponse<List<DemoUserAgeGroup>> ageGroups() {
		return ApiResponse.success(demoUserService.ageGroupSummary());
	}

	/**
	 * 修改用户，带乐观锁校验。
	 *
	 * @param id      主键
	 * @param request 用户入参，用户名必填
	 * @return 统一响应封装，{@code data} 为是否更新成功
	 * @throws BusinessException 记录不存在时抛 404，版本冲突时抛 409
	 */
	@PutMapping("/api/users/{id}")
	@Operation(summary = "修改用户（乐观锁）")
	public ApiResponse<Boolean> update(@PathVariable Long id, @RequestBody @Valid DemoUserSaveRequest request) {
		DemoUser existing = requireUser(id);

		// 直接拷到已落库的实体上：入参只有 username / email / age，同名属性被覆盖，
		// id / version / deleted / 时间字段保持不变，因此乐观锁与逻辑删除不会被绕过
		BeanUtils.copyProperties(request, existing);

		// 影响行数为 0 说明 version 已被并发修改（或记录刚被删除），按冲突处理
		if (!demoUserService.updateById(existing)) {
			throw new BusinessException(ApiResponseConstant.CONFLICT);
		}
		return ApiResponse.success(Boolean.TRUE);
	}

	/**
	 * 删除用户（逻辑删除）。
	 *
	 * <p>
	 * 实体上的 {@code @TableLogic} 让这里实际执行的是
	 * {@code UPDATE demo_user SET deleted = 1 WHERE id = ? AND deleted = 0}，
	 * 数据不会真正消失；后续所有查询也会自动带上 {@code AND deleted = 0}。
	 *
	 * @param id 主键
	 * @return 统一响应封装，{@code data} 为是否删除成功
	 * @throws BusinessException 记录不存在时抛出，携带 404
	 */
	@DeleteMapping("/api/users/{id}")
	@Operation(summary = "删除用户（逻辑删除）")
	public ApiResponse<Boolean> delete(@PathVariable Long id) {
		requireUser(id);
		return ApiResponse.success(demoUserService.removeById(id));
	}

	/**
	 * 按主键取用户，不存在时抛 404。
	 *
	 * <p>
	 * 抽成私有方法是为了让「查详情 / 改 / 删」三处共用同一套不存在时的处理，
	 * 避免某个接口漏判而返回 200 + 空数据。
	 *
	 * @param id 主键
	 * @return 用户信息，保证非 {@code null}
	 * @throws BusinessException 记录不存在或已被逻辑删除时抛出，携带 404
	 */
	private DemoUser requireUser(Long id) {
		DemoUser user = demoUserService.getById(id);
		if (user == null) {
			throw new BusinessException(ApiResponseConstant.NOT_FOUND);
		}
		return user;
	}
}
