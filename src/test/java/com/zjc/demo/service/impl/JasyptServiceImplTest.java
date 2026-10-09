package com.zjc.demo.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.exception.BusinessException;
import com.zjc.demo.service.JasyptService;

/**
 * {@link JasyptServiceImpl} 的测试。
 *
 * <p>
 * <b>为什么用 {@code @SpringBootTest} 而不是 new 一个对象：</b>本类的价值全在「jasypt 装配出来的
 * {@code StringEncryptor} 能正常加解密」这件事上，而 {@code StringEncryptor} 的算法、密钥、迭代次数 都来自
 * {@code jasypt.encryptor.*} 配置。手工 new 一个加密器来测，等于把配置这一环排除在外，
 * 而配置写错恰恰是最常见的故障——密钥没注入、算法名拼错、profile 覆盖没生效，都只会在真实装配时暴露。 用 Mockito 打桩能把用例写绿，但
 * {@code @Resource} 注入失败、加密器 Bean 缺失这类问题就完全测不到了。
 *
 * <p>
 * <b>空值用例用手工实例：</b>参数校验发生在调用加密器<b>之前</b>，不需要完整的容器， 手工 new
 * 出来注入一个只会抛异常的桩即可，跑得更快，也不依赖运行环境。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
class JasyptServiceImplTest {

	/**
	 * 容器里真实装配的服务。
	 */
	@Autowired
	private JasyptService jasyptService;

	/**
	 * 明文加密后再解回来，必须与原文完全一致。
	 *
	 * <p>
	 * 这是本类最核心的一条：能加密不代表能解开，密钥或算法在加解密两侧被子配置改歪时， 只有走完整往返才会暴露。
	 */
	@Test
	@DisplayName("往返一致：encrypt 后再 decrypt 得到原文")
	void roundTripReturnsOriginal() {
		String plainText = "my-db-password-2026";

		String cipherText = jasyptService.encrypt(plainText);

		assertThat(cipherText).isNotEqualTo(plainText);
		assertThat(jasyptService.decrypt(cipherText)).isEqualTo(plainText);
	}

	/**
	 * 同一明文连续加密两次，密文必须不同。
	 *
	 * <p>
	 * 默认配了 {@code RandomIvGenerator}，每次加密用随机 IV，因此密文不可比对。这条用例同时钉住了配置： 若哪天有人把 IV
	 * 生成器换成 {@code NoIvGenerator}（或老教程里的 {@code NoIvGenerator} 写法），
	 * 密文就会变得可预测，本用例立刻变红。
	 */
	@Test
	@DisplayName("随机 IV：同一明文两次加密结果不同，但都能解回原文")
	void encryptionIsNotDeterministic() {
		String plainText = "same-input";

		String first = jasyptService.encrypt(plainText);
		String second = jasyptService.encrypt(plainText);

		assertThat(first).isNotEqualTo(second);
		assertThat(jasyptService.decrypt(first)).isEqualTo(plainText);
		assertThat(jasyptService.decrypt(second)).isEqualTo(plainText);
	}

	/**
	 * 中文、符号、空白等非 ASCII 内容同样要能原样往返。
	 *
	 * <p>
	 * 加解密结果默认走 Base64，理论上不受编码影响，但配置里的字符集、控制台的 encoding 都可能在中途插一脚，因此显式验一遍。
	 */
	@Test
	@DisplayName("非 ASCII 内容：中文与符号原样往返")
	void nonAsciiContentRoundTrips() {
		String plainText = "密码 @2026 ！带空格 与 换行";

		String cipherText = jasyptService.encrypt(plainText);

		assertThat(jasyptService.decrypt(cipherText)).isEqualTo(plainText);
	}

	/**
	 * 空值必须在进入加密器前被拦下，并转成 400 的业务异常。
	 *
	 * <p>
	 * 若放任 {@code null} 传到 jasypt，抛的是它自己的 {@code IllegalArgumentException}，
	 * 而项目里没有为它注册处理器，会掉进兜底分支被改写成 500 —— 明明是用错参数，却报成服务端故障。
	 */
	@Test
	@DisplayName("encrypt 空值：抛 400 的业务异常")
	void encryptRejectsBlankInput() {
		JasyptServiceImpl service = serviceWithStubEncryptor();

		assertThatThrownBy(() -> service.encrypt(null)).isInstanceOf(BusinessException.class).hasMessage("待加密的明文不能为空");
		assertThatThrownBy(() -> service.encrypt("")).isInstanceOf(BusinessException.class).hasMessage("待加密的明文不能为空");
	}

	/**
	 * 空密文同样要被拦下，避免把空串交给解密器。
	 */
	@Test
	@DisplayName("decrypt 空值：抛 400 的业务异常")
	void decryptRejectsBlankInput() {
		JasyptServiceImpl service = serviceWithStubEncryptor();

		assertThatThrownBy(() -> service.decrypt(null)).isInstanceOf(BusinessException.class).hasMessage("待解密的密文不能为空");
		assertThatThrownBy(() -> service.decrypt("")).isInstanceOf(BusinessException.class).hasMessage("待解密的密文不能为空");
	}

	/**
	 * 校验失败的状态码必须是 400（参数非法），不能是默认的 400 之外的兜底值。
	 */
	@Test
	@DisplayName("空值校验：状态码为 400")
	void blankInputUsesParameterInvalidCode() {
		JasyptServiceImpl service = serviceWithStubEncryptor();

		assertThatThrownBy(() -> service.encrypt("")).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ApiResponseConstant.PARAM_INVALID.code());
	}

	/**
	 * 构造一个注入了桩加密器的服务实例，只在参数校验路径上使用。
	 *
	 * @return 注入了抛异常桩的 {@link JasyptServiceImpl}
	 */
	private static JasyptServiceImpl serviceWithStubEncryptor() {
		JasyptServiceImpl service = new JasyptServiceImpl();
		StringEncryptor stub = new StringEncryptor() {

			@Override
			public String encrypt(String message) {
				throw new AssertionError("参数校验不通过时不应调用加密器");
			}

			@Override
			public String decrypt(String encryptedMessage) {
				throw new AssertionError("参数校验不通过时不应调用解密器");
			}
		};
		ReflectionTestUtils.setField(service, "jasyptStringEncryptor", stub);
		return service;
	}

}
