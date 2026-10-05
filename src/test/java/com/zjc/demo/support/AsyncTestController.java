package com.zjc.demo.support;

import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zjc.demo.web.ApiResponse;

import jakarta.annotation.Resource;

/**
 * 测试专用接口，用于验证异步线程的链路透传。
 *
 * @author jiancai.zhong
 */
@RestController
@RequestMapping("/test")
public class AsyncTestController {

	/**
	 * 异步服务，用于取得异步线程上的 traceId。
	 */
	@Resource
	private AsyncTraceService asyncTraceService;

	/**
	 * 返回异步线程上的 traceId，供集成测试与请求自身的 traceId 比对。
	 *
	 * @return 成功响应，{@code data} 为异步线程读到的 traceId
	 * @throws Exception 异步任务超时或被中断
	 */
	@GetMapping("/async-trace")
	public ApiResponse<String> asyncTrace() throws Exception {
		return ApiResponse.success(asyncTraceService.currentTraceId().get(5, TimeUnit.SECONDS));
	}
}
