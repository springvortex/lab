package com.zjc.demo.service.impl;

import org.springframework.stereotype.Service;

import com.zjc.demo.service.HelloService;

/**
 * {@link HelloService} 的默认实现。
 *
 * @author jiancai.zhong
 */
@Service
public class HelloServiceImpl implements HelloService {

	@Override
	public String hello() {
		return "hello SpringVortexDemo!";
	}

}
