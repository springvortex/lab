package com.zjc.demo.common.constant.system;

import com.zjc.demo.common.dict.DictItem;
import lombok.Getter;
import java.util.Arrays;

/**
 * 用户状态字典：0 禁用 / 1 启用 / 2 锁定。实体字段是 {@code Integer}，本枚举只做取值与校验。
 *
 * @author jiancai.zhong
 */
@Getter
public enum UserStatus implements DictItem {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用"),
    LOCKED(2, "锁定");

    /**
     * 存库用的数值，与 {@code sys_user.status} 列对应。
     */
    private final int value;

    /**
     * 中文说明。
     */
    private final String label;

    /**
     * 构造枚举常量。
     *
     * @param value 存库数值
     * @param label 中文说明
     */
    UserStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 按存库数值查枚举。
     *
     * @param value 存库数值
     * @return 对应的枚举
     * @throws IllegalArgumentException 数值不在枚举范围内
     */
    public static UserStatus of(int value) {
        return Arrays.stream(values()).filter(status -> status.value == value).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的用户状态: " + value));
    }

    /**
     * 判断存库数值是否合法，供入参校验使用。接收 {@code null}（表单里「不传」是常态）。
     *
     * @param value 存库数值，可为 {@code null}
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(Integer value) {
        return value != null && Arrays.stream(values()).anyMatch(status -> status.value == value);
    }

    /**
     * 拼接全部可选值，用于错误提示。
     *
     * @return 形如 {@code 0 禁用、1 启用、2 锁定} 的说明文本
     */
    public static String describeAll() {
        return Arrays.stream(values()).map(status -> status.value + " " + status.label)
                .reduce((left, right) -> left + "、" + right).orElse("");
    }
}
