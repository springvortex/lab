package com.zjc.demo.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zjc.demo.dto.DemoUserAgeGroup;
import com.zjc.demo.entity.DemoUser;
import com.zjc.demo.mapper.DemoUserMapper;
import com.zjc.demo.service.DemoUserService;

/**
 * 演示用业务实现：继承 {@code ServiceImpl} 后，{@code IService} 的方法全部有了默认实现。
 *
 * <p>
 * 这是 MyBatis-Plus 的推荐写法——{@code ServiceImpl<M, T>} 已经把
 * {@code baseMapper} 注入好并实现完所有单表方法，因此实现类里<b>通常是空的</b>。
 * 只有当某个操作需要跨表、加业务规则或显式事务时，才在这里写方法。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>泛型顺序是 {@code <Mapper, Entity>}，写反了编译期就会报错，问题不大；
 * 容易出错的是<b>继承了 {@code ServiceImpl} 却忘了写 {@code implements XxxService}</b>，
 * 那样注入接口时会找不到实现类；</li>
 * <li>需要用到 {@code baseMapper} 时直接访问父类字段即可，不必再注入一次 Mapper——
 * 下面两个自定义查询就是直接用 {@code baseMapper} 调的；</li>
 * <li>本类只做「演示可跑通」，不含任何跨表业务逻辑，派生项目时请按真实业务替换。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@Service
public class DemoUserServiceImpl extends ServiceImpl<DemoUserMapper, DemoUser> implements DemoUserService {

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 直接转调 Mapper 上的自定义方法。这里没有额外逻辑，但仍然值得单独成方法：
	 * Controller 只依赖 Service 接口，将来要加缓存、权限过滤或降级时，改动落在这里，
	 * Controller 一行都不用动。
	 *
	 * @param page    分页参数（页码、每页条数）
	 * @param keyword 用户名 / 邮箱的模糊匹配关键字，为空时不过滤
	 * @param minAge  最小年龄，为空时不过滤
	 * @return 分页结果，永不为 {@code null}
	 */
	@Override
	public IPage<DemoUser> searchByCondition(IPage<DemoUser> page, String keyword, Integer minAge) {
		return baseMapper.selectByCondition(page, keyword, minAge);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return 各年龄段的人数，按人数倒序；没有数据时为空列表
	 */
	@Override
	public List<DemoUserAgeGroup> ageGroupSummary() {
		return baseMapper.selectAgeGroupSummary();
	}
}
