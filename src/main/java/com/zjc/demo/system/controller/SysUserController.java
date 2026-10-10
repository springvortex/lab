package com.zjc.demo.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjc.demo.common.constant.system.UserStatus;
import com.zjc.demo.common.constant.web.ApiResponseConstant;
import com.zjc.demo.common.web.ApiResponse;
import com.zjc.demo.common.web.PageResult;
import com.zjc.demo.core.exception.BusinessException;
import com.zjc.demo.system.dto.SysUserDto;
import com.zjc.demo.system.entity.SysUser;
import com.zjc.demo.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

/**
 * 系统用户接口：CRUD + 分页 + 改状态。
 *
 * <p>
 * 路径完整写在每个方法上，不在类上挂 {@code @RequestMapping}。登录名撞唯一索引时
 * 就地捕获转为 409，否则会走全局兜底被报成 500。
 *
 * @author jiancai.zhong
 */
@RestController
@Slf4j
@Tag(name = "系统用户", description = "sys_user 单表 CRUD 与分页")
public class SysUserController {

    @Resource
    private SysUserService sysUserService;

    /**
     * 新增用户，密码必填。
     *
     * @param sysUserDto 入参
     * @return 新记录主键
     * @throws BusinessException 密码为空抛 400；登录名已存在抛 409
     */
    @PostMapping("/api/sys/users")
    @Operation(summary = "新增用户")
    public ApiResponse<Long> create(@RequestBody @Valid SysUserDto sysUserDto) {
        if (!StringUtils.hasText(sysUserDto.getPassword())) {
            throw new BusinessException(ApiResponseConstant.PARAM_INVALID.code(), "密码不能为空");
        }

        SysUser user = new SysUser();
        // 入参不含 id / deleted / 审计字段，这里拷不到服务端掌管的值
        BeanUtils.copyProperties(sysUserDto, user);
        if (user.getStatus() == null) {
            user.setStatus(UserStatus.ENABLED.getValue());
        }

        try {
            sysUserService.save(user);
        } catch (DuplicateKeyException e) {
            log.warn("新增用户失败，登录名已存在: username={}", user.getUsername(), e);
            throw new BusinessException(ApiResponseConstant.CONFLICT.code(), "登录名「" + user.getUsername() + "」已存在");
        }
        return ApiResponse.success(user.getId());
    }

    /**
     * 查询用户详情。
     *
     * @param id 主键
     * @return 用户信息，不含密码
     * @throws BusinessException 记录不存在抛 404
     */
    @GetMapping("/api/sys/users/{id}")
    @Operation(summary = "查询用户详情")
    public ApiResponse<SysUser> detail(@PathVariable Long id) {
        return ApiResponse.success(withoutPassword(requireUser(id)));
    }

    /**
     * 分页查询用户。
     *
     * @param current 页码，从 1 开始
     * @param size    每页条数
     * @param keyword 登录名 / 昵称 / 手机号关键字，不传不过滤
     * @param status  状态，不传不过滤
     * @param deptId  部门主键，不传不过滤
     * @return 分页结果
     */
    @GetMapping("/api/sys/users")
    @Operation(summary = "分页查询用户")
    public ApiResponse<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") @Min(1) long current,
                                                 @RequestParam(defaultValue = "10") @Min(1) long size, @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status, @RequestParam(required = false) Long deptId) {

        IPage<SysUser> page = sysUserService.searchByCondition(new Page<>(current, size), keyword, status, deptId);
        page.getRecords().forEach(user -> user.setPassword(null));
        return ApiResponse.success(PageResult.of(page));
    }

    /**
     * 修改用户。{@code password} 留空表示不改密码。
     *
     * <p>
     * 本接口没有并发保护：表没有乐观锁字段，同时改同一行是后写覆盖先写。
     *
     * @param id         主键
     * @param sysUserDto 入参
     * @return 是否更新成功
     * @throws BusinessException 记录不存在抛 404；登录名冲突抛 409
     */
    @PutMapping("/api/sys/users/{id}")
    @Operation(summary = "修改用户")
    public ApiResponse<Boolean> update(@PathVariable Long id, @RequestBody @Valid SysUserDto sysUserDto) {
        SysUser existing = requireUser(id);
        String originalPassword = existing.getPassword();

        // 入参覆盖不到 createBy / createTime，它们天然保持原样
        BeanUtils.copyProperties(sysUserDto, existing);
        if (!StringUtils.hasText(sysUserDto.getPassword())) {
            existing.setPassword(originalPassword);
        }

        try {
            if (!sysUserService.updateById(existing)) {
                throw new BusinessException(ApiResponseConstant.CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            log.warn("修改用户失败，登录名已存在: id={}, username={}", id, sysUserDto.getUsername(), e);
            throw new BusinessException(ApiResponseConstant.CONFLICT.code(),
                    "登录名「" + sysUserDto.getUsername() + "」已存在");
        }
        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * 修改用户状态。
     *
     * @param id     主键
     * @param status 0 禁用 / 1 启用 / 2 锁定
     * @return 是否更新成功
     * @throws BusinessException 状态取值非法抛 400；记录不存在抛 404
     */
    @PutMapping("/api/sys/users/{id}/status")
    @Operation(summary = "修改用户状态")
    public ApiResponse<Boolean> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (!UserStatus.isValid(status)) {
            throw new BusinessException(ApiResponseConstant.PARAM_INVALID.code(),
                    "状态取值非法，可选：" + UserStatus.describeAll());
        }

        SysUser existing = requireUser(id);
        existing.setStatus(status);
        if (!sysUserService.updateById(existing)) {
            throw new BusinessException(ApiResponseConstant.CONFLICT);
        }
        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * 删除用户（逻辑删除），行仍在库里。
     *
     * @param id 主键
     * @return 是否删除成功
     * @throws BusinessException 记录不存在抛 404
     */
    @DeleteMapping("/api/sys/users/{id}")
    @Operation(summary = "删除用户（逻辑删除）")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        requireUser(id);
        return ApiResponse.success(sysUserService.removeById(id));
    }

    /**
     * 取用户记录，不存在时抛 404。
     *
     * @param id 主键
     * @return 用户实体
     * @throws BusinessException 记录不存在抛 404
     */
    private SysUser requireUser(Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            throw new BusinessException(ApiResponseConstant.NOT_FOUND);
        }
        return user;
    }

    /**
     * 剥离密码后再返回，密文也不该出现在接口契约里。
     *
     * @param user 用户实体
     * @return 同一个对象，密码已置空
     */
    private SysUser withoutPassword(SysUser user) {
        user.setPassword(null);
        return user;
    }
}
