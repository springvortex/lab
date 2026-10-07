package com.zjc.demo.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjc.demo.dto.DemoUserAgeGroup;
import com.zjc.demo.entity.DemoUser;

/**
 * 演示用 Mapper：继承 {@code BaseMapper} 即获得一整套单表 CRUD。
 *
 * <p>
 * 接口里<b>什么都不用写</b>——{@code insert / deleteById / updateById / selectById /
 * selectList / selectPage} 等方法由 {@code BaseMapper} 提供，MP 在启动时按实体上的注解
 * 生成对应 SQL。这就是 MP 相对原生 MyBatis 的主要收益：单表操作零 XML、零注解 SQL。
 *
 * <p>
 * <b>什么时候才需要在这里加方法：</b>
 * <ol>
 * <li>多表关联、聚合统计这类 {@code BaseMapper} 表达不了的查询；</li>
 * <li>需要手写 {@code @Select} / {@code @Update} 覆盖默认行为。</li>
 * </ol>
 * 加方法后把 SQL 写进 {@code resources/mapper/DemoUserMapper.xml}（已被
 * {@code mybatis-plus.mapper-locations} 扫描），或用 MyBatis 注解写在这里。
 *
 * <p>
 * <b>注意事项：</b>接口不需要标 {@code @Mapper}，扫描由
 * {@code MybatisPlusConfig} 上的 {@code @MapperScan("com.zjc.demo.mapper")} 统一完成；
 * 重复标注不会报错，但会让新人搞不清到底哪个在生效。
 *
 * @author jiancai.zhong
 */
public interface DemoUserMapper extends BaseMapper<DemoUser> {

	/**
	 * 按关键字与最小年龄做动态条件查询，并分页。
	 *
	 * <p>
	 * SQL 在 {@code resources/mapper/DemoUserMapper.xml} 的
	 * {@code selectByCondition} 里。<b>第一个参数必须是 {@code IPage}</b>：
	 * 分页插件靠它识别「这条要分页」，随后自动追加 {@code LIMIT / OFFSET}
	 * 并先跑一条 {@code COUNT} 把 {@code total} 填好——XML 里不要自己写 limit，
	 * 写了会和插件打架，表现为分页结果莫名其妙。
	 *
	 * <p>
	 * 两个提醒：
	 * <ul>
	 * <li>多参数必须标 {@code @Param}，否则 XML 里只能用 {@code #{param1}} / {@code #{arg0}}
	 * 这种下标引用，字段一改名就全乱；</li>
	 * <li>手写 SQL <b>不会</b>自动追加逻辑删除条件，{@code AND deleted = 0} 要自己写。</li>
	 * </ul>
	 *
	 * @param page    分页参数（页码、每页条数），同时接收查询结果
	 * @param keyword 用户名 / 邮箱的模糊匹配关键字，为空时不过滤
	 * @param minAge  最小年龄，为空时不过滤
	 * @return 分页结果，{@code records} 为当前页数据
	 */
	IPage<DemoUser> selectByCondition(IPage<DemoUser> page, @Param("keyword") String keyword,
			@Param("minAge") Integer minAge);

	/**
	 * 按年龄段统计人数。
	 *
	 * <p>
	 * 演示「自定义 SQL + 自定义 VO」：聚合结果没有对应的表，用一个 VO 接即可，
	 * 列名 {@code age_group} / {@code user_count} 会按驼峰规则映射到
	 * {@code DemoUserAgeGroup} 的同名字段。
	 *
	 * @return 各年龄段的人数，按人数倒序；没有数据时为空列表
	 */
	List<DemoUserAgeGroup> selectAgeGroupSummary();
}
