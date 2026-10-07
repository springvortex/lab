package com.zjc.demo.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zjc.demo.dto.DemoUserAgeGroup;
import com.zjc.demo.entity.DemoUser;

/**
 * 演示用业务接口：继承 {@code IService} 即获得批量操作与 {@code lambda} 链式查询。
 *
 * <p>
 * {@code IService} 是 MP 在 {@code BaseMapper} 之上再包的一层 Service 能力，
 * 除了把单表 CRUD 转授过来，还额外提供 {@code saveBatch}、{@code saveOrUpdate}、
 * {@code lambdaQuery()} / {@code lambdaUpdate()} 这些「Service 层常用但 Mapper 层没有」的方法。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>{@code saveBatch} 默认 1000 条一批，但底层仍是一条条发给数据库。要真正提速，
 * MySQL 必须在 JDBC URL 上带 {@code rewriteBatchedStatements=true}
 * （本项目已在 {@code application-db.yaml} 里带上），否则批量插入与逐条插入速度差别不大；</li>
 * <li>{@code lambdaQuery()} 用方法引用写条件，字段改名时编译期就报错，
 * 比手写 {@code "user_name"} 字符串安全；</li>
 * <li>Service 层不要加 {@code @Transactional} 当默认值。事务应该在真正需要保证原子性的
 * 业务方法上显式标注，全类加事务会把「查一条记录」也拖进事务里，白白占着连接。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
public interface DemoUserService extends IService<DemoUser> {

	/**
	 * 按关键字与最小年龄做动态条件查询，并分页。
	 *
	 * <p>
	 * 走的是 {@code resources/mapper/DemoUserMapper.xml} 里的自定义 SQL，
	 * 这个方法只是把 Mapper 能力暴露给 Controller，保持「Controller 只调 Service」的分层。
	 *
	 * @param page    分页参数（页码、每页条数）
	 * @param keyword 用户名 / 邮箱的模糊匹配关键字，为空时不过滤
	 * @param minAge  最小年龄，为空时不过滤
	 * @return 分页结果，永不为 {@code null}
	 */
	IPage<DemoUser> searchByCondition(IPage<DemoUser> page, String keyword, Integer minAge);

	/**
	 * 按年龄段统计人数。
	 *
	 * @return 各年龄段的人数，按人数倒序；没有数据时为空列表
	 */
	List<DemoUserAgeGroup> ageGroupSummary();
}
