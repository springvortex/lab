package com.zjc.demo.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjc.demo.common.constant.web.ApiResponseConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link ApiResponse} 契约测试：code 即 HTTP 状态码、非法取值兜底 500。纯单元，不起 Spring。
 *
 * @author jiancai.zhong
 */
@DisplayName("ApiResponse 契约")
class ApiResponseTest {

    @Test
    @DisplayName("success() 默认 success=true、code=200")
    void successHasDefaultContract() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo(ApiResponseConstant.SUCCESS.message());
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("resolveStatus：null 与非法值兜底 500")
    void resolveStatusFallsBackTo500() {
        assertThat(ApiResponse.resolveStatus(null)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ApiResponse.resolveStatus(999)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ApiResponse.resolveStatus(404)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("toResponseEntity：HTTP 状态码与 code 一致")
    void responseEntityMatchesCode() {
        ApiResponse<Void> response = ApiResponse.failure(ApiResponseConstant.NOT_FOUND);
        ResponseEntity<ApiResponse<Void>> entity = response.toResponseEntity();

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("failure(HttpStatus) 保留状态码与提示")
    void failureKeepsStatusAndMessage() {
        ApiResponse<Void> response = ApiResponse.failure(HttpStatus.CONFLICT, "登录名已存在");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(409);
        assertThat(response.getMessage()).isEqualTo("登录名已存在");
    }
}
