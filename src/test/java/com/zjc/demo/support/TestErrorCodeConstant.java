package com.zjc.demo.support;

import com.zjc.demo.constant.ErrorCodeConstant;

/**
 * 测试用自定义错误码枚举，验证 {@link ErrorCodeConstant} 这个扩展点能被业务方正常实现。
 *
 * @author jiancai.zhong
 */
public enum TestErrorCodeConstant implements ErrorCodeConstant {

    /**
     * 模拟「用户不存在」，状态码使用 404
     */
    USER_NOT_FOUND(404, "用户不存在"),

    /**
     * 模拟「库存不足」，状态码使用 409
     */
    STOCK_NOT_ENOUGH(409, "库存不足");

    private final int code;
    private final String message;

    /**
     * 构造自定义错误码。
     *
     * @param code    状态码，需为合法 HTTP 状态码
     * @param message 提示文案
     */
    TestErrorCodeConstant(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * {@inheritDoc}
     *
     * @return 状态码
     */
    @Override
    public int code() {
        return code;
    }

    /**
     * {@inheritDoc}
     *
     * @return 提示文案
     */
    @Override
    public String message() {
        return message;
    }
}
