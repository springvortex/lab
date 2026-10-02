package com.zjc.demo.config;

import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置。
 *
 * <p>
 * <b>Boot 4 变更：</b>定制接口由 Boot 3 的 {@code Jackson2ObjectMapperBuilderCustomizer}
 * 改为 {@link JsonMapperBuilderCustomizer}，且 JSON 库升级到 Jackson 3（包路径
 * {@code tools.jackson.*}，不再是 {@code com.fasterxml.jackson.*}）。
 *
 * <p>
 * 与时区、日期格式相关的项已在 {@code application.yaml} 里用 {@code spring.jackson.*}
 * 声明式配置（改配置不需要动代码），本类只放无法用配置项表达的部分。
 *
 * @author jiancai.zhong
 */
@Configuration
public class JacksonConfig {

    /**
     * 是否把 Long 序列化成字符串。默认开启，可通过 {@code app.jackson.long-to-string} 关闭。
     *
     * <p>
     * 背景：JS 的 Number 是双精度浮点，安全整数范围是 ±(2^53-1)。后端用雪花算法生成的
     * 19 位 Long 型 ID（如 {@code 1627927676485431298}）传到前端会被静默截断成
     * {@code 1627927676485431000}，而且不报错——是前后端联调里极难排查的一类 bug。
     * 统一转成字符串可以从根上规避。
     */
    @Value("${app.jackson.long-to-string:true}")
    private boolean longToStringEnabled;

    /**
     * 注册 Long → String 序列化器。
     *
     * <p>
     * 反序列化方向不受影响：前端传来的数字或字符串都能正常转成 {@code Long}，
     * 因此这是一个<b>单向、向后兼容</b>的改动。
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
