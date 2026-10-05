package com.zjc.demo.service.impl;

import org.springframework.stereotype.Service;

import com.zjc.demo.service.HelloService;

/**
 * {@link HelloService} 的默认实现；派生新项目时可直接删除。
 *
 * <p>
 * 当前是占位实现，用于验证「容器装配 → Service → Controller → 统一响应」整条链路是通的。 排查接口 500
 * 时可先确认本实现能否正常返回，以区分是框架装配问题还是业务代码问题。
 *
 * @author jiancai.zhong
 */
@Service
public class HelloServiceImpl implements HelloService {

	/**
	 * 返回一句固定的问候语，不访问数据库、缓存或远程服务。
	 *
	 * @return 固定问候内容
	 */
	@Override
	public String hello() {
		return "hello SpringVortexDemo!";
	}

}
