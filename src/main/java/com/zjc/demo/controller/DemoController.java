package com.zjc.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.zjc.demo.service.DemoService;
import com.zjc.demo.web.ApiResponse;

import jakarta.annotation.Resource;

@RestController
public class DemoController {

	@Resource
	private DemoService demoService;

	@GetMapping("/{str}")
	public ApiResponse<String> hello(@PathVariable String str) {
		return demoService.hello(str);
	}
}
