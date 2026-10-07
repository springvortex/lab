package com.zjc.demo.config;

import java.util.List;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zjc.demo.constant.TraceConstant;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI 文档配置：服务级元信息 + 分模块分组。
 *
 * <p>
 * <b>职责划分（与参考实现一致）：</b>{@link #openAPI()} 只维护<b>服务级元信息</b>
 * （标题、版本、联系人、Servers、公共组件），所有分组共享； 具体的模块拆分由各个 {@link GroupedOpenApi} Bean 负责。
 * 这样加一个业务模块时只动分组，不会碰公共元信息。
 *
 * <p>
 * <b>访问路径：</b>
 * <ul>
 * <li>Swagger UI：{@code /swagger-ui.html}（会跳转到
 * {@code /swagger-ui/index.html}）</li>
 * <li>文档总览（全部接口）：{@code /v3/api-docs}</li>
 * <li>单个分组：{@code /v3/api-docs/{group}}，例如 {@code /v3/api-docs/demo}</li>
 * </ul>
 * Swagger UI 右上角的下拉框可切换分组。
 *
 * <p>
 * <b>加分组的两种方式（按需选一）：</b>
 * <ul>
 * <li>{@code pathsToMatch(...)}：按 URL 前缀分，适合模块已按路径隔离（如
 * {@code /api/order/**}）；</li>
 * <li>{@code packagesToScan(...)}：按包名分，适合路径没有统一前缀、但代码按包分层的情况。</li>
 * </ul>
 * 不想让某个分组出现的路径用 {@code pathsToExclude(...)} 排除。
 *
 * <p>
 * <b>版本对齐（踩过坑）：</b>springdoc 的大版本必须跟随 Spring Boot—— 2.x 对应 Boot 3，3.x 对应 Boot
 * 4。版本号在 {@code pom.xml} 的 {@code springdoc.version} 里显式锁死：springdoc <b>不在</b>
 * Boot 的依赖管理（BOM）里，不锁版本会解析失败； 版本选错不会编译报错，只在运行期表现为文档接口 500 或 ClassNotFound。
 *
 * @author jiancai.zhong
 */
@Configuration
public class OpenApiConfig {

	/**
	 * 服务级文档元信息，所有分组共享。
	 *
	 * <p>
	 * {@code Server.url("/")} 表示「以当前访问地址作为 base URL」， 这样文档在
	 * localhost、测试环境、生产环境都能直接点 Try it out， 不必为每个环境维护一份 server 配置。
	 *
	 * @return OpenAPI 文档元信息
	 */
	@Bean
	OpenAPI openAPI() {
		Server server = new Server().url("/").description("通过当前访问地址调用接口");

		return new OpenAPI().servers(List.of(server))
				.info(new Info().title("SpringVortexDemo API").description("Spring Boot 4 脚手架模板接口文档").version("0.0.1")
						.contact(new Contact().name("jiancai.zhong")).license(new License().name("Apache 2.0")))
				.components(new Components().addSecuritySchemes("traceId",
						new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
								.name(TraceConstant.HEADER_NAME).description("链路追踪 ID，可留空；服务端会自动生成并写回响应头")));
	}

	/**
	 * 示例接口分组：模板自带的演示接口。
	 *
	 * <p>
	 * 派生新项目时，本分组与 {@code controller/HelloController}、 {@code src/test/.../support/}
	 * 下的演示接口一起删除即可。
	 *
	 * @return 示例接口分组
	 */
	@Bean
	GroupedOpenApi demoApi() {
		return GroupedOpenApi.builder().group("demo").displayName("示例接口").pathsToMatch("/hello", "/test/**").build();
	}

	/**
	 * 演示用户分组：PostgreSQL + MyBatis-Plus 集成示例接口。
	 *
	 * <p>
	 * 按 URL 前缀 {@code /api/users/**} 划分，与 {@code DemoUserController} 各方法上写的
	 * 完整路径对齐；新增业务模块时照此加一个分组 Bean 即可。
	 *
	 * @return 演示用户接口分组
	 */
	@Bean
	GroupedOpenApi demoUserApi() {
		return GroupedOpenApi.builder().group("demo-user").displayName("演示用户").pathsToMatch("/api/users/**").build();
	}

	/**
	 * 运维端点分组：健康检查与监控端点。
	 *
	 * <p>
	 * 需要 {@code springdoc.show-actuator=true} 才会把 Actuator 端点收进文档 （该开关默认关闭，已在
	 * {@code application-pub.yaml} 中开启）。
	 *
	 * @return 运维端点分组
	 */
	@Bean
	GroupedOpenApi actuatorApi() {
		return GroupedOpenApi.builder().group("actuator").displayName("运维端点").pathsToMatch("/actuator/**").build();
	}
}
