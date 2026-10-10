package com.zjc.demo.jasypt.service;

/**
 * 加解密业务接口。接口与实现分开，将来换成 KMS、Vault 之类的远端密钥服务时只需替换实现。
 *
 * @author jiancai.zhong
 */
public interface JasyptService {

    /**
     * 加密明文。
     *
     * @param plainText 待加密的明文，不能为空
     * @return 密文，不含 {@code ENC(...)} 前后缀
     */
    String encrypt(String plainText);

    /**
     * 解密密文。
     *
     * @param cipherText 待解密的密文，不能为空
     * @return 明文
     */
    String decrypt(String cipherText);

}
