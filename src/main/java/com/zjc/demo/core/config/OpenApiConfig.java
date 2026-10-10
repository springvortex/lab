package com.zjc.demo.core.config;

import com.zjc.demo.common.constant.web.TraceConstant;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置：服务级元信息 + 按模块分组。
 *
 * <p>
 * 新增分组时别忘了把包名同步加进 {@link #defaultApi()} 的排除清单，否则接口会出现在两个分组里。
 *
 * @author jiancai.zhong
 */
@Configuration
public class OpenApiConfig {

	/**
	 * 系统 API 分组的显示名，同时也是打开 Swagger UI 时默认选中的分组。
	 */
	private static final String SYS_USER_GROUP_DISPLAY_NAME = "系统 API";

	@Resource
	private SwaggerUiConfigProperties swaggerUiConfigProperties;

	/**
	 * 指定 Swagger UI 的默认分组。
	 *
	 * <p>
	 * 值是分组的 {@code displayName} 而非 group id：springdoc 只把该值透传给前端，
	 * 由 Swagger UI 拿它去匹配 {@code urls[].name}，而那个 name 取的是 displayName。
	 */
	@PostConstruct
	void applyPrimaryGroup() {
		swaggerUiConfigProperties.setUrlsPrimaryName(SYS_USER_GROUP_DISPLAY_NAME);
	}

	/**
	 * 服务级元信息，所有分组共享。
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
	 * 配置项加解密分组。
	 *
	 * @return 加解密接口分组
	 */
	@Bean
	GroupedOpenApi jasyptApi() {
		return GroupedOpenApi.builder().group("jasypt").displayName("配置项加解密")
				.packagesToScan("com.zjc.demo.jasypt.controller").build();
	}

	/**
	 * 系统用户分组。
	 *
	 * @return 系统用户接口分组
	 */
	@Bean
	GroupedOpenApi sysUserApi() {
		return GroupedOpenApi.builder().group("system").displayName(SYS_USER_GROUP_DISPLAY_NAME)
				.packagesToScan("com.zjc.demo.system.controller").build();
	}

	/**
	 * 兜底分组，收纳不属于任何已登记分组的接口。Actuator 端点不认包名过滤，只能按路径排除。
	 *
	 * @return 未分组接口的兜底分组
	 */
	@Bean
	GroupedOpenApi defaultApi() {
		return GroupedOpenApi.builder().group("default").displayName("未分组接口").pathsToMatch("/**")
				.packagesToExclude("com.zjc.demo.jasypt.controller", "com.zjc.demo.system.controller")
				.pathsToExclude("/actuator/**").build();
	}

	/**
	 * 运维端点分组，需要 {@code springdoc.show-actuator=true} 才会收录。
	 *
	 * @return 运维端点分组
	 */
	@Bean
	GroupedOpenApi actuatorApi() {
		return GroupedOpenApi.builder().group("actuator").displayName("运维端点").pathsToMatch("/actuator/**").build();
	}
}
