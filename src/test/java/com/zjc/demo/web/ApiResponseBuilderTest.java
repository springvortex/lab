package com.zjc.demo.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ApiResponse.Builder} 链式构建器的单元测试。
 *
 * <p>
 * 重点是 {@code build()} 的三条分支：{@code code} / {@code message} 为 {@code null} 时必须
 * 保留 {@link ApiResponse} 的默认值（成功时的 200 与默认文案），而不是把字段置空。
 *
 * @author jiancai.zhong
 */
class ApiResponseBuilderTest {

    /**
     * 什么都不设置时，{@code code} 与 {@code message} 必须回落到默认值，{@code success} 为 false。
     */
    @Test
    @DisplayName("build：未设置 code/message 时保留默认值")
    void buildKeepsDefaultsWhenNothingSet() {
        ApiResponse<Object> response = ApiResponse.builder().build();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("操作成功");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
    }

    /**
     * 只设置 code：message 仍走默认值。
     */
    @Test
    @DisplayName("build：只设置 code 时 message 保留默认值")
    void buildWithCodeOnly() {
        ApiResponse<Object> response = ApiResponse.builder().code(201).build();

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("操作成功");
    }

    /**
     * 只设置 message：code 仍走默认值。
     */
    @Test
    @DisplayName("build：只设置 message 时 code 保留默认值")
    void buildWithMessageOnly() {
        ApiResponse<Object> response = ApiResponse.builder().message("自定义文案").build();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("自定义文案");
    }

    /**
     * {@code ok()} 快速初始化成功态，随后可继续链式叠加字段。
     */
    @Test
    @DisplayName("ok()：成功态 + 链式追加 data")
    void okInitializesSuccessAndAllowsChaining() {
        ApiResponse<String> response = ApiResponse.<String>builder().ok().data("payload").build();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("操作成功");
        assertThat(response.getData()).isEqualTo("payload");
    }

    /**
     * {@code fail()} 快速初始化失败态。
     */
    @Test
    @DisplayName("fail()：失败态取 400 与默认失败文案")
    void failInitializesFailure() {
        ApiResponse<String> response = ApiResponse.<String>builder().fail().build();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("操作失败");
    }

    /**
     * 全部 setter 逐个显式设置，且 {@code success(true)} 可单独控制成功标志。
     */
    @Test
    @DisplayName("逐个 setter：success/code/message/data 全部生效")
    void allSettersAreApplied() {
        ApiResponse<Integer> response = ApiResponse.<Integer>builder()
                .success(true)
                .code(206)
                .message("部分内容")
                .data(7)
                .build();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(206);
        assertThat(response.getMessage()).isEqualTo("部分内容");
        assertThat(response.getData()).isEqualTo(7);
    }

    /**
     * {@code code(null)} 与 {@code message(null)} 等价于未设置，必须回落到默认值而非置空。
     */
    @Test
    @DisplayName("build：显式传 null 等价于未设置")
    void buildWithExplicitNullsFallsBackToDefaults() {
        ApiResponse<Object> response = ApiResponse.builder().code(null).message(null).build();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("操作成功");
    }
}
