package com.zjc.demo.jasypt.service.impl;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;

import com.zjc.demo.common.constant.ApiResponseConstant;
import com.zjc.demo.core.exception.BusinessException;
import com.zjc.demo.jasypt.service.JasyptService;

import jakarta.annotation.Resource;

/**
 * {@link JasyptService} 的默认实现，直接调用 jasypt 装配好的 {@link StringEncryptor} 完成加解密。
 *
 * <p>
 * <b>它和 {@code ENC(...)} 是什么关系：</b>配置文件里写 {@code ENC(密文)} 时，jasypt 会在
 * {@code Environment} 层透明解密，业务代码取到的直接是明文、完全无感知。<b>那个场景不需要本类。</b> 本类解决的是另外几个场景：
 * <ul>
 * <li>需要<b>生成</b>密文——比如把一个新密码写进配置文件之前，先拿到它的 {@code ENC(...)} 内容；</li>
 * <li>拿到的是<b>运行期才出现的值</b>（用户输入、第三方回调、数据库里读出来的敏感字段）， 想在落库或转发前先加密；</li>
 * <li>需要把已存的密文<b>解回明文</b>再使用（例如存量系统里密码是密文存的）。</li>
 * </ul>
 *
 * <p>
 * 使用的算法、密钥、迭代次数等全部来自 {@code jasypt.encryptor.*} 配置，本类不含任何硬编码参数：
 * 密文由谁加密、就能被谁解开，前提是两边的配置一致。
 *
 * <p>
 * <b>使用示例：</b>
 *
 * <pre>{@code
 * String cipherText = jasyptService.encrypt("my-db-password");
 * // 把 cipherText 写进配置文件：spring.datasource.password=ENC(<cipherText>)
 *
 * String plainText = jasyptService.decrypt(cipherText);
 * }</pre>
 *
 * <p>
 * ⚠️ <b>同一个明文每次加密结果都不一样</b>（默认配了 {@code RandomIvGenerator}，每次随机 IV）。
 * 这是刻意为之——密文不可比对，攻击者无法通过观察两次密文是否相同来推断明文是否相同。
 * 代价是<b>密文不能拿来做等值查询或去重</b>，需要按明文匹配时请存明文的哈希而非密文。
 *
 * @author jiancai.zhong
 */
@Service
public class JasyptServiceImpl implements JasyptService {

	/**
	 * jasypt 提供的加解密器，由 starter 按 {@code jasypt.encryptor.*} 配置自动装配。
	 *
	 * <p>
	 * 用 {@code @Resource} 按类型名注入，与项目其他位置的注入风格一致。
	 */
	@Resource
	private StringEncryptor jasyptStringEncryptor;

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 返回值<b>不含</b> {@code ENC(...)} 前后缀，写进配置文件时要自己补上：
	 * {@code spring.datasource.password=ENC(<返回值>)}。前后缀不在这里拼，是为了让返回值保持
	 * 「纯密文」语义——既可以拼进 YAML，也可以直接存进数据库字段，不由本类替调用方决定用途。
	 *
	 * @param plainText 待加密的明文，不能为空
	 * @return Base64 编码的密文
	 * @throws BusinessException 明文为 {@code null} 或纯空白时抛出，状态码 400
	 */
	@Override
	public String encrypt(String plainText) {
		if (plainText == null || plainText.isBlank()) {
			throw new BusinessException(ApiResponseConstant.PARAM_INVALID.code(), "待加密的明文不能为空");
		}
		return jasyptStringEncryptor.encrypt(plainText);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 入参是纯密文，<b>不要</b>带 {@code ENC(...)} 前后缀——那层包装是给 {@code Environment} 识别的， 直接传给
	 * {@link StringEncryptor} 会因为首尾字符不合法而解密失败。
	 *
	 * @param cipherText 待解密的密文，不能为空
	 * @return 解密后的明文
	 * @throws BusinessException 密文为 {@code null} 或纯空白时抛出，状态码 400
	 */
	@Override
	public String decrypt(String cipherText) {
		if (cipherText == null || cipherText.isBlank()) {
			throw new BusinessException(ApiResponseConstant.PARAM_INVALID.code(), "待解密的密文不能为空");
		}
		return jasyptStringEncryptor.decrypt(cipherText);
	}

}
