package com.zjc.demo.core.config;

import java.util.List;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zjc.demo.common.constant.TraceConstant;

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
 * <li>单个分组：{@code /v3/api-docs/{group}}，例如 {@code /v3/api-docs/jasypt}</li>
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
	 * 配置项加解密分组：jasypt 演示接口。
	 *
	 * <p>
	 * ⚠️ <b>Swagger UI 的下拉框只列分组、不列总览。</b>新写了 Controller 却忘了加分组，接口在
	 * {@code /v3/api-docs}（总览）里是能看到的，但 UI 页面上翻不到——很容易被误判成「接口没注册」。
	 *
	 * <p>
	 * 本分组按<b>包</b>而非路径划分：jasypt 的 Controller 统一放在
	 * {@code com.zjc.demo.jasypt.controller} 下。这是与 {@link #defaultApi()} 的约定——
	 * 默认分组靠 {@code packagesToExclude} 把这个包排掉，所以<b>新增分组时必须同时加进 {@link #defaultApi()}
	 * 的排除清单</b>，否则接口会同时出现在两个分组里。
	 *
	 * @return 加解密接口分组
	 */
	@Bean
	GroupedOpenApi jasyptApi() {
		return GroupedOpenApi.builder().group("jasypt").displayName("配置项加解密")
				.packagesToScan("com.zjc.demo.jasypt.controller").build();
	}

	/**
	 * 默认分组：收纳<b>不属于任何已登记分组</b>的接口。
	 *
	 * <p>
	 * 新写了 Controller 又来不及想清楚归哪个模块时，不用管它——只要不在下面这些包里的接口，都会自动落到本分组， 不会出现「接口写了但
	 * Swagger UI 里翻不到」的情况。
	 *
	 * <p>
	 * <b>划分方式：按包排除，不按路径排除。</b>{@code pathsToExclude} 是字符串前缀匹配，每加一个分组就要回来
	 * 手工同步一次前缀清单， 忘了改就会静默出错；而 {@code packagesToExclude} 与分组 Bean 里的
	 * {@code packagesToScan} 一一对应，改一处、查一处，不会漏。
	 *
	 * <p>
	 * <b>新增业务分组时的三件事：</b>
	 * <ol>
	 * <li>把该模块的 Controller 挪进独立子包，如 {@code com.zjc.demo.order.controller}；</li>
	 * <li>在 {@link #jasyptApi()} 旁边加一个 {@code GroupedOpenApi}
	 * Bean，{@code packagesToScan} 指向该包；</li>
	 * <li><b>回到本方法，把新包名加进
	 * {@code packagesToExclude}</b>——这一步漏了，接口就会在两个分组里各出现一次。</li>
	 * </ol>
	 *
	 * <p>
	 * ⚠️ {@code packagesToExclude} 里的包名必须与分组 Bean 的 {@code packagesToScan}
	 * <b>完全一致</b>。 springdoc 对包名做的是前缀匹配，写 {@code ...jasypt.controller} 会连带排掉
	 * {@code ...jasypt.controller.sub} 之类 的子包，语义正好是我们要的。
	 *
	 * <p>
	 * ⚠️ <b>Actuator 端点不受 {@code packagesToExclude} 约束（实测确认）。</b>springdoc 通过
	 * {@code ActuatorProvider} 单独注入 Actuator 端点，不走 Controller 扫描链，包名过滤对它无效 —— 试过写
	 * {@code org.springframework.boot.actuate.endpoint.web} 排不掉，端点仍同时出现在本分组与
	 * {@link #actuatorApi()} 里。 因此这里改用 {@code pathsToExclude("/actuator/**")}：该前缀由
	 * {@code management.endpoints.web.base-path} 固定（默认
	 * {@code /actuator}），不是天天变的业务路径， 与 {@link #actuatorApi()} 的
	 * {@code pathsToMatch} 成对维护即可。
	 *
	 * @return 未分组接口的兜底分组
	 */
	@Bean
	GroupedOpenApi defaultApi() {
		return GroupedOpenApi.builder().group("default").displayName("未分组接口").pathsToMatch("/**")
				.packagesToExclude("com.zjc.demo.jasypt.controller").pathsToExclude("/actuator/**").build();
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
