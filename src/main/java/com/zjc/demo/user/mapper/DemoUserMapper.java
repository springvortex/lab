package com.zjc.demo.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjc.demo.user.entity.DemoUser;

/**
 * 用户 Mapper，对应 {@code demo.demo_user} 表。
 *
 * <p>
 * 继承 {@code BaseMapper} 即获得单表 CRUD 能力，无需写任何实现。自定义 SQL 放在
 * {@code src/main/resources/mapper/DemoUserMapper.xml}，两者通过「XML 的 {@code namespace} == 本接口全限定名」
 * 绑定。
 *
 * <p>
 * ⚠️ <b>必须标 {@code @Mapper}</b>。扫描器的 include 过滤条件是「带本注解的接口」，
 * 漏标会导致该 Mapper 不注册成 Bean，注入时报找不到——但编译期没有任何提示。
 *
 * @author jiancai.zhong
 */
@Mapper
public interface DemoUserMapper extends BaseMapper<DemoUser> {

	/**
	 * 按年龄区间查询未删除用户，按年龄升序。
	 *
	 * <p>
	 * 走 XML 自定义 SQL：{@code resultType} 用短类名（依赖 {@code type-aliases-package}）， 并按用户名做
	 * {@code LIKE} 模糊匹配。
	 *
	 * @param minAge 最小年龄（含）
	 * @param maxAge 最大年龄（含）
	 * @param keyword 用户名关键字，模糊匹配；传 {@code null} 则不参与过滤
	 * @return 匹配的用户列表，可能为空
	 */
	List<DemoUser> selectByAgeRange(@Param("minAge") int minAge, @Param("maxAge") int maxAge,
			@Param("keyword") String keyword);

}
