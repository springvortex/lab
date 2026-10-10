package com.zjc.demo.jasypt.service.impl;

import com.zjc.demo.common.constant.web.ApiResponseConstant;
import com.zjc.demo.core.exception.BusinessException;
import com.zjc.demo.jasypt.service.JasyptService;
import org.jasypt.encryption.StringEncryptor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * {@link JasyptService} 的默认实现，直接调用 jasypt 装配好的 {@link StringEncryptor}。
 *
 * <p>
 * 配置文件里写 {@code ENC(密文)} 时 jasypt 会在 Environment 层透明解密，不需要本类；
 * 本类是给「需要主动生成密文」或「运行期才拿到的敏感值」用的。算法与密钥全部来自
 * {@code jasypt.encryptor.*} 配置。同一个明文每次加密结果都不同（随机 IV），因此密文不能做等值查询。
 *
 * @author jiancai.zhong
 */
@Service
public class JasyptServiceImpl implements JasyptService {

    /**
     * jasypt 提供的加解密器，由 starter 按配置自动装配。
     */
    @Resource
    private StringEncryptor jasyptStringEncryptor;

    /**
     * {@inheritDoc}
     *
     * @param plainText 待加密的明文，不能为空
     * @return Base64 编码的密文，不含 {@code ENC(...)} 前后缀
     * @throws BusinessException 明文为空时抛 400
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
     * @param cipherText 待解密的密文，不能带 {@code ENC(...)} 前缀，不能为空
     * @return 明文
     * @throws BusinessException 密文为空时抛 400
     */
    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new BusinessException(ApiResponseConstant.PARAM_INVALID.code(), "待解密的密文不能为空");
        }
        return jasyptStringEncryptor.decrypt(cipherText);
    }

}
