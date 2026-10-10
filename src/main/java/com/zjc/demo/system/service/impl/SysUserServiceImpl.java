package com.zjc.demo.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zjc.demo.system.entity.SysUser;
import com.zjc.demo.system.mapper.SysUserMapper;
import com.zjc.demo.system.service.SysUserService;
import org.springframework.stereotype.Service;

/**
 * {@link SysUserService} 的实现，需要 Mapper 时直接用父类的 {@code baseMapper}。
 *
 * @author jiancai.zhong
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /**
     * {@inheritDoc}
     *
     * @param page    分页参数
     * @param keyword 登录名 / 昵称 / 手机号关键字，为空不过滤
     * @param status  状态，为空不过滤
     * @param deptId  部门主键，为空不过滤
     * @return 分页结果
     */
    @Override
    public IPage<SysUser> searchByCondition(IPage<SysUser> page, String keyword, Integer status, Long deptId) {
        return baseMapper.selectByCondition(page, keyword, status, deptId);
    }
}
