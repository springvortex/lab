package com.zjc.demo.common.constant.web;

/**
 * 错误码契约接口。自定义枚举实现本接口后，可直接传给 {@code BusinessException} 与
 * {@code ApiResponse#failure}，无需转换代码。
 *
 * <p>
 * {@code code()} 返回的就是 HTTP 状态码，必须是 100~599 的合法值。
 *
 * @author jiancai.zhong
 */
public interface ErrorCodeConstant {

    /**
     * 返回错误码。
     *
     * @return 错误码，必须是合法 HTTP 状态码
     */
    int code();

    /**
     * 返回面向调用方的错误提示。
     *
     * @return 错误提示，不应包含内部实现细节
     */
    String message();
}
