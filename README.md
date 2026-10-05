# Spring Boot 2 脚手架模板

> 本 README 位于 `sample/boot2-jdk8` 分支（JDK 8 + Spring Boot 2.7.18 版本）。
> 这里是一切新项目的起点，本身不承载任何业务。
> 同款脚手架另有 `sample/boot3-jdk21`（JDK 21 + Boot 3.5.16）与 `sample/boot4-jdk25`（JDK 25 + Boot 4）分支，按需取用。

## 技术栈

| 项            | 版本 / 选型                                                                        |
|--------------|----------------------------------------------------------------------------------|
| Spring Boot  | 2.7.18（Spring Framework 5.3.31）                                                 |
| JDK          | 8（Boot 2.7 支持 8 ~ 19）                                                           |
| Web          | `spring-boot-starter-web` + Tomcat 9.0.83（Servlet 4.0 / `javax.*` 命名空间）             |
| JSON         | Jackson 2.13.5（`com.fasterxml.jackson.*`）                                        |
| 校验           | `spring-boot-starter-validation`（Hibernate Validator 6.2.5 / Bean Validation 2.0）     |
| AOP          | `spring-boot-starter-aop`                                                         |
| 测试           | `spring-boot-starter-test`（JUnit 5.8.2 / AssertJ 3.22.0 / Mockito 4.5.1）              |
| 工具           | Lombok 1.18.30、`spring-boot-devtools`（optional）                                   |
| 出站 HTTP      | `RestTemplate`（Boot 2 没有 `RestClient`，后者是 Spring 6.1 才引入的）                            |
| 日志           | logback 1.2.12 + SLF4J 1.7.36                                                     |

> **命名空间是 `javax.*`，不是 `jakarta.*`。**  Jakarta 的包名迁移发生在 Spring Framework 6 / Boot 3，
> 本分支沿用的是 `javax.servlet.*`、`javax.validation.*`。升级到 Boot 3 时这是一次全局替换。

## 目录结构

```
src/main/java/com/zjc/demo/
├── DemoApplication.java          # 启动类
├── aop/WebLogAspect.java         # Controller 环绕日志切面
├── async/
│   ├── MdcTaskDecorator.java     # 异步任务的 MDC（traceId）透传
│   └── AsyncTaskMetricsDecorator.java  # 异步任务耗时埋点（micrometer）
├── client/TraceIdPropagationInterceptor.java  # 出站请求头注入 traceId
├── config/
│   ├── WebConfig.java            # WebMvcConfigurer：跨域 + 拦截器扩展点
│   ├── JacksonConfig.java        # Jackson 定制：Long → String
│   ├── AsyncConfig.java          # @EnableAsync + 组合任务装饰器
│   ├── RestTemplateConfig.java   # RestTemplate 定制：链路透传 + 出站超时
│   └── OpenApiConfig.java        # springdoc 服务级元信息与分组
├── constant/
│   ├── ApiResponseConstant.java  # 标准响应码枚举（code 即 HTTP 状态码）
│   ├── ErrorCodeConstant.java    # 错误码契约接口（业务方枚举实现它）
│   └── TraceConstant.java        # traceId 常量：MDC key、请求/响应头名
├── exception/
│   ├── BusinessException.java    # 业务异常（code 即 HTTP 状态码）
│   └── GlobalExceptionHandler.java  # 全局异常处理（@RestControllerAdvice）
├── filter/TraceIdFilter.java     # traceId 生成/沿用 + 写入 MDC
├── web/ApiResponse.java          # 统一响应封装，可 toResponseEntity() 带状态码返回
├── controller/HelloController.java  # 示例，派生时可删
└── service/HelloService(+Impl)      # 示例，派生时可删
src/main/resources/
├── application.yaml              # 主配置（profile 在此激活）
├── config/application-pub.yaml   # pub 附加 profile：公共配置（超时 / Jackson / Actuator / 404 治理）
├── config/application-cors.yaml  # cors 附加 profile：模板自定义项（跨域 / Jackson 开关）
├── config/application-{dev,test,prod}.yaml  # 环境差异
└── logback-spring.xml            # 日志（按级别分文件 + 异步 + 180 天滚动 + 生产落控制台）

LICENSE                           # MIT，派生新项目时记得替换版权持有人
```

配置文件这样分层，是为了让「同一个 key 只写一处」：

| 文件                                 | 放什么                                     |
|------------------------------------|-----------------------------------------|
| `application.yaml`                 | 端口、应用名、profile 激活（`active` + `include`） |
| `application-pub.yaml`             | 与环境无关的公共项，被所有环境 `include` 叠加            |
| `application-cors.yaml`            | 模板自定义项（`app.*`），同样被 `include` 叠加        |
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
  "timestamp": "1790761655561"
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

#### Boot 2 专属：404 必须靠两个配置组合才统一

「未匹配路径返回统一 `ApiResponse` 404」这件事，在 Boot 2 上要靠**两个开关同时打开**：

```yaml
spring:
  mvc:
    throw-exception-if-no-handler-found: true  # 没有 Handler 时抛异常
  web:
    resources:
      add-mappings: false                      # 关掉默认的 /** 静态资源映射
```

只配第一个是不够的：Boot 默认的静态资源 Handler 映射在 `/**` 上，会把所有未匹配路径先接住，
直接 `sendError(404)` 并转给 `/error` 端点，返回的是 Boot 默认错误结构，**不会**经过
`GlobalExceptionHandler`。把静态资源映射关掉后，异常才会抛到 `NoHandlerFoundException` 处理器上。

> `ProfileConfigTest#notFoundHandlingRequiresBothProperties` 有一条断言钉着这两个 key，
> 防止日后有人「清理」配置时悄悄把 404 治理弄丢。
> 本模板是纯 API 服务，没有静态资源，因此 `src/main/resources/static` 也一并去掉了。

#### Boot 2 专属：异常类型与 Boot 3 不同

| 场景                  | Boot 2 / Spring 5.3                    | Boot 3 / Spring 6.1+                              |
|---------------------|----------------------------------------|---------------------------------------------------|
| 找不到 Handler          | `NoHandlerFoundException`              | `NoResourceFoundException`（Spring 6.2 起 2 参构造）      |
| 带状态码的框架异常           | `ResponseStatusException`（无 `ErrorResponseException` 父类） | `ResponseStatusException extends ErrorResponseException` |
| Controller 方法参数校验失败 | `ConstraintViolationException`（**必须**在类上加 `@Validated`） | `HandlerMethodValidationException`（无需 `@Validated`）   |

`ResponseStatusException` 在 Spring 5.3 上的取值 API 也是 `getRawStatusCode()` / `getReason()`
（Spring 6 改成了 `getStatusCode()` / `getBody()`），`GlobalExceptionHandler#handleResponseStatus`
就是按前者写的。

> **升级到 Boot 3 必须补 `HandlerMethodValidationException` 的处理器**，否则「客户端传错参数」
> 会掉进兜底的 `Exception` 分支，被当成服务端故障报成 **500 + ERROR 堆栈**，而不是 400。

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

两个必须知道的实现细节（都是读 logback 源码 / 字节码确认的，不是猜的）：

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

| 场景                     | 断点原因                        | 解决                                                                     |
|------------------------|-----------------------------|------------------------------------------------------------------------|
| `@Async` / 线程池任务        | 任务跑在别的线程，MDC 不随线程传递         | `async/MdcTaskDecorator` + `config/AsyncConfig`                        |
| `RestTemplate` 出站调用   | 出站请求不会自动带 traceId，下游拿不到上游链路 | `client/TraceIdPropagationInterceptor` + `config/RestTemplateConfig`   |

**`@Async` 只需三步，缺一不可**：

1. `AsyncConfig` 上的 `@EnableAsync` —— 不加它 `@Async` **静默失效**（同步执行且不报错）；
2. 注册 **唯一一个** `TaskDecorator` Bean —— Spring Boot 的任务执行自动配置会把它应用到
   它创建的执行器上，因此**不必**自己定义 `@Async` 执行器。
   **注意 `getIfUnique()` 的静默限制**：Boot 2 与 Boot 3 的任务自动配置都是用
   `ObjectProvider<TaskDecorator>.getIfUnique()` 取值的，容器里出现两个 `TaskDecorator` Bean 时
   会拿不到唯一实例，结果是**两个都被静默丢弃**（不报错、不打日志，MDC 透传悄悄失效）。
   模板有「链路透传 + 耗时埋点」两个诉求，因此在 `AsyncConfig` 里手动把它们合成一个 Bean——
   Spring 6.1 才有 `CompositeTaskDecorator`，Boot 2 只能用一行 lambda 等价实现：
   `outer.decorate(inner.decorate(task))`。
3. `@Async` 方法写在**另一个 Bean** 里 —— 同类内部自调用绕过代理，异步与透传都不生效且不报错。

**出站调用**由拦截器把 MDC 里的 traceId 写进 `X-Trace-Id` 请求头。闭环关系是：
下游的 `TraceIdFilter` 正好从同一个头读取并沿用，上下游无需额外约定，链路自动串起来。

`RestTemplateBuilder` **由 Boot 自动配置**（原型作用域，自带消息转换器与可观测性），
模板不自己造 `RestTemplate`，而是注册一个 `RestTemplateCustomizer`——Boot 会把它应用到
它创建的每个实例上，因此业务侧注入即用：

```java
@Service
public class UserService {
    private final RestTemplate restTemplate;

    public UserService(RestTemplateBuilder builder) {
        this.restTemplate = builder.rootUri("http://user-service").build();
    }
}
```

> 注意 `MockRestServiceServer` 必须在 `builder.build()` **之后**再 `createServer(restTemplate)`：
> `RestTemplateCustomizer` 在 `build()` 的最后一步执行（它会替换请求工厂设置超时），
> 先 `bindTo` 再 `build()` 会把 mock 的请求工厂覆盖掉，Mock 就失效了。

`ApiIntegrationTest` 用 `MockRestServiceServer` 接住真实请求断言请求头，
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
> `IllegalArgumentException`，且报错不指向配置位置——最典型的跨域坑。另外跨域时响应头默认被浏览器
> 屏蔽，模板用 `exposedHeaders("X-Trace-Id")` 把 traceId 放行了，前端 JS 才读得到。
>
> 上面三项在 `config/application-cors.yaml` 里已经显式写出（代码内也有默认值，不配也能跑）。
> `application-prod.yaml` 把 `allowed-origins` 覆盖成了占位域名 `https://your-domain.com`：
> 这是刻意选的安全默认值，**部署前必须替换**，否则浏览器跨域会被拒——宁可先不通，也不要默认放通。
> 已实测：prod 下带 `Origin: https://your-domain.com` 返回 200 且回 `Access-Control-Allow-Origin`，
> 带其他来源返回 403。
>
> ⚠️ **多个来源要写成逗号字符串，不要写成 YAML 列表。** YAML 列表会被摊平成
> `app.cors.allowed-origins[0]`、`[1]` 这样的索引键，而 profile 之间是**逐索引覆盖**的：
> 只要 `application-prod.yaml` 的项数少于 `application-cors.yaml`，多出来的旧项（很可能就是 `*`）
> 就会残留下来，等于跨域没收敛。逗号字符串是单个标量键，覆盖干净无歧义。
> `ProfileConfigTest` 里有一条断言专门钉死这件事。

### Jackson 全局配置

`spring.jackson.time-zone / date-format` 写在 `application.yaml`（声明式，改配置不用动代码）；
`Long → String` 写在 `config/JacksonConfig`（配置属性表达不了，必须用定制器）。

- **时区**：容器里 JVM 默认常常是 UTC，不设会导致接口时间与数据库差 8 小时，属上线事故高发项；
- **Long → String**：JS Number 安全整数上限是 2^53-1，19 位雪花 ID 传到前端会被静默截断且不报错。
  此项可用 `app.jackson.long-to-string: false` 关闭，**反序列化不受影响**，是单向兼容改动；
- 实测：`date-format` 只作用于 `java.util.Date`，`LocalDateTime` 仍走 ISO-8601。

Boot 2 的定制器是 `Jackson2ObjectMapperBuilderCustomizer`（Boot 4 改名 `JsonMapperBuilderCustomizer`，
且 `addModule` 换成 `modules`、Jackson 2 换成 Jackson 3 的 `tools.jackson.*`）。

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
      request-timeout: 30s        # Callable / DeferredResult；不配就是永不超时
app:
  http:
    client:
      connect-timeout: 3000       # 出站建连（毫秒），由 RestTemplateConfig 读取
      read-timeout: 10000         # 出站读响应（毫秒）；不配 = 无限等待
```

三个容易踩的点：

1. **Boot 2 没有 `spring.http.client.*` 这个前缀。** 出站超时配置项是 Boot 3 才加的
   （Boot 4 又改成复数 `spring.http.clients.*`），在 Boot 2 上写了不会报错也不会生效——
   属于「配了但没起作用，看起来已经防护了」的最坏情况。模板改用自己的键 `app.http.client.*`，
   由 `RestTemplateConfig` 通过 `@Value` 读取，`ProfileConfigTest` 有断言钉着。
2. `server.connection-timeout` 是**通用键，自 Boot 2.1 起按 error 级废弃**，理由是各容器语义不同；
   Tomcat 请写 `server.tomcat.connection-timeout`。
3. JDK 8 没有虚拟线程，`spring.threads.virtual.enabled` 在 Boot 2 里也不存在。这里的线程池就是
   Boot 默认的 `ThreadPoolTaskExecutor`，参数用 `spring.task.execution.pool.*` 调整。

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

> K8s 探针开关 `management.endpoint.health.probes.enabled` 定义在
> `spring-boot-actuator-autoconfigure` jar 的配置元数据里（不在 `spring-boot-actuator` jar 里），
> 用 IDE 补全搜不到时可以去 jar 的 `META-INF/spring-configuration-metadata.json` 确认。

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
4. **代码块里「行首的 `@`」要写成 HTML 实体 `&#64;`，并且用纯 `<pre>` 而不是 `<pre>{@code`。**
   javadoc 会把行首的 `@Override` 当成块级标记（未知标记 → 报 `未知标记: Override`），
   并把 `<pre>{@code` 判定成「未终止的内嵌标记」。注意此时**不能**用 `@@` 转义——JDK 8 的
   javadoc 不认这种写法，会报「`@` 后没有标记名」。`WebConfig#addInterceptors` 的注释里有现成范例。

**校验命令**（`maven-javadoc-plugin` 未随项目引入，直接用 JDK 自带的 `javadoc` 检查即可）：

```bash
# 生成依赖 classpath
mvn -o -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt

# 检查（规范档：doclint 全组，仅跳过 missing 组的隐式构造器噪声）
javadoc -Xdoclint:all,-missing -quiet -encoding UTF-8 -charset UTF-8 \
  -d target/javadoc-check -classpath "$(cat target/cp.txt)" $(find src/main/java -name "*.java")
```

当前状态：**0 错误 0 警告**。若改用最严格的 `-Xdoclint:all`，会多出若干条
「使用不提供注释的默认构造器」——都指向 Spring 组件类（Controller / Config / Filter / Aspect）
由容器调用的隐式无参构造器。为它们补空构造器只为讨好 linter，属噪音，故未加；
团队若要求 doclint 全组零警告，在对应类里补一个带注释的 public 无参构造器即可。

### 测试与覆盖率（JaCoCo）

模板自带一套可运行的测试与覆盖率门禁，当前状态：**124 个用例全绿，行 / 分支覆盖率 100%**。

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
| `jacoco.version` = `0.8.15`        | JaCoCo 版本需支持被测字节码：Java 8 只是 class file 52，0.8.x 全都覆盖；升到 JDK 21（65）/ 25（69）时才需要重新核对 |
| `jacoco.minimum-coverage` = `0.80` | 门禁阈值（0.00~1.00）。模板实测为 100%，阈值留 0.80 是给派生项目加代码时的缓冲，不必改插件配置         |
| 排除 `DemoApplication.class`         | 启动入口类只做 `SpringApplication.run` 转发，覆盖它需要起真容器且无任何业务价值，按业界惯例排除      |
| `check` 绑定 `verify` 阶段             | `mvn test` 不会被覆盖率卡住，只有 `verify` / `install` / `deploy` 才强制校验      |

> **Boot 2 的坑：`annotationProcessorPaths` 必须显式写 `<version>`。**
> Boot 2.7 锁的 `maven-compiler-plugin` 是 3.10.1，它在解析 `annotationProcessorPaths` 时
> **不会**回退到 `dependencyManagement`，只写 groupId/artifactId 会报
> `For artifact {org.projectlombok:lombok:null:jar}: The version cannot be empty`。
> 这里用 `${lombok.version}`（父 pom 提供）与 `${project.parent.version}` 显式填上了。

**测试分层（重要）：**

| 层次       | 位置                      | 职责                                                             |
|----------|-------------------------|----------------------------------------------------------------|
| 单元测试     | 与被测类同包                  | 边界值、防御分支、异常兜底。例如 `ApiResponseTest` 覆盖 `code` 为 `null` / 非法值的兜底 |
| MVC 集成测试 | `ApiIntegrationTest`    | 真实 HTTP 链路：状态码是否如实透出、异常是否真被路由到处理器、traceId 是否贯通、跨域是否生效          |
| 测试专用接口   | `src/test/.../support/` | `ExceptionTestController` 由组件扫描带入测试上下文，用来触发各类异常；**不会打进生产包**    |

三条容易踩的坑，本模板已经用测试钉死：

1. **不要把单元测试当成异常处理的验证。** 单元测试只能证明「处理器拿到异常后返回什么」，
   证明不了「异常会不会被路由到这个处理器」。
2. **Spring 5 不会自动校验 Controller 方法参数，必须在类上加 `@Validated`**，
   校验失败抛 `ConstraintViolationException`。少了 `GlobalExceptionHandler` 里对应的处理器，
   客户端传错参数会得到 500 + ERROR 堆栈而不是 400。
   （Spring 6.1 起改为自动校验 + `HandlerMethodValidationException`，本模板为了两个版本行为一致
   保留了 `@Validated`，它在 Boot 3 上同样生效。）
3. **`ConstraintViolationException` 的提示格式是 `方法名.参数名: 约束消息`**（方法级校验，
   `propertyPath` 带出方法名），Boot 3 则只有参数名。断言写死参数名会在跨版本迁移时红掉。

### 接口文档（springdoc + OpenAPI 3）

依赖 `springdoc-openapi-ui`，启动后即可访问：

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

> `actuator` 分组依赖 `springdoc.show-actuator=true`（默认关闭，已在环境 profile 里开启）。
> 注意该开关同时会让 Actuator 端点出现在「全部接口」总览里，生产环境如需收窄请关掉。
>
> **版本必须显式锁定，且大版本要跟随 Spring Boot**：springdoc 不在 Boot 的依赖管理（BOM）里，
> 不写 `<version>` 会直接解析失败；版本选错则**不报编译错误**，只在运行期表现为文档接口 500 或
> `ClassNotFoundException`，排查成本很高。对照关系是 **springdoc 1.x → Boot 2，2.x → Boot 3，
> 3.x → Boot 4**（当前 `springdoc.version=1.7.0`）。
>
> 同时注意 Boot 2 用的 artifact 是 `springdoc-openapi-ui`（Boot 3 起改名
> `springdoc-openapi-starter-webmvc-ui`），`GroupedOpenApi` 也在 `org.springdoc.core` 包
> （Boot 3 在 `org.springdoc.core.models`）。模板用 `ApiIntegrationTest` 里的运行期用例守着这件事。
>
> 生产环境请关闭或加鉴权——接口清单属于敏感信息。

## 本地启动

```bash
mvn -o clean test          # 先跑测试（DemoApplicationTests 校验 Spring 上下文）
mvn spring-boot:run        # 启动，端口 8000
curl http://localhost:8000/hello
curl http://localhost:8000/swagger-ui/index.html
```

> Boot 2.7 支持 JDK 8 ~ 19，本模板构建**目标设为 Java 8**。若终端默认的 `JAVA_HOME` 指向更高版本
> 会顺利用更高字节码编译出包（看不到问题），但换个环境就可能不兼容；反过来若默认 `JAVA_HOME` 是
> JDK 8 而分支是 Boot 3，会直接报 `类文件具有错误的版本 61.0, 应为 52.0`。
> 因此**构建时显式指定 JDK** 是最省事的做法：
>
> ```bash
> JAVA_HOME="D:/app/Java/jdk1.8.0_471" mvn -o clean test   # Windows Git Bash
> ```
>
> 顺带装 agent 可消除 Mockito 自挂载警告：`-XX:+EnableDynamicAgentLoading`。

---

## 分支策略（重要）

```
main                        默认分支：纯索引，只记录各分支用途，不含代码
└── sample/boot4-jdk25      基座分支：Boot 4 / JDK 25 版脚手架
    ├── sample/boot3-jdk21  JDK 21 + Boot 3.5.16 版脚手架（本分支的上游）
    ├── sample/boot2-jdk8   JDK 8 + Boot 2.7.18 版脚手架（本 README 所在）
    ├── xxxxx-mysql         教程分支：Spring Boot 集成 MySQL
    └── xxxxx-…             每个教程分支只讲一个主题
```

约定：

1. `main` **只做导航**，不写代码，是别人 clone 下来看到的第一个分支。
2. 任何教程 / 实验分支**一律从对应版本的 `sample/*` 分支拉取**，不要在别的教程分支上继续叠加 feature，避免主题互相污染。
3. 新建教程分支后在 `main` 的 README 里补一行记录，保持索引可查。
4. 注意：远端 `origin/HEAD` 指向 `main`，从脚手架分支提 PR / push 时要显式指定目标分支。
5. **三个脚手架分支要平行维护**：改了某一版的基建（超时配置、异常处理器、springdoc 版本等），
   记得评估另外两版是否需要跟着改。

```bash
git switch sample/boot2-jdk8 && git pull --ff-only
git switch -c xxxxx-mysql
git push -u origin xxxxx-mysql
```

---

## 从本模板派生一个新项目

### 1. 拉取代码

```bash
# 同一仓库內拉分支
git switch sample/boot2-jdk8 && git pull --ff-only && git switch -c feature/your-project

# 或独立新仓库
git clone --branch sample/boot2-jdk8 --single-branch <url> your-project && cd your-project && rm -rf .git && git init
```

### 2. 改 Maven 坐标（`pom.xml`）

```xml

<groupId>com.yourco</groupId>
<artifactId>your-project</artifactId>
<version>0.0.1-SNAPSHOT</version>

<properties>
    <java.version>8</java.version>     <!-- Boot 2.7 支持 8 ~ 19，按需调整 -->
</properties>
```

> 改 JDK 版本时记得同步 `maven.compiler.source` / `maven.compiler.target`（本模板显式写成了 8，
> 不跟随 Boot 父 pom 的默认 1.8 之外的值，避免 IDE 与命令行构建结果不一致）。

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

`application.yaml` 里现在是硬编码的 `active: dev` + `include: pub, cors`。两者分工不同，别混淆：

- **`active`（互斥，选一个）**：环境维度。`dev` / `test` / `prod`，同一时刻只有一个生效。
- **`include`（叠加，可多个）**：横切维度。`pub` 是「与环境无关的公共配置」——超时、
  Jackson、Actuator 这些在哪套环境都一样；`cors` 是「模板自定义项」——跨域、`app.*` 开关。
  它们单独成文件并被所有环境包含，避免 `application-dev.yaml` 和 `application-prod.yaml` 各抄一份。
- **已实测的覆盖关系**：`active: prod` + `include: pub` 时 `activeProfiles` 顺序是
  `[pub, cors, prod]`，**后者优先**，即 `application-prod.yaml` 覆盖公共文件。
  所以同一个 key 只在 `pub` / `cors` 里写一次，环境文件里只写差异——比如 `springdoc.*` 只在
  环境文件定义一次，`application-prod.yaml` 里只留「关闭」这三条覆盖，dev / test 不用再抄一遍。

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
src/main/resources/config/application-cors.yaml # 自定义配置占位，不需要就删，同时去掉 include: cors
src/main/resources/config/application-pub.yaml  # 公共配置占位，不需要就删，同时去掉 include: pub
```

**保留**（这些是基础设施，不是示例）：

```
DemoApplication.java / aop / async / client / constant / exception / web / DemoApplicationTests.java / logback-spring.xml
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

## 升级点：从 Boot 2 升到 Boot 3 时要注意什么

本分支停在 Boot 2。将来要升 Boot 3，改 `<parent>` 的 `<version>` 一处即可，
但要同步复核下面这些破坏性变更：

| 主题              | Boot 2.7                                                        | Boot 3.x                                             |
|-----------------|-----------------------------------------------------------------|------------------------------------------------------|
| 命名空间            | `javax.servlet.*` / `javax.validation.*`                        | `jakarta.servlet.*` / `jakarta.validation.*`（全局替换）      |
| JDK 基线          | 8                                                               | 17                                                   |
| 语法限制            | 无 `String#repeat` / `List.of` / `@Serial` / `var`（Java 9+ 特性不可用） | 可用到当前 JDK 的所有语法                                      |
| `serialVersionUID` | 手写属性（无法用 `@Serial` 注解）                                          | 可用 `@Serial`                                        |
| Web starter     | `spring-boot-starter-web`                                       | 同（到 Boot 4 才改名 `-webmvc`）                            |
| 出站客户端           | `RestTemplate` + `RestTemplateCustomizer`                       | 同；另有 `RestClient`（Spring 6.1+，Boot 4 才有独立 starter）    |
| 出站超时            | **无** `spring.http.client.*`，需自行定义属性键                           | `spring.http.client.*`（Boot 4 改复数 `clients`）          |
| Jackson 定制器      | `Jackson2ObjectMapperBuilderCustomizer`                         | 同（到 Boot 4 才改名 `JsonMapperBuilderCustomizer`）         |
| 找不到 Handler 异常  | `NoHandlerFoundException` + 两个配置开关                              | `NoResourceFoundException`（Spring 6.2 起 2 参构造）        |
| 带状态码异常          | `ResponseStatusException`（`getRawStatusCode()` / `getReason()`）  | `ResponseStatusException extends ErrorResponseException`（`getStatusCode()`） |
| 方法参数校验          | `ConstraintViolationException`（**必须** `@Validated`）              | `HandlerMethodValidationException`（自动校验，需新增处理器）       |
| 任务装饰器组合         | 无 `CompositeTaskDecorator`，需自己用 lambda 合成                       | Spring 6.1+ 有 `CompositeTaskDecorator`                |
| springdoc       | 1.x（`springdoc-openapi-ui`，`org.springdoc.core.GroupedOpenApi`）  | 2.x（`springdoc-openapi-starter-webmvc-ui`，`core.models` 包） |
| `HttpHeaders`   | 实现 `Map`，可用 `containsKey()`                                     | 不再实现 `Map`；`containsHeader()` 是 Spring 6.2+ 才有        |
| `@MockBean`     | 可用                                                              | 可用但已废弃，Boot 4 换成 `@MockitoBean`                      |

顺序建议：**先把 `javax` → `jakarta` 与 JDK 8 → 17 这两件事做完**（它们是机械替换且量最大），
再逐个处理异常类型与配置键的差异；最后跑一遍 `ApiIntegrationTest`，
它里面针对 `NoHandlerFoundException` / `ConstraintViolationException` / 出站链路的断言
会明确指出哪些 Boot 2 行为假设需要重写。

---
