package com.zjc.demo.service.impl;

import org.springframework.stereotype.Service;

import com.zjc.demo.service.DemoService;
import com.zjc.demo.web.ApiResponse;

@Service
public class DemoServiceImpl implements DemoService {

	@Override
	public ApiResponse<String> hello(String str) {
		return ApiResponse.success(str);
	}

}
