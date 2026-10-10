package com.zjc.demo.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjc.demo.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统用户 Mapper，对应 {@code public.sys_user} 表。自定义 SQL 在
 * {@code resources/mapper/system/SysUserMapper.xml}，靠 namespace 与本接口绑定。
 *
 * <p>
 * 必须标 {@code @Mapper}，漏标不会注册成 Bean，且编译期无任何提示。
 *
 * @author jiancai.zhong
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按关键字、状态、部门做动态条件分页查询。
     *
     * <p>
     * 第一个参数是 {@code IPage} 时分页插件才会改写这条语句（追加 LIMIT/OFFSET 并先跑 COUNT），
     * 所以 XML 里不要自己写 limit。
     *
     * @param page    分页参数，同时接收查询结果
     * @param keyword 登录名 / 昵称 / 手机号关键字，为空不过滤
     * @param status  状态，为空不过滤
     * @param deptId  部门主键，为空不过滤
     * @return 分页结果
     */
    IPage<SysUser> selectByCondition(IPage<SysUser> page, @Param("keyword") String keyword,
                                     @Param("status") Integer status, @Param("deptId") Long deptId);
}
