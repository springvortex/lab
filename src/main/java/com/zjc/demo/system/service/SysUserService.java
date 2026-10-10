package com.zjc.demo.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zjc.demo.system.entity.SysUser;

/**
 * 系统用户业务接口，继承 {@code IService} 即获得单表 CRUD。
 *
 * @author jiancai.zhong
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 按关键字、状态、部门做动态条件分页查询，走 XML 自定义 SQL。
     *
     * @param page    分页参数
     * @param keyword 登录名 / 昵称 / 手机号关键字，为空不过滤
     * @param status  状态，为空不过滤
     * @param deptId  部门主键，为空不过滤
     * @return 分页结果，永不为 {@code null}
     */
    IPage<SysUser> searchByCondition(IPage<SysUser> page, String keyword, Integer status, Long deptId);
}
