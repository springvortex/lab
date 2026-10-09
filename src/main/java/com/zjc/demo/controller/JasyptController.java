package com.zjc.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zjc.demo.service.JasyptService;
import com.zjc.demo.web.ApiResponse;

import jakarta.annotation.Resource;

/**
 * 配置项加解密接口，把 jasypt 的加解密能力暴露成 HTTP 服务。
 *
 * <p>
 * 典型用途是<b>生成密文</b>：拿到一个想写进配置文件的新密码，先在本地调一次本接口， 把返回的密文补上 {@code ENC(...)} 前后缀写进
 * YAML，明文就不用进仓库了。
 *
 * <p>
 * ⚠️ <b>生产环境不要暴露本接口。</b>它能拿任意明文换取密文，等于把加密能力对外提供；
 * 密文是可以被解开的，函数本身也不做鉴权。真要在生产提供类似能力，请先补鉴权并限制调用方。
 *
 * <p>
 * ⚠️ <b>GET + Query 参数会进访问日志。</b>本接口的入参是明文，会被 Tomcat 的 access log、 反向代理日志、以及
 * {@code WebLogAspect} 原样记录下来。用它加密正式密码时， 请改用 POST + 请求体，或至少确保这些日志不被长期留存。
 *
 * <p>
 * <b>参数校验放在哪：</b>本类<b>不做</b>参数校验，Controller 只负责收参与包装响应； 「入参不能为空」的判断统一由
 * {@code JasyptServiceImpl} 抛 {@code BusinessException}，再由全局异常处理器转成
 * 400。这样校验规则跟着业务实现走， 换个调用入口（定时任务、MQ 消费者）也不会漏掉。
 *
 * @author jiancai.zhong
 */
@RestController
public class JasyptController {

	/**
	 * 加解密业务服务，实际动作转发给它，Controller 不写业务逻辑。
	 */
	@Resource
	private JasyptService jasyptService;

	/**
	 * 加密明文，返回可写进配置文件的密文。
	 *
	 * <p>
	 * 返回值<b>不含</b> {@code ENC(...)} 前后缀，写进 YAML 时自己补上。
	 *
	 * <pre>{@code
	 * GET /api/jasypt/encrypt?plainText=my-db-password
	 * 200
	 * {"success":true,"code":200,"message":"操作成功","data":"8cXk...==","traceId":"...","timestamp":...}
	 * }</pre>
	 *
	 * @param plainText 待加密的明文，不能为空或纯空白
	 * @return 统一响应封装，{@code data} 为加密后的密文
	 */
	@GetMapping("/api/jasypt/encrypt")
	public ApiResponse<String> encrypt(@RequestParam String plainText) {
		return ApiResponse.success(jasyptService.encrypt(plainText));
	}

	/**
	 * 解密密文，返回原始明文。
	 *
	 * <p>
	 * 入参是纯密文，<b>不要</b>带 {@code ENC(...)} 前后缀。密钥与算法必须与加密时一致， 否则会抛解密异常。
	 *
	 * <pre>{@code
	 * GET /api/jasypt/decrypt?cipherText=8cXk...==
	 * 200
	 * {"success":true,"code":200,"message":"操作成功","data":"my-db-password","traceId":"...","timestamp":...}
	 * }</pre>
	 *
	 * @param cipherText 待解密的密文，不能为空或纯空白
	 * @return 统一响应封装，{@code data} 为解密后的明文
	 */
	@GetMapping("/api/jasypt/decrypt")
	public ApiResponse<String> decrypt(@RequestParam String cipherText) {
		return ApiResponse.success(jasyptService.decrypt(cipherText));
	}

}
