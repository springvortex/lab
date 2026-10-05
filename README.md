# Spring Boot 4 到底新在哪？一篇看完（含可运行示例）

> **仓库地址**：<https://github.com/springvortex/lab>
>
> **下载本项目**（带上本文所有示例代码）：
>
> ```bash
> git clone -b springboot4/feature https://github.com/springvortex/lab.git
> cd lab
> ```
>
> 配了 SSH 密钥的话，把地址换成 `git@github.com:springvortex/lab.git` 即可。
> 分支说明：`springboot4/feature` 就是本文所在分支；只想看脚手架本身的约定，用 `-b sample/boot4-jdk25`。

这篇面向刚接触 Spring Boot 4 的同学。每个新特性都用大白话讲清楚"**以前怎么做、现在怎么做**"，并且都配了能跑起来的示例代码和
curl 命令——看完可以自己动手试一遍。文中所有代码都在 `springboot4/feature` 分支里，路径都标在每一节末尾。

## 先说结论

| # | 新特性              | 一句话说人话                            | 什么时候用得上          |
|---|------------------|-----------------------------------|------------------|
| 1 | API 版本管理         | 接口升级不用再改 URL 了                    | 对外接口要出新版本        |
| 2 | 声明式 HTTP 客户端     | 调下游只写一个接口，不用手写封装类                 | 调用其他服务           |
| 3 | 多个任务装饰器          | 异步任务的"链路追踪"和"性能埋点"可以各写各的          | 用到 `@Async`      |
| 4 | 出站地址过滤           | 防止用户给的 URL 把你的服务器当跳板去摸内网          | 有回调 / 转存 / 代理类接口 |
| 5 | Jackson 配置开关     | JSON 的读写行为可以直接用配置改，不用写 Java 类     | 所有 Web 项目        |
| 6 | `RestTestClient` | 测试时不用再手写 JSON 路径字符串               | 写接口测试            |
| 7 | JSpecify 空安全     | 告诉工具"这个字段会不会是 null"               | 想让 IDE 帮你查空指针    |
| 8 | 更全的运维信息          | `/actuator/info` 里能看到运行目录、时区、启动多久 | 排查线上环境问题         |

## 0. 先跑起来

```bash
# 跑测试（137 个用例，几分钟）
JAVA_HOME="D:/app/Java/jdk-25.0.2" mvn -o clean verify

# 启动应用，端口 8000
JAVA_HOME="D:/app/Java/jdk-25.0.2" mvn -o spring-boot:run
```

启动成功后，随便挑一条试试（下面每节还会再给）：

```bash
curl -H 'X-API-Version: 2.0' http://localhost:8000/api/version-demo/orders/A001
```

---

## 1. 接口版本管理：升级接口不用改 URL 了

### 以前怎么做

接口要出新版本，常见两种土办法：

- **在路径里塞版本号**：`/v1/orders/{id}`、`/v2/orders/{id}`。问题是"版本升级 = 改地址"，
  所有客户端都得跟着改一遍。
- **复制一个 Controller**：两个类 90% 代码重复，改一处漏一处。

### 现在怎么做

**地址不动**，版本号放在请求头里。同一个路径，写两个方法，各声明自己的版本：

```java

@RestController
@RequestMapping("/api/version-demo/orders")
public class VersionedOrderController {

    // 老版本：只返回订单号和金额
    @GetMapping(path = "/{orderId}", version = "1.0")
    public ApiResponse<OrderV1> getOrderV1(@PathVariable String orderId) {
        return ApiResponse.success(new OrderV1(orderId, "199.00"));
    }

    // 新版本：多了币种和状态，URL 一模一样
    @GetMapping(path = "/{orderId}", version = "2.0")
    public ApiResponse<OrderV2> getOrderV2(@PathVariable String orderId) {
        return ApiResponse.success(new OrderV2(orderId, "199.00", "CNY", "PAID"));
    }
}
```

配上三行配置就能用：

```yaml
spring:
  mvc:
    apiversion:
      use:
        header: X-API-Version  # 从哪个请求头读版本
      supported: "1.0,2.0"     # 支持哪些版本
      default: "1.0"           # 没带版本头时算哪个版本（这一条必须写！）
```

### 试一试

```bash
curl -H 'X-API-Version: 1.0' http://localhost:8000/api/version-demo/orders/A001
# {"data":{"orderId":"A001","amount":"199.00"}}

curl -H 'X-API-Version: 2.0' http://localhost:8000/api/version-demo/orders/A001
# {"data":{"orderId":"A001","amount":"199.00","currency":"CNY","status":"PAID"}}

curl -o /dev/null -w '%{http_code}\n' -H 'X-API-Version: 9.9' \
  http://localhost:8000/api/version-demo/orders/A001
# 400（版本没在 supported 里）
```

### 记住三条

1. **`default` 必须写。** 不写的话，凡是没带版本头的请求都会失败——连 `/hello` 这种
   压根没做版本管理的接口也会一起挂掉。
2. **版本是"语义版本"，不是字符串。** 所以 `1.10` 比 `1.9` 大，别用字符串的直觉去猜。
3. **版本写错会返回 400，不会偷偷降级。** 这是故意的：宁可让调用方立刻发现，
   也不要返回一份"看着能用、其实少字段"的数据。

> 除了请求头，版本还可以从查询参数、URL 路径段、`Accept` 头里读，配置项是
> `spring.mvc.apiversion.use.*`，四种可以组合使用。

**代码位置**：`feature/version/VersionedOrderController.java`，测试 `ApiVersioningTest`

---

## 2. 声明式 HTTP 客户端：调下游只写一个接口

### 以前怎么做

调用别的服务，得手写一个封装类：拼 URL、填参数、选客户端、转返回值、处理异常。
一个下游几十行样板代码，而且"对方接口长什么样"散落在代码各处。

### 现在怎么做

**只写一个接口**，实现由 Spring 在运行时自动生成：

```java

@HttpExchange("/demo/catalog")
public interface CatalogClient {

    @GetExchange("/{id}")
    CatalogItem findById(@PathVariable String id);
}
```

然后加一个注解把它注册成 Bean：

```java

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(types = CatalogClient.class, group = "catalog")
public class HttpServiceClientConfig {
}
```

`group = "catalog"` 是这组客户端的**配置前缀**，服务地址在配置文件里给：

```yaml
spring:
  http:
    serviceclient:
      catalog:                                     # 与 group 名对应
        base-url: http://localhost:${server.port}
        connect-timeout: 3s
        read-timeout: 10s
        default-header:
          X-Client: spring-vortex-demo
```

之后在业务代码里注入就能用，跟调本地方法一样：

```java

@Resource
private CatalogClient catalogClient;

@GetMapping("/demo/catalog-proxy/{id}")
public ApiResponse<CatalogItem> viaDeclarativeClient(@PathVariable String id) {
    return ApiResponse.success(catalogClient.findById(id));
}
```

注意 `@PathVariable` 还是平时写 Controller 用的那个注解，**不需要学新东西**。

### 试一试

```bash
curl http://localhost:8000/demo/catalog-proxy/A001
# {"data":{"id":"A001","name":"机械键盘","price":"399.00"}}
```

这条命令会真实发一次 HTTP 请求出去（发给自己，所以不依赖任何外部服务）。

### 记住三条

1. **接口里只写相对路径。** 写成 `http://xxx` 的绝对地址会绕过 `base-url`，
   那就没法按环境切地址了。
2. **超时、链路透传、地址过滤这些不用你在接口上重复配。** 它们统一由 Boot 自动配置的
   `RestClient` 接管，声明式客户端自动继承。
3. **测试里别用 `${local.server.port}` 当 `base-url`。** 随机端口是服务启动**之后**才知道的，
   而客户端 Bean 在启动过程中就创建好了，会直接启动失败。测试里要连随机端口，
   就自己拿端口造一个客户端：

```java
CatalogClient client = HttpServiceProxyFactory
    .builderFor(RestClientAdapter.create(RestClient.create("http://localhost:" + port)))
    .build()
    .createClient(CatalogClient.class);
```

**代码位置**：`feature/httpclient/` 下 4 个类，测试 `CatalogClientContractTest` 等 3 个

---

## 3. 异步任务：两个"装饰器"可以并存了

### 以前怎么做

Spring 允许你给异步任务加"装饰器"（`TaskDecorator`）——典型用途是把当前请求的
traceId 带进异步线程，这样异步日志才不会断链。

但过去**容器里只能有一个**。想再加一个"给异步任务统计耗时"，就会报
`NoUniqueBeanDefinitionException`，只能自己写个组合类把两个拼起来。

### 现在怎么做

Boot 4 会**自动收集容器里所有装饰器并组装**，于是两个关注点各写各的即可：

```java

@Bean
@Order(0)
public TaskDecorator mdcTaskDecorator() {
    return new MdcTaskDecorator();                        // 已有：把 traceId 带进异步线程
}

@Bean
@Order(Ordered.LOWEST_PRECEDENCE)
public TaskDecorator asyncTaskMetricsDecorator(MeterRegistry meterRegistry) {
    return new AsyncTaskMetricsDecorator(meterRegistry);   // 新增：统计异步任务耗时
}
```

第二个装饰器的核心就几行（记录任务执行了多久）：

```java

@Override
public Runnable decorate(Runnable runnable) {
    return () -> {
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            meterRegistry.timer(TIMER_NAME).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    };
}
```

### 记住四条

1. **一个关注点一个 Bean，不用自己写组合类。**
2. **顺序由 `@Order` 决定，`@Order` 值最大的在最外层**（最先执行、最后收尾）——因为
   Spring 是"一层层包起来"的。
3. **两个装饰器都往 MDC 里写东西时要小心顺序**：外层如果是"整体覆盖 MDC"的写法，
   会把内层刚写进去的键抹掉。
4. **写测试时别急着断言指标。** 装饰器在最外层，而 `CompletableFuture` 是内层完成的，
   所以 `future.get()` 返回时，外层那句"记录耗时"可能还没执行。测试里要等一小会儿再断言。

**代码位置**：`async/AsyncTaskMetricsDecorator.java`、`config/AsyncConfig.java`，
测试 `AsyncTaskMetricsDecoratorTest`

---

## 4. 出站地址过滤：防止你的服务器被当跳板

### 要防什么

只要你的接口里存在"**由用户提供 URL**"的出站调用（图片转存、Webhook 回调、URL 预览、代理抓取……），
就有人会试着填一个内网地址，让你的服务器去访问它。比如云服务器上有一个特殊地址
`169.254.169.254`，访问它可能拿到临时凭证。这类攻击叫 **SSRF**（服务端请求伪造）。

### 现在怎么做

Boot 4.1 提供了一个地址过滤器，**声明成一个 Bean 就自动对所有 HTTP 客户端生效**：

```java

@Bean
public InetAddressFilter outboundInetAddressFilter() {
    return InetAddressFilter.not("169.254.0.0/16")   // 链路本地网段（元数据服务在这里）
        .andNot("0.0.0.0/8");                    // 语义模糊，没有正当用途
}
```

生效范围：`RestClient`、`RestTemplate`、`WebClient` 全都覆盖，不用一个个配。

### 记住三条（这条最容易写反）

1. **`matches()` 的含义是"是否放行"。** `InetAddressFilter.not(x)` 读作"放行不匹配 x 的地址"，
   也就是**拦截 x**。少写一个 `not`，过滤器就变成"只放行要拦的地址"了，而且**不会有任何报错**。
2. **别图省事直接封掉所有内网地址**（`not(InetAddressFilter.internalAddresses())`）。
   那样连回环地址和私网地址都被封了，你的服务一旦要调用内网系统就会全线失败。
3. **这个 Bean 只能有一个。** 想组合多条规则，用 `and` / `or` / `andNot` 串在同一个 Bean 里。

实测的效果（测试里已经固化）：

| 地址              | 上面的过滤器 | 说明     |
|-----------------|--------|--------|
| 169.254.169.254 | 拦截     | 云元数据服务 |
| 8.8.8.8         | 放行     | 正常公网地址 |
| 127.0.0.1       | 放行     | 本机，别误伤 |
| 10.1.2.3        | 放行     | 内网，别误伤 |

**代码位置**：`config/HttpClientSecurityConfig.java`，测试 `HttpClientSecurityConfigTest`

---

## 5. JSON 配置：读写行为可以直接写配置了

### 以前怎么做

想让 Jackson 换个行为（比如金额不要用科学计数法输出），只能写 Java 配置类。
而"JSON 嵌套太深直接拒绝"这种解析器级别的保护，过去根本没地方配。

### 现在怎么做

Boot 4.1 起，这些都可以用配置项直接声明：

```yaml
spring:
  jackson:
    read:
      strict-duplicate-detection: true  # 同一个字段出现两次，直接拒绝
    write:
      write-bigdecimal-as-plain: true   # 金额别写成 1.0E+7 这种
    factory:
      constraints:                      # 解析器级保护，防"一个请求打挂服务"
        read:
          max-nesting-depth: 1000       # JSON 最多嵌套多少层
          max-string-length: 100000000  # 单个字符串最长多少
```

### 为什么要开"重复字段检测"

`{"amount":10,"amount":9999}` 这种报文是合法的 JSON，但**不同库取值规则不一样**：
有的取先出现的，有的取后出现的。如果网关和业务代码取的值不同，攻击者就能构造出
"网关看到 10、实际扣款 9999"的报文。开了严格检测就直接拒绝，从源头消除歧义。

### 试一试

```bash
# 重复字段 → 400
curl -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8000/demo/jackson/echo \
  -H 'Content-Type: application/json' -d '{"orderId":"A","orderId":"B","amount":1}'
# 400

# 金额 1.0E+7 输出成 10000000
curl -X POST http://localhost:8000/demo/jackson/echo \
  -H 'Content-Type: application/json' -d '{"orderId":"A001","amount":1.0E+7}'
# {"data":{"orderId":"A001","amount":10000000}}
```

### 记住两条

1. **配之前先确认默认值**，有些开关默认就是开的（比如 `USE_FAST_DOUBLE_PARSER` 默认 true，配了等于没配）。
   本文提到的两个开关默认都是 `false`，所以配置是实打实有效的。
2. **`factory.constraints` 是"防攻击"用的**，别为了兼容脏数据把它放到很大。

**代码位置**：`feature/jackson/JacksonDemoController.java`，测试 `JacksonFeatureTest`

---

## 6. 测试更顺手了：`RestTestClient`

### 以前怎么做

用 `MockMvc` 写测试，只能拿到响应体的**字符串**。想断言某个字段，就得手写
`jsonPath("$.data.xxx")` 这样的路径字符串——字段名写错、结构改了，编译器一点提示都没有。

### 现在怎么做

用 `RestTestClient`，风格接近平时写业务代码：

```java
RestTestClient client = RestTestClient.bindTo(mockMvc).build();
client.get().uri("/hello")
    .exchange()
    .expectStatus()
    .isOk()
    .expectBody()
    .jsonPath("$.data")
    .isEqualTo("hello SpringVortexDemo!");

// 也可以直接把响应体变成对象，避免手写 JSON 路径
String body = client.get().uri("/hello")
    .exchange()
    .returnResult(String.class)
    .getResponseBody();
```

### 记住三条

1. **按场景选绑定方式**：不起真实服务用 `bindTo(mockMvc)`（最快，最常用）；
   要打真实服务用 `bindToServer()`；只测一个 Controller 用 `bindToController(...)`。
2. **非响应式项目不要用 `WebTestClient`**，那是给 WebFlux 用的。
3. **想要 `@AutoConfigureRestTestClient` 自动注入，得单独引 `spring-boot-resttestclient` 模块**
   （它不在通用的测试 starter 里）。顺便注意：**`TestRestTemplate` 也搬到这个模块了**，
   老路径已经不存在。

**代码位置**：`feature/RestTestClientDemoTest.java`

---

## 7. 空安全：让工具帮你查空指针

### 解决什么问题

"这个方法会不会返回 null？"过去只能靠看代码和注释。Spring Framework 7 全面采用了
**JSpecify** 注解，IDE 和静态分析工具能据此帮你提示"这里可能为 null 你却直接用了"。

### 怎么用

按**包**开启：在包里加一个 `package-info.java`，声明这个包默认"不为空"。

```java
@NullMarked
package com.zjc.demo.feature;

import org.jspecify.annotations.NullMarked;
```

这样包内所有类型默认非空，**确实可能为空的成员再单独标 `@Nullable`**：

```java
private @Nullable CatalogItem lastQueried;   // 确实可能还没查过
```

### 记住三条

1. **按包逐个启用就行**，不用一次性改造整个老项目——新包先标，老代码慢慢迁。
2. **它只管编译期提示，运行期不校验。** 真传了 `null` 也不会抛异常，
   运行时兜底还是要靠 `requireNonNull` 或参数校验注解。
3. 看到别人标了 `@Nullable`，**就当它一定会返回 null 来处理**。

**代码位置**：`feature/package-info.java`、`feature/httpclient/CatalogDemoController.java`

---

## 8. 运维信息更全了：`/actuator/info` 多了 process 段

### 解决什么问题

排查线上问题时经常要问："容器时区对不对？""服务跑在哪个目录？""启动多久了？"
过去得登录机器才能看。Boot 4.1 把这些信息放进了 info 端点。

### 怎么用

```yaml
management:
  info:
    process:
      enabled: true  # 默认是 false，按需打开
```

```bash
curl http://localhost:8000/actuator/info
```

多了这些字段：运行时长 `uptime`、启动时间 `startTime`、当前时间 `currentTime`、
时区 `timezone`、语言环境 `locale`、工作目录 `workingDirectory`。

### 记住两条

1. **它是运维端点，会暴露服务器信息**，公网一定要加鉴权，或者只在内网暴露。
2. **别顺手把所有端点都打开。** 项目里只暴露了 `health,info,metrics`；
   `env`（含配置和密码）、`beans`、`heapdump` 这些千万别放到公网。

**测试**：`feature/ActuatorProcessInfoTest`

---

## 9. 顺便说说：从 Boot 3 升级要注意什么

| 变化                    | 你要做什么                                                                    |
|-----------------------|--------------------------------------------------------------------------|
| `javax.*` 彻底移除        | 全局替换成 `jakarta.*`（`@PostConstruct`、`@Inject` 等）                          |
| Web starter 改名        | `spring-boot-starter-web` → `spring-boot-starter-webmvc`                 |
| 测试依赖拆分                | 按技术栈引 `-webmvc-test`、`-restclient-test` 等                                |
| Jackson 换成 3          | 包名从 `com.fasterxml.jackson` 改成 `tools.jackson`                           |
| Mock 注解换名             | `@MockBean` → `@MockitoBean`，`@SpyBean` → `@MockitoSpyBean`              |
| `TestRestTemplate` 搬家 | 改到 `spring-boot-resttestclient` 模块                                       |
| Undertow 不再支持         | 换 Tomcat 或 Jetty                                                         |
| 自动配置拆成小模块             | 找源码时注意位置变了（如 `RestClientAutoConfiguration` 在 `spring-boot-restclient` 里） |

从 4.0 升到 4.1 还有三个小坑：

- 4.0 里标记废弃的 API 在 4.1 **已经全删了**，升级前先清掉废弃调用，否则直接编译不过；
- 打包时 **`-DskipTests` 不再跳过 AOT 处理**，要跳过请用 `-Dmaven.test.skip=true`；
- Dockerfile 里的 `-Djarmode=layertools` 要改成 `-Djarmode=tools`。

---

## 10. 这些新东西本文还没演示（但 Boot 4 都有）

| 能力                                             | 说明                                      |
|------------------------------------------------|-----------------------------------------|
| Spring gRPC 支持（4.1）                            | 官方支持写 / 测 gRPC 服务端与客户端                  |
| OpenTelemetry starter                          | 一条依赖接上 OTLP 的指标和链路                      |
| `spring.datasource.connection-fetch=lazy`（4.1） | 真正执行 SQL 时才拿数据库连接，启动更快                  |
| `spring.jpa.bootstrap`（4.1）                    | JPA 实体很多时异步初始化                          |
| Log4j 文件轮转（4.1）                                | 支持按大小 / 时间 / 两者 / cron 轮转（本项目用 logback） |
| Kotlin 相关                                      | 序列化 starter、协程上下文传播、`BeanRegistrar` DSL |

---

## 示例代码在哪

```
src/main/java/com/zjc/demo/
├── feature/
│   ├── package-info.java                  # 7. 空安全 @NullMarked
│   ├── version/VersionedOrderController   # 1. 接口版本管理
│   ├── httpclient/                        # 2. 声明式 HTTP 客户端（4 个类）
│   └── jackson/JacksonDemoController      # 5. JSON 读写开关演示
├── async/AsyncTaskMetricsDecorator        # 3. 第二个任务装饰器
└── config/
    ├── AsyncConfig                        # 3. 两个装饰器并存
    └── HttpClientSecurityConfig           # 4. 出站地址过滤
```

新增配置都在 `src/main/resources/config/application-pub.yaml`（公共配置，各环境通用）。

> 脚手架本身的说明（统一响应格式、traceId 链路、配置分层、测试覆盖率门禁等）在
> `sample/boot4-jdk25` 分支的 README 里，需要时用 `git show sample/boot4-jdk25:README.md` 查看。
