package com.zjc.demo.jasypt.controller;

import com.zjc.demo.common.web.ApiResponse;
import com.zjc.demo.jasypt.service.JasyptService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置项加解密接口，典型用途是生成写进配置文件的密文。
 *
 * <p>
 * 生产环境不要暴露：它能拿任意明文换密文且不做鉴权。另外入参是明文、走 GET Query，
 * 会被 access log 记录，加密正式密码时请确保日志不被长期留存。
 *
 * @author jiancai.zhong
 */
@RestController
public class JasyptController {

    @Resource
    private JasyptService jasyptService;

    /**
     * 加密明文，返回值不含 {@code ENC(...)} 前后缀，写进 YAML 时自己补上。
     *
     * @param plainText 待加密的明文，不能为空
     * @return 密文
     */
    @GetMapping("/api/jasypt/encrypt")
    public ApiResponse<String> encrypt(@RequestParam String plainText) {
        return ApiResponse.success(jasyptService.encrypt(plainText));
    }

    /**
     * 解密密文，入参不要带 {@code ENC(...)} 前后缀。
     *
     * @param cipherText 待解密的密文，不能为空
     * @return 明文
     */
    @GetMapping("/api/jasypt/decrypt")
    public ApiResponse<String> decrypt(@RequestParam String cipherText) {
        return ApiResponse.success(jasyptService.decrypt(cipherText));
    }

}
