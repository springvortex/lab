package com.zjc.demo.jasypt.service;

/**
 * 加解密业务接口，把 jasypt 的加解密能力接进标准分层。
 *
 * <p>
 * <b>分层约定：</b>
 * <ul>
 * <li>接口放在 {@code service} 包，实现放在 {@code service.impl} 包；</li>
 * <li>Controller 只做参数接收与响应包装，加解密动作落在实现类里；</li>
 * <li>参数非法请抛 {@code BusinessException}，由全局异常处理器转成标准响应。</li>
 * </ul>
 *
 * <p>
 * 接口与实现分开，是为了让 Controller 依赖稳定的抽象而不是具体的加密组件—— 将来要换成 KMS、Vault
 * 之类的远端密钥服务时，只需替换实现，Controller 与测试都不用动。
 *
 * @author jiancai.zhong
 */
public interface JasyptService {

	/**
	 * 加密明文。
	 *
	 * @param plainText 待加密的明文，不能为空
	 * @return 加密后的密文（不含 {@code ENC(...)} 前后缀）
	 */
	String encrypt(String plainText);

	/**
	 * 解密密文。
	 *
	 * @param cipherText 待解密的密文，不能为空
	 * @return 解密后的明文
	 */
	String decrypt(String cipherText);

}
