package com.zjc.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文冒烟测试：验证 Spring 容器与全部自动配置能正常启动。
 *
 * <p>
 * 它是模板的安全网。派生新项目、升级 Spring Boot 版本、增删依赖后先跑它，
 * 能第一时间暴露配置错误、Bean 冲突、依赖缺失这类问题，避免带着坏上下文继续开发。
 *
 * <p>
 * <b>注意：</b>{@code @SpringBootTest} 默认加载<b>完整应用上下文</b>，包含真实数据源等外部依赖，
 * 启动慢且对外部环境有要求。只测 Controller 或单层逻辑时，请改用
 * {@code @WebMvcTest}、{@code @DataJpaTest} 这类切片测试，或引入 Testcontainers 做依赖隔离。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
class DemoApplicationTests {

    /**
     * 断言应用上下文可成功加载；上下文启动失败时本用例即失败。
     */
    @Test
    void contextLoads() {
    }

}
