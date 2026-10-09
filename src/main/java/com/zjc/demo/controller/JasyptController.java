package com.zjc.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zjc.demo.service.JasyptService;
import com.zjc.demo.web.ApiResponse;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;

/**
 * 配置项加解密接口，演示 jasypt 的工具类如何暴露成 HTTP 服务。
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
 * 入参与返回值的日志由 {@code WebLogAspect} 自动记录，无需手动打日志。
 *
 * <p>
 * <b>参数校验怎么落地的：</b>入参是裸的 {@code @RequestParam}，约束注解直接挂在方法参数上
 * （{@code @NotBlank}）。Spring 6.1 起这类校验由框架默认执行，抛出的是
 * {@code HandlerMethodValidationException}。
 *
 * <p>
 * ⚠️ <b>类上的 {@code @Validated} 会改变提示里参数名的形式</b>：加上它之后，Controller 被 AOP
 * 代理包了一层，方法参数上的 {@code MethodParameters} 信息丢失，校验失败时的提示会变成 <b>方法名限定</b>的
 * {@code encrypt.plainText: 明文不能为空}；去掉它则是裸参数名
 * {@code plainText: 明文不能为空}（2026-10-09 A/B 实测）。两种写法校验都会触发，差别只在文案。
 *
 * @author jiancai.zhong
 */
@Validated
@RestController
public class JasyptController {

	/**
	 * 取自配置文件的加密值，用于验证 {@code ENC(...)} 的透明解密。
	 *
	 * <p>
	 * 配置里写的是 {@code demo: ENC(密文)}，但这里注入到的<b>已经是明文</b>—— jasypt 在
	 * {@code Environment} 层就解开了，业务代码完全无感知。这也是它和 {@code encrypt}
	 * 接口的区别：那边是手动调加密器，这边是自动解。
	 */
	@Value("${demo}")
	private String demo;

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
	 * <p>
	 * 请求与响应示例：
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
	public ApiResponse<String> encrypt(@RequestParam @NotBlank(message = "明文不能为空") String plainText) {
		return ApiResponse.success(jasyptService.encrypt(plainText));
	}

	/**
	 * 解密密文，返回原始明文。
	 *
	 * <p>
	 * 入参是纯密文，<b>不要</b>带 {@code ENC(...)} 前后缀。密钥与算法必须与加密时一致， 否则会抛解密异常。
	 *
	 * <p>
	 * 请求与响应示例：
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
	public ApiResponse<String> decrypt(@RequestParam @NotBlank(message = "密文不能为空") String cipherText) {
		return ApiResponse.success(jasyptService.decrypt(cipherText));
	}

	/**
	 * 读取配置文件里被 {@code ENC(...)} 包裹的加密值，验证「透明解密」是否生效。
	 *
	 * <p>
	 * 与上面两个接口的区别：那两个是<b>手动调加密器</b>，本接口验证的是<b>另一条路径</b>—— 配置项在 {@code Environment}
	 * 层就被 jasypt 解开了，业务代码用 {@code @Value} 取到的 直接是明文，全程无感知。返回的就是解密后的明文本身。
	 *
	 * <p>
	 * 密钥不对时本接口会直接启动失败或取到异常，属于「配置问题」，不是接口问题。
	 *
	 * @return 统一响应封装，{@code data} 为解密后的配置值
	 */
	@GetMapping("/api/jasypt/demo")
	public ApiResponse<String> demo() {
		return ApiResponse.success(demo);
	}

}
