package com.zjc.demo.core.aop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link WebLogAspect} 敏感字段打码的纯单元测试。
 *
 * @author jiancai.zhong
 */
@DisplayName("WebLogAspect 敏感字段打码")
class WebLogAspectTest {

    @Test
    @DisplayName("password/phone 等敏感字段整体替换为 ***")
    void maskSensitiveKeys() {
        String json = "{\"username\":\"tom\",\"password\":\"P@ss123\",\"phone\":\"13812345678\"}";

        assertThat(WebLogAspect.maskSensitiveValues(json))
                .isEqualTo("{\"username\":\"tom\",\"password\":\"***\",\"phone\":\"***\"}");
    }

    @Test
    @DisplayName("key 大小写不敏感，嵌套对象同样生效")
    void nestedAndCaseInsensitive() {
        String json = "{\"user\":{\"PassWord\":\"x\",\"TOKEN\":\"y\"},\"remark\":\"ok\"}";

        assertThat(WebLogAspect.maskSensitiveValues(json))
                .isEqualTo("{\"user\":{\"PassWord\":\"***\",\"TOKEN\":\"***\"},\"remark\":\"ok\"}");
    }

    @Test
    @DisplayName("普通 JSON 与长数字 ID 不受影响")
    void normalJsonUntouched() {
        String json = "{\"username\":\"tom\",\"deptId\":1912345678901234567}";

        assertThat(WebLogAspect.maskSensitiveValues(json)).isEqualTo(json);
    }

    @Test
    @DisplayName("null 与空串原样返回")
    void nullAndEmptyUntouched() {
        assertThat(WebLogAspect.maskSensitiveValues(null)).isNull();
        assertThat(WebLogAspect.maskSensitiveValues("")).isEmpty();
    }
}
