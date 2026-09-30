package com.zjc.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zjc.demo.service.HelloService;
import com.zjc.demo.web.ApiResponse;

import jakarta.annotation.Resource;

/**
 * 示例接口，负责将 Service 返回的业务数据包装为统一响应。
 *
 * @author jiancai.zhong
 */
@RestController
public class HelloController {

	@Resource
	private HelloService demoService;

	/**
	 * 返回问候内容。
	 *
	 * @param str 输入内容
	 * @return 统一响应封装，data 为问候内容
	 */
	@GetMapping("/hello")
	public ApiResponse<String> hello() {
		return ApiResponse.success(demoService.hello());
	}
}
