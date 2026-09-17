package com.zjc.demo.service.impl;

import org.springframework.stereotype.Service;

import com.zjc.demo.service.DemoService;

/**
 * {@link DemoService} 的默认实现。
 *
 * @author jiancai.zhong
 */
@Service
public class DemoServiceImpl implements DemoService {

	@Override
	public String hello(String str) {
		return str;
	}

}
