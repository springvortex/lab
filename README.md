# Spring Boot 4 脚手架模板

> 本 README 位于 `sample/boot4-jdk25` 分支。这里是一切新项目的起点，本身不承载任何业务。

## 技术栈

| 项           | 版本 / 选型                                                                       |
|-------------|-------------------------------------------------------------------------------|
| Spring Boot | 4.1.1（Spring Framework 7.0.x）                                                 |
| JDK         | 25（Boot 4 支持 17 ~ 26）                                                         |
| Web         | `spring-boot-starter-webmvc` + Tomcat（Servlet 6.1 / Jakarta EE 11）            |
| JSON        | Jackson 3（`tools.jackson.*`）                                                  |
| 校验          | `spring-boot-starter-validation`（Hibernate Validator 9 / Bean Validation 3.1） |
| AOP         | `spring-boot-starter-aspectj`                                                 |
| 测试          | `spring-boot-starter-webmvc-test`（JUnit 6 / AssertJ 3 / Mockito 5）            |
| 工具          | Lombok 1.18.x、`spring-boot-devtools`（optional）                                |
| 并发          | 虚拟线程，**默认开启**（`spring.threads.virtual.enabled=true`，需 Java 21+）               |
| 数据库        | PostgreSQL 17 + HikariCP（Boot 默认连接池），驱动 `org.postgresql:postgresql`      |
| ORM          | MyBatis-Plus 3.5.17（Boot 4 专用 starter + `mybatis-plus-jsqlparser`）          |

> Boot 4 把 `spring-boot-starter-web` 改名为 `spring-boot-starter-webmvc`；测试依赖也按技术栈拆开了，
> 用 `-webmvc-test` 即会自动带进 `spring-boot-starter-test`（已通过 `mvn dependency:tree` 核实），无需重复声明。

## 目录结构

```
src/main/java/com/zjc/demo/
├── DemoApplication.java          # 启动类
├── aop/WebLogAspect.java         # Controller 环绕日志切面
├── config/
│   ├── WebConfig.java            # WebMvcConfigurer：跨域 + 拦截器扩展点
│   ├── JacksonConfig.java        # Jackson 3 定制：Long → String
│   ├── MybatisPlusConfig.java    # @MapperScan + 分页/乐观锁拦截器链
│   └── MybatisPlusMetaObjectHandler.java  # create_time / update_time 自动填充
├── controller/
│   ├── HelloController.java      # 示例，派生时可删
│   └── DemoUserController.java   # 示例：单表 CRUD + 分页，派生时可删
├── dto/
│   ├── DemoUserSaveRequest.java  # 入参（不用实体接参），示例，派生时可删
│   └── DemoUserAgeGroup.java     # 自定义 SQL 的返回 VO，示例，派生时可删
├── entity/DemoUser.java          # 实体：@TableId / @Version / @TableLogic，示例
├── mapper/DemoUserMapper.java    # Mapper：继承 BaseMapper 即有一整套单表 CRUD，示例
├── service/DemoUserService(+Impl)  # 继承 IService / ServiceImpl，示例，派生时可删
├── constant/
│   ├── ApiResponseConstant.java  # 标准响应码枚举（code 即 HTTP 状态码）
│   ├── ErrorCodeConstant.java    # 错误码契约接口（业务方枚举实现它）
│   └── TraceConstant.java        # traceId 常量：MDC key、请求/响应头名
├── exception/
│   ├── BusinessException.java    # 业务异常（code 即 HTTP 状态码）
│   └── GlobalExceptionHandler.java  # 全局异常处理（@RestControllerAdvice）
├── filter/TraceIdFilter.java     # traceId 生成/沿用 + 写入 MDC
├── web/
│   ├── ApiResponse.java          # 统一响应封装，可 toResponseEntity() 带状态码返回
│   └── PageResult.java           # 分页响应结构，替代直接序列化 IPage
└── service/HelloService(+Impl)      # 示例，派生时可删
src/main/resources/
├── application.yaml              # 主配置（profile 在此激活）
├── config/application-pub.yaml   # pub 附加 profile：公共配置（超时 / 跨域 / Jackson / Actuator / springdoc）
├── config/application-db.yaml    # db 附加 profile：数据源 + HikariCP + MyBatis-Plus（见下文）
├── db/schema.sql                 # 建表脚本（幂等，手动执行一次）
├── mapper/DemoUserMapper.xml     # 自定义 SQL（BaseMapper 表达不了的查询写这里）
└── logback-spring.xml            # 日志（按级别分文件 + 异步 + 180 天滚动 + 生产落控制台）

LICENSE                           # MIT，派生新项目时记得替换版权持有人
lombok.config                     # 让 JaCoCo 忽略 Lombok 生成的方法
```

配置文件这样分层，是为了让「同一个 key 只写一处」：

| 文件                                 | 放什么                                     |
|------------------------------------|-----------------------------------------|
| `application.yaml`                 | 端口、应用名、profile 激活（`active` + `include`） |
| `application-pub.yaml`             | 与环境无关的公共项，被所有环境 `include` 叠加            |
| `application-{dev,test,prod}.yaml` | **只放差异**；实测优先级高于 `application-pub.yaml` |

### 响应约定：code 即 HTTP 状态码

模板采用「body 里的 `code` 与 HTTP 状态码完全一致」的方案，而不是业界另一种「HTTP 恒 200、错误全放
body」的做法。好处是网关重试、前端拦截器、APM 告警都能按标准状态码工作：

```json
{
  "success": false,
  "code": 404,
  "message": "资源不存在",
  "data": null,
  "traceId": "06e9610c586245f3af21807231411a75",
  "timestamp": 1790761655561
}
```

| 场景                        | HTTP + code |
|---------------------------|-------------|
| 正常返回                      | 200         |
| 参数校验失败 / 缺少参数 / JSON 解析失败 | 400         |
| 未认证 / 无权限                 | 401 / 403   |
| 资源不存在                     | 404         |
| 请求方法不支持 / 媒体类型不支持         | 405 / 415   |
| 未预期异常                     | 500         |

- `ErrorCode.code()` 与 `BusinessException` 的 `code` **必须返回 100~599 的合法 HTTP 状态码**；
  非法值（如 0 / -1 / 10001）会在 `ApiResponse.httpStatus()` 处兜底成 500，不会把请求打挂。
- 需要「HTTP 状态码 + 业务细粒度码」两套编码并存时，请给 `ApiResponse` 增加独立的 `subCode`
  字段承载业务码，**不要**让 `code` 脱离 HTTP 语义。
- Controller 正常返回时无需特殊处理，直接返回 `ApiResponse` 即 200；异常处理器里统一调用
  `ApiResponse.failure(...).toResponseEntity()` 携带状态码。

### 链路追踪：traceId

**单体应用同样需要 traceId**——它的作用是「一次请求的关联 ID」，把散落在 Controller 日志、
`WebLogAspect` 请求/返回日志、异常堆栈里的输出串起来。微服务才需要跨服务传递
（W3C `traceparent`），本模板不涉及，因此只做本地链路：

- `filter/TraceIdFilter`：优先沿用 Nginx / 网关下发的 `X-Trace-Id`，缺失时本地生成 32 位 ID；
  写入 MDC、写回响应头，请求结束后 `MDC.remove()`；
- `ApiResponse#traceId`：构造时自动从 MDC 取值，业务代码无需感知，异常响应同样带上；
- `logback-spring.xml`：`%X{traceId}` 进日志，非 HTTP 线程输出 `-` 占位保证列数稳定。

**日志落盘与控制台的约定**：非 prod（dev / test / 未知 profile）控制台同步输出 + 文件异步输出；
**prod 除了文件，还额外挂了一个控制台 appender**——容器化后 `docker logs` / `kubectl logs` 只能读到
stdout，只落文件会导致线上完全看不到日志。prod 的控制台刻意用不带 ANSI 高亮的 pattern，
避免 `%highlight` 的转义字符污染日志采集。

> `<configuration scan="false">` 是刻意关闭的：打成 fat jar 后配置文件不在文件系统上、无法被监听，
> 开启会在每次启动打印 logback 的 `Watching .xml files requires...` 警告，并在生产常驻一个扫描线程。
> 开发期改日志配置由 devtools 触发重启生效。

#### 异步队列：多久会丢日志、会不会拖慢接口

四个级别各挂一个 `AsyncAppender`（它只能挂一个下游），队列策略按级别分层：

| Appender           | `discardingThreshold` | 行为                 |
|--------------------|-----------------------|--------------------|
| `ASYNC_DEBUG_FILE` | 1638（队列 20%）          | 快满时丢 DEBUG，业务线程不阻塞 |
| `ASYNC_INFO_FILE`  | 1638（队列 20%）          | 快满时丢 INFO，业务线程不阻塞  |
| `ASYNC_WARN_FILE`  | 0                     | 一条不丢               |
| `ASYNC_ERROR_FILE` | 0                     | 一条不丢               |

两个必须知道的实现细节（都是读 logback 1.5.x 源码 / 字节码确认的，不是猜的）：

1. **`discardingThreshold` 是「剩余容量的绝对条数」，不是百分比。** 判定式是
   `remainingCapacity < discardingThreshold`，所以 `queueSize=8192` 配 20% 就是 **1638**。
   不显式配置时 logback 的默认值恰好也是 `queueSize / 5`。
2. **能丢的只有 TRACE / DEBUG / INFO。** `AsyncAppender.isDiscardable()` 判定 `level <= INFO`，
   WARN / ERROR 从来不在丢弃范围内——所以 ERROR 队列写 0 只是显式声明「不丢」的意图，写 20% 也丢不掉它。

另外把**级别过滤器挂在了 AsyncAppender 上，而不是下游文件 appender 上**：事件在进入队列前就被
DENY，既不占队列槽位也省掉消息格式化。若挂在下游，一条 ERROR 会同时灌进 4 条队列、写文件时才被丢弃，
白白放大内存与队列压力——这才是「队列很容易满」的真正原因。已实测四个日志文件级别互不污染
（debug 只有 DEBUG、info 只有 INFO、warn 只有 WARN）。

> ⚠️ **`neverBlock=false` 不等于「最多等 3s」。** 队列真满时走的是 `putUninterruptibly()`，
> 业务线程会**一直阻塞到队列腾出空位且不可中断**；`maxFlushTime=3000` 只约束关闭 JVM 时的收尾刷盘。
> 这是「宁可慢也要留下 ERROR 现场」的取舍。若你的场景宁可丢日志也不能拖慢接口，
> 把对应 appender 的 `neverBlock` 改成 `true`（队列满直接丢弃，不阻塞）。

排查时拿响应体里的 traceId 一捞即为整条链路：

```bash
grep '06e9610c586245f3af21807231411a75' logs/info/*.log
```

#### 异步与出站调用：链路如何不断

`TraceIdFilter` 只在请求线程上工作，另外两个场景需要额外处理，模板都已自动化：

| 场景                             | 断点原因                        | 解决                                                                 |
|--------------------------------|-----------------------------|--------------------------------------------------------------------|
| `@Async` / 线程池任务               | 任务跑在别的线程，MDC 不随线程传递         | `async/MdcTaskDecorator` + `config/AsyncConfig`                    |
| `RestClient` / `@HttpExchange` | 出站请求不会自动带 traceId，下游拿不到上游链路 | `client/TraceIdPropagationInterceptor` + `config/RestClientConfig` |

**`@Async` 只需三步，缺一不可**：

1. `AsyncConfig` 上的 `@EnableAsync` —— 不加它 `@Async` **静默失效**（同步执行且不报错）；
2. 注册 `TaskDecorator` Bean —— Spring Boot 的任务执行自动配置会自动收集容器里的
   `TaskDecorator` 并应用到它创建的执行器上，因此**不必**自己定义 `@Async` 执行器，
   也不会覆盖 Boot 对虚拟线程等默认配置的适配；
3. `@Async` 方法写在**另一个 Bean** 里 —— 同���内部自调用绕过代理，异步与透传都不生效且不报错。

**出站调用**由拦截器把 MDC 里的 traceId 写进 `X-Trace-Id` 请求头。闭环关系是：
下游的 `TraceIdFilter` 正好从同一个头读取并沿用，上下游无需额外约定，链路自动串起来。

依赖 `spring-boot-starter-restclient`，`RestClient.Builder` **由 Boot 自动配置**（原型作用域，
自带消息转换器、SSL、可观测性），模板不自己造 builder，而是注册一个
`RestClientCustomizer`——Boot 会把它应用到创建的每个 builder 上，因此业务侧注入即用：

```java

@Bean
UserApi userApi(RestClient.Builder builder) {          // 已带链路透传
    RestClient restClient = builder.baseUrl("http://user-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    return HttpServiceProxyFactory.builderFor(adapter).build().createClient(UserApi.class);
}
```

`ApiIntegrationTest` 用 `MockRestServiceServer` 绑定真实 builder 断言请求头，
确认拦截器真的被执行，而不只是「Bean 存在」。

已知限制：`Filter` 抛出的异常不进 `@RestControllerAdvice`；外部请求头会做字符集清洗防日志伪造。

### Web 层配置：跨域与拦截器

`config/WebConfig implements WebMvcConfigurer`，是所有 Web 横切设置的**唯一入口**。改动前先分清三层职责：

| 层级                      | 可见信息                | 适合做                           |
|-------------------------|---------------------|-------------------------------|
| Filter（`TraceIdFilter`） | 原始 request/response | 链路 ID、包装请求体、压缩、编码             |
| HandlerInterceptor      | 目标 Handler          | 鉴权、限流、幂等（需要知道命中哪个 Controller） |
| AOP（`WebLogAspect`）     | 方法/参数               | 日志、事务埋点                       |

模板**默认不注册任何业务拦截器**，只留了 `addInterceptors()` 扩展点（含用法注释）。跨域已开箱可用，三个可配项：

```yaml
app:
  cors:
    allowed-origins: '*'      # 生产务必收敛到具体域名
    max-age: 3600
    allow-credentials: false  # 开启凭证时 allowed-origins 不能是 *
```

> 注意：一旦 `allow-credentials: true` 而 `allowed-origins: *`，Spring 会在处理请求的瞬间抛
> `IllegalArgumentException`，且报错不指向配置位置——最后跨域坑。另外跨域时响应头默认被浏览器
> 屏蔽，模板用 `exposedHeaders("X-Trace-Id")` 把 traceId 放行了，前端 JS 才读得到。
>
> 跨域三项在 `config/application-cors.yaml` 里显式写出（代码内也有默认值，不配也能跑）。
> **生产必须收敛**：把 `allowed-origins` 覆盖成具体域名再上线，别让它停在 `*`。
>
> ⚠️ **多个来源要写成逗号字符串，不要写成 YAML 列表。** YAML 列表会被摊平成
> `app.cors.allowed-origins[0]`、`[1]` 这样的索引键，而 profile 之间是**逐索引覆盖**的：
> 只要 `application-prod.yaml` 的项数少于 `application-cors.yaml`，多出来的旧项（很可能就是 `*`）
> 就会残留下来，等于跨域没收敛。逗号字符串是单个标量键，覆盖干净无歧义。

### Jackson 全局配置

`spring.jackson.time-zone / date-format` 写在 `application.yaml`（声明式，改配置不用动代码）；
`Long → String` 写在 `config/JacksonConfig`（配置属性表达不了）。

- **时区**：容器里 JVM 默认常常是 UTC，不设会导致接口时间与数据库差 8 小时，属上线事故高发项；
- **Long → String**：JS Number 安全整数上限是 2^53-1，19 位雪花 ID 传到前端会被静默截断且不报错。
  此项可用 `app.jackson.long-to-string: false` 关闭，**反序列化不受影响**，是单向兼容改动；
- 实测：`date-format` 只作用于 `java.util.Date`，`LocalDateTime` 仍走 ISO-8601。

### 超时配置：三类超时都在 `application-pub.yaml` 显式给了默认值

「不配超时」在生产等价于一枚定时炸弹：连接超时防慢连接占线程、异步超时防请求永久悬挂、
出站超时防下游故障把自己拖死。模板三类都写了注释化默认值，按需微调即可：

```yaml
server:
  tomcat:
    connection-timeout: 20s       # accept 后等待请求行的时间；Tomcat 默认 60s，这里收紧到 20s
    keep-alive-timeout: 20s       # 长连接空闲等待下一个请求的时间；不设则取 connection-timeout
    max-keep-alive-requests: 100  # 单连接最多复用次数，防长连接被单客户端长期占用
    max-swallow-size: 2MB         # 客户端中断时最多再读多少请求体，以便正常回响应
spring:
  mvc:
    async:
      request-timeout: 30s        # @Async / Callable / DeferredResult / SSE；不配就是永不超时
  http:
    clients:
      connect-timeout: 3s         # 出站建连（RestClient / RestTemplate / WebClient 通用）
      read-timeout: 10s           # 出站读响应；不配 = 无限等待
```

三个容易踩的点：

1. **Boot 4 起用复数 `spring.http.clients.*`**，单数 `spring.http.client.*` 已于 4.0 标记废弃
   （Boot 4.1.1 元数据里带 `replacement` 提示），写错前缀不报错、但也不生效。
2. `server.connection-timeout` 是**通用键，自 Boot 2.1 起按 error 级废弃**，理由是各容器语义不同；
   Tomcat 请写 `server.tomcat.connection-timeout`。
3. 开了虚拟线程后 `server.tomcat.threads.max` 不再生效（Boot 4 元数据原文：*Doesn't have an
   effect if virtual threads are enabled*），别再靠调线程数救超时问题。

### Actuator 监控端点

依赖 `spring-boot-starter-actuator`，默认只开放 `health` / `info` / `metrics`：

| 端点                           | 用途                                  |
|------------------------------|-------------------------------------|
| `/actuator/health`           | 健康检查（含 groups: liveness、readiness）  |
| `/actuator/health/liveness`  | K8s 存活探针，DB/Redis 挂了**不会**导致 Pod 重启 |
| `/actuator/health/readiness` | K8s 就绪探针，只从 Service 摘流量             |
| `/actuator/metrics`          | 指标查询（接 Prometheus 需另加 registry）     |

已验证 `env` 等敏感端点返回 404（未暴露）。**切勿**把 `env` / `beans` / `heapdump` / `threaddump`
放到公网，它们会泄漏数据源密码、内网地址与完整 bean 清单。

### 注释规范（Javadoc）

全项目注释遵循同一套结构，以 `web/ApiResponse` 为样板：

| 位置 | 结构                                                                                            |
|----|-----------------------------------------------------------------------------------------------|
| 类  | 一句话摘要（句号结尾）→ `<p>` 行为与约定 → `<p><b>使用示例</b>` / `<p><b>注意事项</b>` → `@param <T>`（泛型类）→ `@author` |
| 方法 | 一句话摘要 → `<p>` 细节与边界 → 全部 `@param`、非 void 的 `@return`、必要 `@throws`                             |
| 字段 | 一句话说明；有隐含约定时用 `<p>` 补充（如 `ApiResponse#traceId` 的自动填充规则）                                       |

写的时候有三条硬约束：

1. **不要 `{@link}` 指向 Lombok 生成的方法**（如 `@Getter` 产生的 `getCode()`）。javadoc 只解析源码，
   看不到编译期生成的成员，会直接报 `reference not found` 错误——写 `{@code code}` 或链接真实方法。
2. 泛型类/方法要写 `@param <T>`；有 javadoc 的方法**每个参数都要有 `@param`**，
   **非 void 必须有 `@return`**，否则 `doclint` 的 `missing` 组会报错。
3. `<p>` 与 `<ul>` / `<li>` 要闭合；`<pre>{@code ... }</pre>` 里的花括号必须成对。

**校验命令**（`maven-javadoc-plugin` 未随项目引入，直接用 JDK 自带的 `javadoc` 检查即可）：

```bash
# 生成依赖 classpath
mvn -o -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt

# 检查（规范档：doclint 全组，仅跳过 missing 组的隐式构造器噪声）
javadoc -Xdoclint:all,-missing -quiet -encoding UTF-8 -charset UTF-8 \
  -d target/javadoc-check -classpath "$(cat target/cp.txt)" $(find src/main/java -name "*.java")
```

当前状态：**0 错误 0 警告**。若改用最严格的 `-Xdoclint:all`，会多出 8 条
「使用不提供注释的默认构造器」——都指向 Spring 组件类（Controller / Config / Filter / Aspect）
由容器调用的隐式无参构造器。为它们补空构造器只为讨好 linter，属噪音，故未加；
团队若要求 doclint 全组零警告，在对应类里补一个带注释的 public 无参构造器即可。

### 测试与覆盖率（JaCoCo）

模板自带一套可运行的测试与覆盖率门禁，当前状态：**149 个用例全绿**（`mvn clean test`，未统计覆盖率）。
覆盖率门禁阈值为 0.80，绑在 `verify` 阶段。

其中数据库相关的用例是真库集成测试，会真的往本地 PostgreSQL 写数据：
多数靠类上的 `@Transactional` 回滚，只有「重复用户名」那条不行——PostgreSQL 里一条语句报错
会让整个事务进入 aborted 状态，必须让两次插入各自独立提交，因此它改为 `@AfterEach` 显式清理。
**跑测试前库必须是起的。**

**为什么引入 JaCoCo：**模板是派生项目的基线，它的正确性只能靠测试守住。而「测试写没写全」
不能靠肉眼看——`jacoco:check` 把覆盖率变成构建门禁，漏测会直接让 `mvn verify` 失败。

**常用命令：**

```bash
mvn -o test                  # 跑测试 + 生成报告（target/site/jacoco/index.html）
mvn -o verify                # 额外执行覆盖率门禁，不达标则构建失败
mvn -o test -Djacoco.skip=true   # 临时跳过覆盖率统计（排查构建问题时用）
```

**关键配置**（`pom.xml`）：

| 配置项                                | 说明                                                                |
|------------------------------------|-------------------------------------------------------------------|
| `jacoco.version` = `0.8.15`        | JaCoCo 版本必须 ≥ 被测字节码的 class file 版本：Java 25 = 69，0.8.15 是首个完整支持的版本 |
| `jacoco.minimum-coverage` = `0.80` | 门禁阈值（0.00~1.00）。模板实测为 100%，阈值留 0.80 是给派生项目加代码时的缓冲，不必改插件配置         |
| 排除 `DemoApplication.class`         | 启动入口类只做 `SpringApplication.run` 转发，覆盖它需要起真容器且无任何业务价值，按业界惯例排除      |
| `check` 绑定 `verify` 阶段             | `mvn test` 不会被覆盖率卡住，只有 `verify` / `install` / `deploy` 才强制校验      |

**测试分层（重要）：**

| 层次       | 位置                      | 职责                                                             |
|----------|-------------------------|----------------------------------------------------------------|
| 单元测试     | 与被测类同包                  | 边界值、防御分支、异常兜底。例如 `ApiResponseTest` 覆盖 `code` 为 `null` / 非法值的兜底 |
| MVC 集成测试 | `ApiIntegrationTest`    | 真实 HTTP 链路：状态码是否如实透出、异常是否真被路由到处理器、traceId 是否贯通、跨域是否生效          |
| 测试专用接口   | `src/test/.../support/` | `ExceptionTestController` 由组件扫描带入测试上下文，用来触发各类异常；**不会打进生产包**    |

两条容易踩的坑，本模板已经用测试钉死：

1. **不要把单元测试当成异常处理的验证。** 单元测试只能证明「处理器拿到异常后返回什么」，
   证明不了「异常会不会被路由到这个处理器」。
2. **Spring 6.1 起，Controller 方法参数上的约束注解抛的是 `HandlerMethodValidationException`，
   不再是 `ConstraintViolationException`。** 少处理这一个异常，`@RequestParam @NotBlank` 校验失败
   会被兜底的 `Exception` 分支当成服务端故障报成 **500 + ERROR 堆栈**，
   而不是 400。`ApiIntegrationTest` 中 `blankRequestParamReturns400` 就是这条链路的守卫。

### 接口文档（springdoc + OpenAPI 3）

依赖 `springdoc-openapi-starter-webmvc-ui`，启动后即可访问：

| 地址                                           | 说明                 |
|----------------------------------------------|--------------------|
| <http://localhost:8000/swagger-ui.html>      | Swagger UI 页面（跳转）  |
| <http://localhost:8000/v3/api-docs>          | 全部接口（JSON）         |
| <http://localhost:8000/v3/api-docs/demo>     | `demo` 分组：模板示例接口   |
| <http://localhost:8000/v3/api-docs/actuator> | `actuator` 分组：运维端点 |

#### 分模块（分组）

`config/OpenApiConfig` 的职责是分开的，`openAPI()` 只维护**服务级元信息**（标题、版本、联系人、
Servers、公共组件），**所有分组共享**；模块拆分由各个 `GroupedOpenApi` Bean 负责。
加一个业务模块时只加一个分组 Bean，不碰公共元信息。

```java

@Bean
public GroupedOpenApi orderApi() {
    return GroupedOpenApi.builder()
        .group("order")                 // -> /v3/api-docs/order
        .displayName("订单模块")
        .pathsToMatch("/api/order/**")  // 按 URL 前缀分
        .build();
}
```

两种拆分维度按需选一：`pathsToMatch(...)`（模块已按路径隔离，推荐）、
`packagesToScan(...)`（路径没有统一前缀但代码按包分层）。不需要的路径用 `pathsToExclude(...)` 排除。

> `actuator` 分组依赖 `springdoc.show-actuator=true`（默认关闭，已在 `application-pub.yaml` 开启）。
> 注意该开关同时会让 Actuator 端点出现在「全部接口」总览里，生产环境如需收窄请关掉。

> **版本必须显式锁定，且大版本要跟随 Spring Boot**：springdoc 不在 Boot 的依赖管理（BOM）里，
> 不写 `<version>` 会直接解析失败；版本选错则**不报编译错误**，只在运行期表现为文档接口 500 或
> `ClassNotFoundException`，排查成本很高。对照关系是 **springdoc 2.x → Boot 3，3.x → Boot 4**
> （当前 `springdoc.version=3.1.1`，其内部锁定 Spring Boot 4.1.0）。
> 模板用 `ApiIntegrationTest` 里的两个运行期用例守着这件事。
>
> 生产环境请关闭或加鉴权——接口清单属于敏感信息。

## PostgreSQL + MyBatis-Plus

本分支在基座之上接了数据库。这一节按「痛点 → 规矩 → 配置 → 示例 → 验证」展开。

### 痛点：三个坐标少一个都不行

MyBatis-Plus 在 Boot 4 上的接入，最容易翻车的不是代码而是依赖坐标：

| 坐标                                    | 作用                                | 少了 / 写错会怎样                                                                                     |
|---------------------------------------|-----------------------------------|------------------------------------------------------------------------------------------------|
| `mybatis-plus-spring-boot4-starter`   | Boot 4 专用 starter，传递引入 jdbc + mybatis-spring 4.x | 写成 `-spring-boot3-starter` 会把 Boot 3 的自动配置拉进来，与 Boot 4 打架                                              |
| `mybatis-plus-jsqlparser`             | 分页插件 `PaginationInnerInterceptor` 的载体     | **自 3.5.9 起分页插件被拆出 starter**。不引这个类直接不存在（编译期报错）；勉强绕过则分页「看起来能跑但查的是全表」 |
| `org.postgresql:postgresql`           | JDBC 驱动（runtime 即可）                 | 启动期不报错，第一条 SQL 才 `ClassNotFoundException`                                                        |

> `mybatis-plus-jsqlparser` 有三个坐标，对应不同的 jsqlparser 版本：
> `mybatis-plus-jsqlparser`（jsqlparser 5.2，默认选它）、`-5.0`、`-4.9`（项目里已有 jsqlparser 4.9 时用）。

**别再引原生 `mybatis-spring-boot-starter`**：MP 的 starter 已经传递带进来了，两套自动配置会打架。

### 规矩：数据库配置单独拆一个文件

配置按这三处放，不要混：

| 文件                                 | 放什么                                                    |
|------------------------------------|--------------------------------------------------------|
| `config/application-db.yaml`       | **全部**数据库配置：数据源 URL / 账号 / 密码、HikariCP、MyBatis-Plus             |
| `application.yaml`                 | 只加一行 `include: - db`，把 db profile 叠加进来                     |
| `application-{dev,test,prod}.yaml` | 不放数据库配置；换库走环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` |

拆出来的原因：这块内容最长、改动最频繁，塞进 `application.yaml` 会把「端口 / 应用名 / profile 激活」这些骨架配置淹掉。

覆盖顺序仍然遵循既有约定：`include` 的 profile 排在 `active` 之前，所以环境文件里的同名 key 优先级更高。

**换库只改环境变量，不改文件**：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 三个占位符都能覆盖文件里的默认值，
`DatabaseConfigTest.environmentVariablesOverrideDefaults` 钉死了这条路径。

> ⚠️ **部署生产时务必显式设置这三个环境变量。** 文件里的默认值指向的是本机库；
> 生产忘了配，应用会「正常启动、正常写入」，只是数据全去了错误的地方——
> **「连错库」比「连不上」危险得多**，往往几周后才被发现。
> 想要「缺环境变量就启动失败」的硬约束，把占位符的默认值去掉即可：
> 写 `${DB_URL}` 而不是 `${DB_URL:jdbc:postgresql://...}`。

### 配置：PostgreSQL 专属的两个参数

```yaml
url: jdbc:postgresql://localhost:5432/postgres?currentSchema=demo&ApplicationName=SpringVortexDemo&reWriteBatchedInserts=true
```

| 参数                       | 作用                                                                |
|--------------------------|-------------------------------------------------------------------|
| `currentSchema=demo`     | 把 `search_path` 钉在 `demo` 模式上。不写会走默认的 `"$user", public`，业务表散落进 `public` |
| `ApplicationName`        | 出现在 `pg_stat_activity.application_name`，线上排障时一眼认出是哪个服务              |
| `reWriteBatchedInserts`  | 把批量 insert 改写成多值 INSERT，批插性能提升数倍。`saveBatch` 不配它等于没提速              |

**HikariCP 的超时是 `long` 毫秒，不是 Duration 字符串。** 写 `connection-timeout: 30s` 会在启动期报
`NumberFormatException: For input string: "30s"`。同项目的 `spring.http.clients.*` 才是 Duration，两套单位别混。

### 建表：执行一次就行

```bash
psql -h localhost -U jiancai.zhong -d postgres -f src/main/resources/db/schema.sql
```

脚本幂等（`IF NOT EXISTS`），可重复执行。表建在独立的 `demo` 模式下，不往 `public` 里堆。

建表语句里有两处是配合 MyBatis-Plus 的，**改了会让注解失效**：

```sql
version integer NOT NULL DEFAULT 0,   -- @Version 乐观锁
deleted integer NOT NULL DEFAULT 0,   -- @TableLogic 逻辑删除
```

用户名唯一用的是**部分唯一索引**而非普通唯一约束：

```sql
CREATE UNIQUE INDEX uk_demo_user_username ON demo.demo_user (username) WHERE deleted = 0;
```

只约束未删除的行——否则删掉一个用户后，他的用户名会被永久占用、无法重新注册。

> 没有用 `spring.sql.init` 让应用启动时自动建表：那会让「应用启动」依赖「数据库可写」，
> 库不可用时连上下文都起不来，所有测试一起失败。建表交给初始化脚本更稳妥。

### 示例：四层怎么摆

```
DemoUserSaveRequest (dto)  只收客户端该填的字段，不用实体接参
        ↓ BeanUtils.copyProperties(request, user)
DemoUser (entity)          @TableName / @TableId(ASSIGN_ID) / @Version / @TableLogic + 填充注解
        ↓
DemoUserMapper             extends BaseMapper<DemoUser>，接口里什么都不用写
        ↓
DemoUserService(+Impl)     extends IService / ServiceImpl<Mapper, Entity>，实现类通常也是空的
        ↓
DemoUserController         分页用 PageResult.of(IPage) 包装，不直接序列化 IPage
```

入参 → 实体用 `org.springframework.beans.BeanUtils.copyProperties(request, user)` 按属性名拷贝。
两个坑：**参数顺序是「源, 目标」**（`commons-beanutils` 那个包正好相反，抄过来会静默拷反方向）；
拷贝没有编译期保护，两边字段名不一致时**不报错、只是拷不过去**。

修改接口直接把入参拷到已落库的实体上（`BeanUtils.copyProperties(request, existing)`）——
入参只有 `username` / `email` / `age`，同名属性被覆盖，`id` / `version` / `deleted` / 时间字段原样保留，
乐观锁与逻辑删除都不会被绕过。

主键用 `IdType.ASSIGN_ID`（雪花 ID，MP 本地生成，插入无需回查）。代价是 ID 有 19 位，
传到 JS 会超出 `Number` 安全整数上限——这条已经被基座的 Jackson `Long → String` 接住了，
所以响应里 `id` 是字符串，前端不用再处理。想换成数据库自增，把 `id-type` 改成 `auto`、建表换成 `bigserial` 即可。

### 自定义 SQL：写在 `resources/mapper` 下

目录位置是固定的，由 `application-db.yaml` 里的这一行决定：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
```

也就是说建 `src/main/resources/mapper/DemoUserMapper.xml` 就会被扫描到。三步走：

**① 在 Mapper 接口加方法**（多参数必须标 `@Param`）：

```java
public interface DemoUserMapper extends BaseMapper<DemoUser> {
    // 第一个参数是 IPage —— 分页插件靠它识别「这条要分页」
    IPage<DemoUser> selectByCondition(IPage<DemoUser> page,
                                      @Param("keyword") String keyword,
                                      @Param("minAge") Integer minAge);

    List<DemoUserAgeGroup> selectAgeGroupSummary();   // 聚合，返回自定义 VO
}
```

**② 在 XML 里写 SQL**（`namespace` 必须是 Mapper 接口的全限定名）：

```xml
<mapper namespace="com.zjc.demo.mapper.DemoUserMapper">

    <sql id="Base_Column_List">
        id, username, email, age, version, deleted, create_time, update_time
    </sql>

    <select id="selectByCondition" resultType="DemoUser">
        SELECT <include refid="Base_Column_List"/>
        FROM demo_user
        <where>
            deleted = 0                                   <!-- 手写 SQL 必须自己带 -->
            <if test="keyword != null and keyword != ''">
                AND (username ILIKE CONCAT('%', #{keyword}, '%')
                  OR email   ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="minAge != null">
                AND age &gt;= #{minAge}
            </if>
        </where>
        ORDER BY id DESC
    </select>
</mapper>
```

**③ 经 Service 转一手再给 Controller**（Controller 只依赖 Service 接口）：

```java
@Override
public IPage<DemoUser> searchByCondition(IPage<DemoUser> page, String keyword, Integer minAge) {
    return baseMapper.selectByCondition(page, keyword, minAge);   // baseMapper 是父类字段，不用再注入
}
```

手写 SQL 的四条规矩：

| #  | 规矩                                                       | 不遵守会怎样                                    |
|----|----------------------------------------------------------|-------------------------------------------|
| 1  | **逻辑删除条件要自己写** `AND deleted = 0`                            | MP 的自动追加只对 `BaseMapper` 内置方法生效，手写 SQL 会把已删数据查出来 |
| 2  | **分页靠第一个参数是 `IPage`**，XML 里别写 `limit`                      | 自己写会与插件改写打架，分页结果错乱                        |
| 3  | **多参数必须 `@Param`**                                        | XML 只能写 `#{param1}` / `#{arg0}`，字段改名就全乱    |
| 4  | **`resultType` 写短名要登记别名包**                                | `type-aliases-package` 没覆盖到的包会启动期报 `Cannot find class: Xxx` |

XML 里 `<`、`>=` 这类符号要写成 `&lt;`、`&gt;=`，否则 XML 解析直接失败。

返回的 VO 放 `dto` 包，列名 `age_group` / `user_count` 会按驼峰规则自动映射到
`ageGroup` / `userCount`。注意计数是 `Long`，会被全局的 `Long → String` 一并转成字符串
（`"userCount": "3"`），想保持数字就把字段类型改成 `Integer`。

### 重复数据怎么给出明确提示

用户名撞唯一索引时，MyBatis-Spring 会把 SQLState `23505` 翻译成 `DuplicateKeyException`。
它既不是 `BusinessException` 也不是 Spring 的 4xx 异常，不处理就会掉进全局兜底分支，
被当成服务端故障报成 **500「服务内部错误」**——排查方向被带偏，前端也只会弹一句没用的提示。

本分支在 Controller 里就地捕获，转成 409 并说清是哪个值冲突：

```java
try {
    demoUserService.save(user);
} catch (DuplicateKeyException e) {
    log.warn("新增用户失败，用户名已存在: username={}", user.getUsername(), e);
    throw new BusinessException(ApiResponseConstant.CONFLICT.code(), "用户名「" + user.getUsername() + "」已存在");
}
```

响应：

```json
{"success": false, "code": 409, "message": "用户名「alice」已存在", "data": null, "traceId": "..."}
```

两点说明：

- **不要在插入前先查一次 `exists()` 来防重复**：查完到插入之间存在时间窗，并发下照样撞索引。
  唯一索引是唯一可靠的防线，这里做的是「冲突发生后给出可读原因」。
- 原始驱动报错（含约束名 `uk_demo_user_username`）只进日志，不外泄到响应里。
  确实想把它原样返回的话，把 `e.getRootCause().getMessage()` 拼进 message 即可。

### 六个必踩的坑（都是本分支实测出来的）

1. **分页插件要放到执行链最后。** 官方文档明确要求「多个插件时把分页插件放到最后面」，
   否则 COUNT SQL 可能统计不准。`MybatisPlusConfig` 里先加乐观锁、后加分页，`MybatisPlusConfigTest` 钉死了顺序。
2. **`IService` / `ServiceImpl` 在 3.5.17 换了包。** 从
   `com.baomidou.mybatisplus.extension.service.*` 迁到了 `com.baomidou.mybatisplus.spring.service.*`。
   照着老博客写会直接编译不过。
3. **`@Version` 为 `null` 时乐观锁整段跳过，且不报错。** 而 `insert` 不会把数据库的
   `DEFAULT 0` 回写进实体——所以「插入后拿同一个对象直接更新」是<b>没有并发保护</b>的，
   生成的 SQL 里根本没有 `AND version = ?`。正确做法是先 `selectById` 再改。
4. **MyBatis 一级缓存会让同一事务内两次 `selectById` 返回同一个对象实例。**
   想造「两份数据」模拟并发冲突是造不出来的（改了第一份等于改了第二份）。
   测试里改用 `JdbcTemplate` 直接在库里把 `version` 加 1 来模拟另一个事务抢先提交。
5. **Homebrew 版 PostgreSQL 的默认用户不是 `postgres`。** 它是当前 macOS 用户名
   （如 `jiancai.zhong`）；用 `postgres` 连会直接报 `role "postgres" does not exist`。
6. **自动填充要「字段注解 + 处理器」成对配置。** 字段标了 `@TableField(fill = ...)` 但处理器没赋值 →
   写库为 `null`；处理器赋值了但字段没标 → 不生效。另外 `strictXxxFill` 依赖 MP 的 `TableInfo` 缓存，
   脱离 Spring 容器直接 new 处理器来调会抛 `NullPointerException`——它的测试必须起容器。

### 验证

```bash
# 1. 建库建表（只需一次）
createdb postgres 2>/dev/null; psql -h localhost -U jiancai.zhong -d postgres -f src/main/resources/db/schema.sql

# 2. 跑测试（真库集成测试会真的写库，靠 @Transactional 回滚，不留垃圾数据）
mvn clean verify

# 3. 手工验证
mvn spring-boot:run
curl -X POST http://localhost:8000/api/users \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","age":18}'
curl http://localhost:8000/api/users?current=1&size=10
curl http://localhost:8000/api/users/{id}
```

切换到别的库不用改配置文件：

```bash
DB_URL="jdbc:postgresql://129.204.226.206:5432/postgres" DB_USERNAME=wechat DB_PASSWORD=xxx mvn clean verify
```

## 本地启动

```bash
mvn -o clean test          # 先跑测试（DemoApplicationTests 校验 Spring 上下文）
mvn spring-boot:run        # 启动，端口 8000
curl http://localhost:8000/hello
curl http://localhost:8000/swagger-ui/index.html
```

> Boot 4 要求 JDK 17+。若终端构建报 `类文件具有错误的版本 61.0, 应为 52.0`，说明当前 `JAVA_HOME`
> 仍指向 JDK 8，先切过去再执行（构建本身与 IDE 运行是两套 JDK 来源）：
>
> ```bash
> JAVA_HOME="D:/app/Java/jdk-25.0.2" mvn -o clean test   # Windows Git Bash
> ```
>
> 顺带装 agent 可消除 Mockito 自挂载警告：`-XX:+EnableDynamicAgentLoading`。

---

## 分支策略（重要）

```
main                    默认分支：纯索引，只记录各分支用途，不含代码
└── sample/boot4-jdk25  基座分支：本 README 所在，保持通用、零业务耦合
    ├── xxxxx-mysql     教程分支：Spring Boot 集成 MySQL
    ├── xxxxx-redis     教程分支：集成 Redis
    └── xxxxx-…         每个教程分支只讲一个主题
```

约定：

1. `main` **只做导航**，不写代码，是别人 clone 下来看到的第一个分支。
2. 任何教程 / 实验分支**一律从对应的 `sample/*` 脚手架分支拉取**，不要在别的教程分支上继续叠feature，避免主题互相污染。
3. 新建教程分支后在 `main` 的 README 里补一行记录，保持索引可查。
4. 注意：远端 `origin/HEAD` 指向 `main`，从 `sample/boot4-jdk25` 提 PR / push 时要显式指定目标分支。

```bash
git switch sample/boot4-jdk25 && git pull --ff-only
git switch -c xxxxx-mysql
git push -u origin xxxxx-mysql
```

---

## 从本模板派生一个新项目

### 1. 拉取代码

```bash
# 同一仓库內拉分支
git switch sample/boot4-jdk25 && git pull --ff-only && git switch -c feature/your-project

# 或独立新仓库
git clone --branch sample/boot4-jdk25 --single-branch <url> your-project && cd your-project && rm -rf .git && git init
```

### 2. 改 Maven 坐标（`pom.xml`）

```xml

<groupId>com.yourco</groupId>
<artifactId>your-project</artifactId>
<version>0.0.1-SNAPSHOT</version>

<properties>
    <java.version>25</java.version>    <!-- 按需降到 21 / 17 -->
</properties>
```

### 3. 改包名（最容易漏的一步）

把 `com.zjc.demo` 重命名为 `com.yourco.yourapp`，以下 **5 处必须一起改**，漏一处就会埋雷：

| # | 位置                                                                   | 漏改的后果                              |
|---|----------------------------------------------------------------------|------------------------------------|
| 1 | `src/main/java/com/zjc/demo/**`                                      | 编译不过（会被发现）                         |
| 2 | `src/test/java/com/zjc/demo/DemoApplicationTests.java`               | 测试包不一致                             |
| 3 | `DemoApplication` 类名 → `YourAppApplication`                          | 无功能影响，但 Spring Boot 启动类名与项目不符      |
| 4 | **`logback-spring.xml` 里的 `<logger name="com.zjc" level="DEBUG"/>`** | **最隐蔽**：业务代码不打 DEBUG 日志，且不会报错、不会失败 |
| 5 | `application.yaml` 的 `spring.application.name`                       | 日志文件名、注册中心、APM 里的服务名全跟着错           |

IDE 的「Refactor → Rename」只覆盖 1/2/3，**4 和 5 必须手动改**。

### 4. 调整 profile

`application.yaml` 里现在是硬编码的 `active: dev` + `include: pub`。两者分工不同，别混淆：

- **`active`（互斥，选一个）**：环境维度。`dev` / `test` / `prod`，同一时刻只有一个生效。
- **`include`（叠加，可多个）**：横切维度。`pub` 是「与环境无关的公共配置」——虚拟线程、
  Jackson、Actuator 这些在哪套环境都一样，因此单独成文件并被所有环境包含。
  这是 `include` 的典型用法：把公共项从各环境文件里抽出来，避免 `application-dev.yaml`
  和 `application-prod.yaml` 各抄一份。
- **已实测的覆盖关系**：`active: prod` + `include: pub` 时 `activeProfiles` 顺序是
  `[pub, prod]`，**后者优先**，即 `application-prod.yaml` 覆盖 `application-pub.yaml`。
  所以同一个 key 只在 `pub` 里写一次，环境文件里只写差异——比如 `springdoc.*` 只在 `pub`
  定义一次（默认开启），`application-prod.yaml` 里只留「关闭」这三条覆盖，dev / test 不用再抄一遍。

新项目按自己的环境拆分：

```yaml
spring:
  profiles:
    active: @activatedProperties@  # 交给构建期替换，或用部署时的 --spring.profiles.active 覆盖
```

常见做法：

- `application.yaml` 只放主配置与 profile 激活；
- `application-dev.yaml` / `application-test.yaml` / `application-prod.yaml` 放环境差异；
- 与环境无关的公共项放 `config/application-pub.yaml` 这类附加 profile，用 `include` 叠加；
- 敏感值一律走环境变量占位 `${DB_PASSWORD:}`，**不要写进配置文件**；
- 运行时覆盖优先级最高：`java -jar app.jar --spring.profiles.active=prod`。

> 上面示例里的 `@activatedProperties@` 是 Maven 资源过滤占位符，要在 `pom.xml` 的 `<build><resources>`
> 里对 `application.yaml` 开启 `<filtering>true</filtering>` 才会被替换。模板没有开这个开关，
> 默认走「硬编码 `active: dev` + 部署时用命令行覆盖」的方式。

### 5. 删除示例文件

派生项目时清掉这些（它们只是 API 用法演示）：

```
src/main/java/com/zjc/demo/controller/HelloController.java
src/main/java/com/zjc/demo/service/HelloService.java
src/main/java/com/zjc/demo/service/impl/HelloServiceImpl.java
src/main/resources/static/favicon.ico          # 可选，随手换自己的图标
src/main/resources/config/application-pub.yaml # 公共配置占位，不需要就删，同时去掉 include: pub
```

**保留**（这些是基础设施，不是示例）：

```
DemoApplication.java / aop / constant / exception / web / DemoApplicationTests.java / logback-spring.xml
```

### 6. 自检

```bash
mvn clean test
```

删完示例后 `DemoApplicationTests` 仍应通过——它只验证 Spring 上下文能起来。

### 7. 换掉 LICENSE 与 Maven 坐标里的版权信息

根目录 `LICENSE` 是 MIT，持有人写的是模板作者。派生新项目时：

- 替换 `LICENSE` 里的 `Copyright (c) <年份> <持有人>`；公司项目通常改用 Apache-2.0（含专利授权），
  换的时候记得同步 `pom.xml` 的 `<licenses>`；
- 顺手把 `pom.xml` 的 `<name>` / `<description>` 一起改掉——它们会出现在构建产物与依赖信息里。

---

## 升级点：升 Spring Boot 版本时要注意什么

改 `<parent>` 的 `<version>` 一处即可，但要同步Review下面这些 Boot 4 的破坏性变更：

| 主题                | Boot 3.x                                   | Boot 4                                                  |
|-------------------|--------------------------------------------|---------------------------------------------------------|
| Web starter       | `spring-boot-starter-web`                  | `spring-boot-starter-webmvc`                            |
| 测试 starter        | `spring-boot-starter-test` 包含一切            | 按技术栈拆分：`-webmvc-test`、`-jdbc-test`、`-restclient-test` … |
| Jackson           | `com.fasterxml.jackson.*` / `ObjectMapper` | `tools.jackson.*` / `JsonMapper`                        |
| Jackson 定制器       | `Jackson2ObjectMapperBuilderCustomizer`    | `JsonMapperBuilderCustomizer`                           |
| 自动配置包             | `…autoconfigure.web.servlet.*`             | `…webmvc.autoconfigure.*`                               |
| Mock bean         | `@MockBean` / `@SpyBean`                   | `@MockitoBean` / `@MockitoSpyBean`（Spring Framework 原生） |
| `@SpringBootTest` | 自动配置 MockMvc / TestRestTemplate            | 需自己配或直接引对应 test starter                                 |
| 嵌入式容器             | Tomcat / Jetty / Undertow                  | **Undertow 支持已移除**                                      |
| 空值注解              | 各种 `@Nullable`                             | 统一 JSpecify（classpath 里可见 `jspecify`）                   |

平滑迁移的临时方案是 `spring-boot-starter-classic`（把模块化jar重新打包成一坨），但那是技术债，新项目不要走这条路。

---
