package com.zjc.demo.support;

/**
 * 测试用字符串工具：补齐「Java 8 没有 {@code String#repeat}」的缺口。
 *
 * <p>
 * 本模板基于 JDK 8（Boot 2.7），而 {@code String#repeat} 是 Java 11 才加入的 API。
 * 有若干测试用例需要构造「超长输入」——例如验证 traceId 截断到 64 位、验证切面把超长返回值
 * 截断到 2000 字符——在 Boot 4 分支里可以直接写 {@code "x".repeat(3000)}，这里只能自己拼。
 *
 * <p>
 * <b>升级到 Java 11+ 时可以直接删掉本类</b>，把调用点换回 {@code String#repeat}。
 * 只放在 {@code src/test} 下，不进生产 class path。
 *
 * @author jiancai.zhong
 */
public final class TestStrings {

    /**
     * 阻止实例化。
     *
     * @throws UnsupportedOperationException 总是抛出，本类不允许实例化
     */
    private TestStrings() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 把 {@code token} 连续重复 {@code count} 次拼接成一个字符串。
     *
     * <p>
     * 语义与 Java 11 的 {@code token.repeat(count)} 完全一致（含 {@code count <= 0} 时返回空串）。
     *
     * @param token 被重复的内容，可为任意字符串（含空串）
     * @param count 重复次数
     * @return 拼接结果
     */
    public static String repeat(String token, int count) {
        StringBuilder builder = new StringBuilder(Math.max(0, token.length() * count));
        for (int i = 0; i < count; i++) {
            builder.append(token);
        }
        return builder.toString();
    }
}
