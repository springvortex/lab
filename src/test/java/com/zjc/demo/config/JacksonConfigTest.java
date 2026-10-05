package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link JacksonConfig} 的单元测试。
 *
 * <p>
 * 开关 {@code app.jackson.long-to-string} 的两个分支都要覆盖：开启时 Long 必须变成带引号的
 * 字符串（规避 JS 精度截断），关闭时必须是裸数字。只测开启分支是不够的——关闭分支一旦坏掉，
 * 排查成本很高，因为它在默认配置下永远不会被执行。
 *
 * @author jiancai.zhong
 */
class JacksonConfigTest {

    /**
     * 构造一个只应用了本配置的 ObjectMapper。
     *
     * @param longToStringEnabled 是否开启 Long 转字符串
     * @return ObjectMapper 实例
     */
    private ObjectMapper buildMapper(boolean longToStringEnabled) {
        JacksonConfig config = new JacksonConfig();
        ReflectionTestUtils.setField(config, "longToStringEnabled", longToStringEnabled);

        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    /**
     * 构造一个含 Long 字段的待序列化对象。
     *
     * @param value Long 值
     * @return 待序列化对象
     */
    private Map<String, Object> payload(Long value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", value);
        return map;
    }

    /**
     * 开启时：包装类型 Long 与基本类型 long 都必须序列化为带引号的字符串。
     */
    @Test
    @DisplayName("开启开关：Long / long 均序列化为字符串")
    void longIsSerializedAsStringWhenEnabled() throws Exception {
        ObjectMapper mapper = buildMapper(true);

        assertThat(mapper.writeValueAsString(payload(1234567890123456789L)))
                .isEqualTo("{\"id\":\"1234567890123456789\"}");
    }

    /**
     * 关闭时：Long 保持裸数字，说明开关能够真正关掉这个自定义序列化器。
     */
    @Test
    @DisplayName("关闭开关：Long 保持裸数字")
    void longStaysNumericWhenDisabled() throws Exception {
        ObjectMapper mapper = buildMapper(false);

        assertThat(mapper.writeValueAsString(payload(123L))).isEqualTo("{\"id\":123}");
    }

    /**
     * 开启时反序列化方向不受影响：字符串与数字都能读回 Long，保证改动是向后兼容的单向变更。
     *
     * @throws Exception 反序列化失败
     */
    @Test
    @DisplayName("反序列化：字符串与数字都能读回 Long")
    void deserializationAcceptsBothForms() throws Exception {
        ObjectMapper mapper = buildMapper(true);

        assertThat(mapper.readValue("{\"id\":\"123\"}", Map.class).get("id")).isEqualTo("123");
        assertThat(mapper.readValue("{\"id\":123}", Map.class).get("id")).isEqualTo(123);
    }

    /**
     * 其他类型不受影响，避免「顺手把所有字段都转成字符串」这类误伤。
     */
    @Test
    @DisplayName("Integer / String 不受影响")
    void otherTypesAreNotAffected() throws Exception {
        ObjectMapper mapper = buildMapper(true);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("count", 42);
        map.put("name", "abc");

        assertThat(mapper.writeValueAsString(map)).isEqualTo("{\"count\":42,\"name\":\"abc\"}");
    }
}
