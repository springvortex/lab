package com.zjc.demo.common.constant.system;

import lombok.Getter;
import java.util.Arrays;

/**
 * 性别字典：0 未知 / 1 男 / 2 女。实体字段是 {@code Integer}，本枚举只做取值与校验。
 *
 * @author jiancai.zhong
 */
@Getter
public enum Gender {

    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女");

    /**
     * 存库用的数值，与 {@code sys_user.gender} 列对应。
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
    Gender(int value, String label) {
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
    public static Gender of(int value) {
        return Arrays.stream(values()).filter(gender -> gender.value == value).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的性别取值: " + value));
    }

    /**
     * 判断存库数值是否合法，供入参校验使用。
     *
     * @param value 存库数值，可为 {@code null}
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(Integer value) {
        return value != null && Arrays.stream(values()).anyMatch(gender -> gender.value == value);
    }
}
