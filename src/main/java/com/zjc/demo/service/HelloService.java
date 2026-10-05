package com.zjc.demo.service;

/**
 * 示例业务接口，用于演示 Service 层的标准写法；派生新项目时可直接删除。
 *
 * <p>
 * <b>分层约定：</b>
 * <ul>
 * <li>接口放在 {@code service} 包，实现放在 {@code service.impl} 包。接口与实现分离便于 Spring
 * 生成事务代理，也便于单元测试时替换实现；</li>
 * <li>业务校验失败请抛 {@code BusinessException}，由全局异常处理器转成标准响应， 不要返回 {@code null}
 * 或自定义错误结构，否则会绕过统一状态码约定；</li>
 * <li>需要事务时在<b>实现类</b>的方法或类上标注 {@code @Transactional}， 标注在接口上对基于 CGLIB
 * 的代理不生效。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
public interface HelloService {

	/**
	 * 返回一句固定的问候语，不依赖任何外部资源。
	 *
	 * @return 问候内容，恒不为 {@code null}
	 */
	public String hello();

}
