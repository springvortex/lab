package com.zjc.demo.core.config;

import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置。时区、日期格式等用 {@code spring.jackson.*} 配置的项不在这里。
 *
 * @author jiancai.zhong
 */
@Configuration
public class JacksonConfig {

    /**
     * 是否把 Long 序列化成字符串，可用 {@code app.jackson.long-to-string} 关闭。
     * 雪花 ID 有 19 位，超出 JS 的安全整数范围会被静默截断。
     */
    @Value("${app.jackson.long-to-string:true}")
    private boolean longToStringEnabled;

    /**
     * 注册 Long → String 序列化器。反序列化方向不受影响，前端传数字或字符串都能解析。
     *
     * @return Jackson 定制器
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> {
            if (longToStringEnabled) {
                SimpleModule longModule = new SimpleModule("LongToStringModule");
                longModule.addSerializer(Long.class, ToStringSerializer.instance);
                longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
                builder.addModule(longModule);
            }
        };
    }
}
