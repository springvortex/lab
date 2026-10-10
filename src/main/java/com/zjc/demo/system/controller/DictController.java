package com.zjc.demo.system.controller;

import com.zjc.demo.common.constant.web.ApiResponseConstant;
import com.zjc.demo.common.dict.DictItemVo;
import com.zjc.demo.common.dict.DictRegistry;
import com.zjc.demo.common.web.ApiResponse;
import com.zjc.demo.core.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典接口：把枚举字典暴露给前端，避免下拉框选项在前后端各维护一份。
 *
 * @author jiancai.zhong
 */
@RestController
@Tag(name = "字典", description = "枚举字典查询")
public class DictController {

	@Resource
	private DictRegistry dictRegistry;

	/**
	 * 列出全部字典类型。
	 *
	 * @return 字典类型列表
	 */
	@GetMapping("/api/sys/dicts")
	@Operation(summary = "列出全部字典类型")
	public ApiResponse<List<String>> types() {
		return ApiResponse.success(dictRegistry.types());
	}

	/**
	 * 查询某个字典的全部选项。
	 *
	 * @param type 字典类型，如 {@code gender}、{@code user-status}
	 * @return 字典项列表
	 * @throws BusinessException 字典类型不存在时抛 404
	 */
	@GetMapping("/api/sys/dicts/{type}")
	@Operation(summary = "查询字典项")
	public ApiResponse<List<DictItemVo>> items(@PathVariable String type) {
		if (!dictRegistry.exists(type)) {
			throw new BusinessException(ApiResponseConstant.NOT_FOUND.code(), "字典类型不存在: " + type);
		}
		return ApiResponse.success(dictRegistry.items(type));
	}
}
