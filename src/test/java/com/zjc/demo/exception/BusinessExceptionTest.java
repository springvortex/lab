package com.zjc.demo.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.support.TestErrorCodeConstant;

/**
 * {@link BusinessException} 四个构造器的单元测试。
 *
 * <p>
 * 重点是错误码来源：枚举构造要取枚举的码、单参构造要落到默认的 400、显式传码要原样保留
 * （哪怕是非 HTTP 语义的值，兜底交给 {@code ApiResponse} 处理）。
 *
 * @author jiancai.zhong
 */
class BusinessExceptionTest {

    /**
     * 以框架自带的 {@link ApiResponseConstant} 构造。
     */
    @Test
    @DisplayName("枚举构造：取枚举的码与文案")
    void fromApiResponseConstant() {
        BusinessException e = new BusinessException(ApiResponseConstant.NOT_FOUND);

        assertThat(e.getCode()).isEqualTo(404);
        assertThat(e.getMessage()).isEqualTo("资源不存在");
    }

    /**
     * 以业务方自定义的 {@code ErrorCodeConstant} 实现构造，验证扩展点可用。
     */
    @Test
    @DisplayName("自定义 ErrorCodeConstant 构造：扩展点可用")
    void fromCustomErrorCodeConstant() {
        assertThat(new BusinessException(TestErrorCodeConstant.USER_NOT_FOUND).getCode()).isEqualTo(404);
        assertThat(new BusinessException(TestErrorCodeConstant.STOCK_NOT_ENOUGH).getCode()).isEqualTo(409);
        assertThat(new BusinessException(TestErrorCodeConstant.STOCK_NOT_ENOUGH).getMessage()).isEqualTo("库存不足");
    }

    /**
     * 只给文案时，错误码必须是默认的 400。
     */
    @Test
    @DisplayName("单参构造：错误码默认 400")
    void messageOnlyUsesDefaultCode() {
        BusinessException e = new BusinessException("自定义提示");

        assertThat(e.getCode()).isEqualTo(400);
        assertThat(e.getMessage()).isEqualTo("自定义提示");
    }

    /**
     * 显式传码时原样保留，不做合法性校验（非法值的兜底由 ApiResponse 负责）。
     */
    @Test
    @DisplayName("int 构造：错误码原样保留")
    void explicitIntCodeIsKept() {
        assertThat(new BusinessException(422, "无法处理的实体").getCode()).isEqualTo(422);
        assertThat(new BusinessException(10001, "非 HTTP 语义编码").getCode()).isEqualTo(10001);
    }

    /**
     * 以 {@link HttpStatus} 构造，避免手写状态码数字。
     */
    @Test
    @DisplayName("HttpStatus 构造：取 status.value()")
    void fromHttpStatus() {
        BusinessException e = new BusinessException(HttpStatus.CONFLICT, "数据已存在");

        assertThat(e.getCode()).isEqualTo(409);
        assertThat(e.getMessage()).isEqualTo("数据已存在");
    }
}
