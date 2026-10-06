# 一个请求头搞定中英文切换 —— Spring Boot 4 国际化，附我踩过的 4 个坑

同一个接口，国内用户看中文、海外用户看英文。

我见过不少做法是：body 里加个 `lang` 字段，然后 Service 里 `if ("en".equals(lang))`，或者干脆中英文各写一个接口。能跑，但接口一多就收不住了。

这篇记的是另一种做法：**一个 `Accept-Language` 请求头解决，业务代码一行都不用改**。Spring Boot 4.1.1 + JDK 25，代码在本仓库，照着跑就能复现。

```bash
git clone -b springboot4/i18n git@github.com:springvortex/lab.git
```

---

## 目录

1. [这分支是怎么来的](#1-这分支是怎么来的)
2. [国际化到底是个啥](#2-国际化到底是个啥)
3. [为什么要折腾这个](#3-为什么要折腾这个)
4. [先跑起来看看效果](#4-先跑起来看看效果)
5. [代码里怎么用](#5-代码里怎么用)
6. [配置怎么改，能改哪些值](#6-配置怎么改能改哪些值)
7. [一次请求到底发生了什么](#7-一次请求到底发生了什么)
8. [我踩过的 4 个坑](#8-我踩过的-4-个坑)
9. [想加一门新语言](#9-想加一门新语言)
10. [怎么自检](#10-怎么自检)

---

## 1. 这分支是怎么来的

先交代下背景。本分支 `springboot4/i18n` 是从主脚手架分支 **`template`** 拉出来的：

```bash
git switch -c springboot4/i18n template
```

`template` 是 Boot 4.1.1 + JDK 25 的脚手架，统一响应、traceId、异步、跨域、Jackson、Actuator、接口文档这些它都已经有了（想看全貌翻 `template` 分支的 README）。我拉这个分支只干一件事：把国际化加上去。

所以本分支新增的东西很集中：

```
src/main/java/com/zjc/demo/config/I18nConfig.java     # 新增：消息源 + 语言解析器，就这一个入口
src/main/java/com/zjc/demo/util/MessageUtils.java     # 新增：业务取词用的工具类
src/main/resources/config/application-i18n.yaml       # 新增：i18n 独立 profile
src/main/resources/i18n/messages.properties           # 新增：基名文件，最后兜底用
src/main/resources/i18n/messages_zh.properties        # 新增：中文
src/main/resources/i18n/messages_en.properties        # 新增：英文
src/main/resources/application.yaml                   # 改：include 里加个 i18n
```

另外 `ApiResponseConstant`、`ErrorCodeConstant`、`BusinessException`、`ApiResponse`、`GlobalExceptionHandler` 这几个都只动了很小一块——给枚举加了个 `messageKey` 字段，第 5 节会讲为什么这么加。

---

## 2. 国际化到底是个啥

说白了就是一件事：**把写死在 Java 里的那句"资源不存在"，换成一个 key，真正的句子挪到 `.properties` 里，请求进来的时候按语言取出来。**

以前这么写：

```java
throw new BusinessException(404, "资源不存在");   // 中文焊死在代码里
```

现在这么写：

```java
throw new BusinessException(ApiResponseConstant.NOT_FOUND);   // 只写枚举
```

句子放在文件里：

```properties
# i18n/messages_zh.properties
response.not-found=资源不存在

# i18n/messages_en.properties
response.not-found=Resource not found
```

请求带 `Accept-Language: zh-CN` 就返回中文，带 `en-US` 就返回英文。业务代码完全不知道有这回事。

整条链路上就三个东西，认个脸就行：

- **消息源**（`MessageSource`）：你给它一个 key 和一个语言，它返回一句话。本分支在 `I18nConfig#messageSource()`。
- **语言解析器**（`LocaleResolver`）：判断"这次请求该用哪门语言"。本分支在 `I18nConfig#localeResolver()`。
- **资源文件**：就是那几个 `.properties`，在 `src/main/resources/i18n/`。

---

## 3. 为什么要折腾这个

最实在的一条：**加一门语言不用动 Java**。

以前要加英文，得把所有 `if/else` 翻一遍，漏一处就漏一句，而且漏了你还不一定发现得了。现在复制一份 `.properties` 翻译完就完事。

其他几条也挺实在：

- **改文案不用发版**。运营要改一句提示语，改资源文件就行，不用走编译部署。
- **`Accept-Language` 是 HTTP 标准头**。浏览器和 HTTP 客户端本来就会带，你不用跟前端约定什么 `lang` 参数，也不用往 URL 或 body 里塞东西。
- **一个接口就够了**，不用中英文各写一遍。

再补一句：**日志不要跟着切**。日志是给运维看的，如果它也跟着请求语言变，检索的时候中文英文混在一起会很痛苦。所以本分支只让响应文案跟随语言，日志保持原样。这是刻意的，不是漏了。

---

## 4. 先跑起来看看效果

别急着看代码，先跑起来有个直观印象。

```bash
mvn -o clean verify
java -jar target/spring-vortex-demo-0.0.1.jar --server.port=8000
```

同一个接口，换个头试试：

```bash
curl --noproxy '*' -H "Accept-Language: zh-CN" localhost:8000/hello
# {"success":true,"code":200,"message":"操作成功",...}

curl --noproxy '*' -H "Accept-Language: en-US" localhost:8000/hello
# {"success":true,"code":200,"message":"Success",...}
```

异常响应一样会切：

```bash
curl --noproxy '*' -H "Accept-Language: en-US" localhost:8000/not-exist
# {"success":false,"code":404,"message":"Resource not found",...}

curl --noproxy '*' -X DELETE -H "Accept-Language: en-US" localhost:8000/hello
# {"success":false,"code":405,"message":"DELETE method is not supported",...}
```

Postman 里也简单：请求 `GET localhost:8000/hello`，在 **Headers** 标签页加一行 `Accept-Language: en-US`（值换成 `zh-CN` 就是中文）。有个小坑——如果你看到 Headers 里有个灰色的、Postman 自动生成的 `Accept-Language`，得**先取消它的勾选**再自己加一行，不然会被自动值盖掉。

### 语言是这么选出来的

| 请求头 | 结果 |
|---|---|
| `zh-CN` / `zh` / `zh-Hans` | 中文 |
| `en-US` / `en-GB` / `en` | 英文 |
| 不带头 / 头是乱的 / 不支持的语言（比如 `fr-FR`） | 回落默认语言，可配置，见第 6 节 |

---

## 5. 代码里怎么用

### 5.1 最常见：抛异常

```java
throw new BusinessException(ApiResponseConstant.NOT_FOUND);      // 内置的
throw new BusinessException(UserErrorCode.USER_DISABLED);        // 自定义的也一样
```

枚举现在长这样，多了第三个参数：

```java
public enum ApiResponseConstant implements ErrorCodeConstant {
    SUCCESS(200, "操作成功", "response.success"),
    NOT_FOUND(404, "资源不存在", "response.not-found"),
    //       ↑状态码    ↑兜底文案         ↑资源文件里的 key
    ;
}
```

你可能会问：都国际化了，干嘛还留第二个参数？

因为资源文件里可能漏配某个 key。漏了的话，我不希望接口报错，更不希望把 `response.not-found` 这种标识符直接吐给调用方。所以取不到词的时候回落这句固定中文——接口照常可用，最坏情况就是语言没切过去。

### 5.2 自定义枚举：可以一点都不改

如果你已经有自己的错误码枚举，**什么都不动就能用**：

```java
public enum UserErrorCode implements ErrorCodeConstant {
    USER_DISABLED(403, "账号已被禁用");

    private final int code;
    private final String message;

    UserErrorCode(int code, String message) { this.code = code; this.message = message; }

    @Override public int code() { return code; }
    @Override public String message() { return message; }
    // messageKey() 在接口里有 default 实现，返回 null，于是自动用 message() 兜底
}
```

想让它也支持多语言，再覆写一个方法就行：

```java
@Override public String messageKey() { return "user.disabled"; }
```

### 5.3 自己写业务文案

注解里写 key，取值时用 `MessageUtils`：

```java
throw new BusinessException(MessageUtils.getMessage("order.not-enough-stock", skuId));
```

### 5.4 没有请求上下文的时候

定时任务、MQ 消费这种场景没有请求头，得**自己指定语言**：

```java
String msg = MessageUtils.getMessage("mail.subject", Locale.ENGLISH);
```

`MessageUtils` 常用的就这几个：

| 方法 | 什么时候用 |
|---|---|
| `getMessage(key)` | 按当前请求语言取 |
| `getMessage(key, args...)` | 带占位符，填 `{0}` `{1}` |
| `getMessage(key, locale)` | 指定语言 |
| `getMessageOrDefault(key, fallback, locale)` | 取不到就用兜底文案 |

### 哪些文案故意没国际化

说清楚，免得你以为是漏了：

- `@NotBlank(message = "...")` 的校验文案——业务自己写的，翻不翻你定；
- Jackson 解析错误、框架的 `detail`——这是排查用的技术信息，翻译了反而丢线索；
- `log.warn(...)` 里的日志——理由前面说过了；
- Swagger 上的展示文案——文档元信息，不随请求变。

---

## 6. 配置怎么改，能改哪些值

这节是重点，也是最容易搞混的地方。先给结论：

> **这个分支里真正生效的配置只有一项：`app.i18n.default-locale`。**

配置文件在 `src/main/resources/config/application-i18n.yaml`，靠 `application.yaml` 的 `include` 挂进来：

```yaml
# application.yaml
spring:
  profiles:
    active: dev
    include:
      - pub
      - cors
      - i18n          # ← 挂着这个
```

### 6.1 想让英文当默认语言？改一行

```yaml
# config/application-i18n.yaml
app:
  i18n:
    default-locale: zh_CN     # 请求头缺失、乱填、或者不支持时用这个
```

改成英文就是把这行换成 `en`。**不用动 Java，也不用重新打包**：

```bash
# 直接改配置文件
app:
  i18n:
    default-locale: en

# 或者启动时覆盖，部署时这么干最方便
java -jar app.jar --app.i18n.default-locale=en
```

#### 能填哪些值

| 填法 | 实际生效 | 说明 |
|---|---|---|
| `zh` | 中文 | 最省事 |
| `zh-CN` | 中文 | 连字符 |
| `zh_CN` | 中文 | 下划线，配置里这么写也认 |
| `en` | 英文 | |
| `en-US` | 英文 | 带区域也会被归一，跟 `en` 一样 |
| `en-GB` | 英文 | 同上 |

可用值必须落在 `I18nConfig#SUPPORTED_LOCALES` 里，现在是 `zh` 和 `en` 两种。

填错会怎样？**直接起不来**，不会给你默默糊过去：

```bash
java -jar app.jar --app.i18n.default-locale=fr-FR
# java.lang.IllegalStateException:
#   app.i18n.default-locale=fr-FR 的语言不在支持清单内，可用值: [zh, en]。
#   新增语言请同时补 i18n/messages_<语言>.properties
```

这么设计是有原因的：要是放行，就会出现"不带头请求返回法文、但法文资源文件根本不存在"的状态——接口照样 200，文案却悄悄回落到基名文件，你根本发现不了。不如启动时炸出来。

### 6.2 那几项看着像配置的，其实不生效

同一个文件里还有这些：

```yaml
spring:
  messages:
    basename: i18n/messages
    encoding: UTF-8
    fallback-to-system-locale: false
    use-code-as-default-message: true
  web:
    locale-resolver: accept-header
```

不是写错了，是刻意的——这几项在本分支里**改了也没用**：

| 配置 | 为什么不生效 | 真正生效在哪 |
|---|---|---|
| `spring.messages.*` | `I18nConfig` 自己定义了叫 `messageSource` 的 Bean，Boot 的自动配置带 `@ConditionalOnMissingBean`，直接退避了 | `I18nConfig` 第 186 行 `setBasenames("classpath:i18n/messages")` |
| `spring.web.locale-resolver` | 同理，`LocaleResolver` 也被我们自定义了 | `I18nConfig` 第 221-222 行 |

留着它们纯粹是**给人看的**：让人一眼看出编码是 UTF-8、兜底策略是什么。但**你改它不报错也不生效**，属于经典的静默陷阱。Boot 自带的 `spring.web.locale` 在这里同样无效，别指望它。

真要改资源文件的目录，动代码那一行：

```java
// I18nConfig#messageSource()
messageSource.setBasenames("classpath:i18n/messages");
//                                    ↑ 必须带 classpath: 前缀，用 / 分隔
```

### 6.3 支持哪些语言在哪改

这个是编译期常量，写在 `I18nConfig`：

```java
public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.CHINESE, Locale.ENGLISH);
```

想加语言就往 `List.of(...)` 里塞一项，第 9 节说。

---

## 7. 一次请求到底发生了什么

```
GET /hello
  │  Accept-Language: en-US
  ▼
① LocaleResolver 判断这次用哪门语言
  │   看请求头，去 SUPPORTED_LOCALES([zh, en]) 里找
  │   找到 en → 返回 Locale.ENGLISH
  │   没带头 / 找不到 → 返回 app.i18n.default-locale 配的那个
  ▼
② LocaleContextHolder 把语言存进当前线程
  │   后面任何地方都能读到，不用把 Locale 一层层往下传
  ▼
③ Controller 抛异常 / 组装响应
  ▼
④ MessageSource 按 key + 语言查句子
  │   key = "response.not-found"，语言 = en
  │   查找顺序：精确(messages_en_US) → 语言级(messages_en) → 基名(messages)
  ▼
⑤ 响应体
  {"code":404,"message":"Resource not found"}
```

### 兜底有三层，缺哪层都不会把接口打挂

| 情况 | 会怎样 |
|---|---|
| 当前语言缺这个 key | 回落基名文件 `i18n/messages.properties` |
| 三个文件都缺这个 key | 返回 **key 本身**，响应里直接能看到漏了哪个，比静默好排查 |
| 枚举没配 `messageKey`（是 `null`） | 用枚举自带的固定文案，老枚举零改动就能用 |

---

## 8. 我踩过的 4 个坑

这几个坑有个共同点特别讨厌：**错了不报错、不打日志，就是悄悄不对。**

### 坑 1：语言清单要写"语言级"，别写国家/地区级

```java
List.of(Locale.CHINESE, Locale.ENGLISH)          // 对，country 是空的
List.of(Locale.US, Locale.SIMPLIFIED_CHINESE)    // 错，带 country
```

原因是 `AcceptHeaderLocaleResolver` 找不到精确匹配时，只会拿"语言相同**且 country 为空**"的清单项来兜底。你要是写了 `Locale.US`，那 `en-GB`、`en-AU` 既找不到精确匹配、又过不了兜底判定，就直接回落默认语言了。

实测对照：

| 清单写法 | `en-US` 请求 | `en-GB` 请求 |
|---|---|---|
| `[zh, en_US]` | 英文 | 中文（挂了） |
| `[zh, en]`（本项目写法） | 英文 | 英文 |

补充一句：本项目要求**每一项**都语言级，不只是非默认项。因为默认语言是可配置的——今天默认中文，明天你改成 `en`，中文就变成"非默认语言"了，这时候它要是带着 country，`zh-TW` 就会静默回落。全写成语言级，默认值才能随便换。

### 坑 2：资源文件也得用语言级命名

```
i18n/messages_zh.properties      对
i18n/messages_en.properties      对
i18n/messages_en_US.properties   错
```

`ResourceBundle` 的查找顺序是「精确 → 语言 → 基名」，语言级文件一份就能覆盖这门语言的所有区域变体。**两边粒度必须一致**，不然会出现"解析器归一到了 `en`、却没有 `messages_en.properties`"这种空转。

### 坑 3：`setSupportedLocales` 那行别删

`AcceptHeaderLocaleResolver` **默认只支持 `Locale.getDefault()` 一门语言**。也就是说在一台中文机器上，英文请求照样返回中文，而且一声不吭。`I18nConfig` 里显式调 `setSupportedLocales` 就是为了堵这个。

### 坑 4：`include: i18n` 删了也不会红

把 `application.yaml` 里的 `i18n` 去掉，国际化配置整体不加载。但 `I18nConfig` 代码里有同名默认值兜着，**测试和冒烟都不会红**——你改的配置就是全都不生效而已。

`ProfileConfigTest#i18nPropertiesAreBound` 这条测试就是专门盯着它的。

### 改了配置没反应？按这个顺序查

1. 改的是 `app.i18n.default-locale` 吗？`spring.messages.*` 是不生效的（见 6.2）。
2. 值落在 `zh` / `en` 里吗？写错会启动失败，翻启动日志。
3. 资源文件在 `src/main/resources/i18n/` 下吗？基名是 `messages` 吗？

---

## 9. 想加一门新语言

**只是切换默认语言的话不用看这节**，改 `app.i18n.default-locale` 就够了。

真要加一门（比如日语），改三处：

1. **加文件**：复制 `i18n/messages.properties`，改名 `i18n/messages_ja.properties`，翻译。**别删 key**，key 集合必须和基名文件一模一样。
2. **加清单**：`I18nConfig` 的 `SUPPORTED_LOCALES` 里加 `Locale.JAPANESE`（记住要语言级）。
3. **（可选）设为默认**：想让它当默认，把 `app.i18n.default-locale` 改成 `ja`。

三处缺一不可，每处都有测试盯着：

| 漏了哪处 | 会怎样 | 谁盯着 |
|---|---|---|
| 资源文件 | 该语言下所有文案回落基名文件，响应 200 但语言全错 | `MessageUtilsTest#allBundlesShareSameKeys` |
| `SUPPORTED_LOCALES` | 该语言被判为"不支持"，回落默认语言 | `I18nConfigTest#resolverIsPopulated` |
| 清单写成国家/地区级 | 该语言的区域变体静默回落 | `I18nConfigTest#nonDefaultEntriesAreLanguageLevel` |
| `include: i18n` | 整个 i18n profile 静默失效 | `ProfileConfigTest#i18nPropertiesAreBound` |

key 的命名按「模块.语义」分三组：

| 前缀 | 干嘛的 | 会上响应体吗 |
|---|---|---|
| `response.` | 统一响应码的文案 | 会 |
| `request.` | 请求参数相关提示 | 会 |
| `error.` | 开发期错误，比如工具类被反射实例化 | 不会 |

占位符用 `{0}` `{1}`：

```properties
response.method-not-allowed={0} 方法不支持
request.missing-parameter=缺少必填参数: {0}
```

---

## 10. 怎么自检

```bash
# 全量构建 + 测试 + 覆盖率门禁（178 个用例）
mvn -o clean verify

# 覆盖率报告在 target/site/jacoco/index.html
```

有句话得说在前面：本分支从 `template` 继承了**一条已知失败的用例**
`ProfileConfigTest#appPropertiesAreExplicit`。它断言生产环境跨域已收敛成具体域名，
但 `application-prod.yaml` 里并没有那条覆盖——是 `template` 的历史遗留，本分支没动它。
除此之外全部通过，覆盖率门禁照常生效。

冒烟：

```bash
java -jar target/spring-vortex-demo-0.0.1.jar --server.port=8000

curl --noproxy '*' localhost:8000/hello                              # 默认语言
curl --noproxy '*' -H "Accept-Language: zh-CN" localhost:8000/hello  # 中文
curl --noproxy '*' -H "Accept-Language: en-US" localhost:8000/hello  # 英文
```

验证配置真的能改默认语言（不改代码、不重新打包）：

```bash
java -jar target/spring-vortex-demo-0.0.1.jar --server.port=8000 --app.i18n.default-locale=en
curl --noproxy '*' localhost:8000/hello     # → Success
```

两条环境提醒：

1. 终端默认 JDK 可能是 8，**必须先切到 JDK 25**（`export JAVA_HOME=D:/app/Java/jdk-25.0.2`），不然编译直接失败。
2. `curl` 走代理会返回奇怪的东西，**记得加 `--noproxy '*'`**。

---

## 附录：这分支到底改了什么

| 文件 | 改了啥 |
|---|---|
| `config/I18nConfig.java` | 新增。消息源 + 语言解析器，默认语言从配置读，还会归一和校验 |
| `util/MessageUtils.java` | 新增。业务取词的唯一入口 |
| `config/application-i18n.yaml` | 新增。`app.i18n.default-locale` + 说明性质的 `spring.messages.*` |
| `i18n/messages{,_zh,_en}.properties` | 新增。17 个 key × 3 份（key 集合必须完全一致） |
| `application.yaml` | 改。`include` 加 `i18n` |
| `constant/ApiResponseConstant.java` | 改。每项加 `messageKey` |
| `constant/ErrorCodeConstant.java` | 改。加 `messageKey()` 的 `default` 实现（返回 `null`，老枚举零改动兼容） |
| `web/ApiResponse.java` | 改。默认文案走 `MessageUtils` 取 |
| `exception/BusinessException.java` | 改。同上 |
| `exception/GlobalExceptionHandler.java` | 改。404/405/400/406 这些框架状态码异常的文案也接进国际化 |
| `constant/TraceConstant.java` | 改。私有构造器的提示文案挪进资源文件 |
| 测试 | 新增 `I18nConfigTest`（16）、`MessageUtilsTest`（20）、`AsyncConfigTest`、`AsyncTaskMetricsDecoratorTest`；`ApiIntegrationTest`（36）里加了语言协商和框架状态码用例 |

脚手架其他部分（统一响应、traceId、异步、跨域、Jackson、Actuator、接口文档）没动，
要看去 **`template`** 分支的 README。
