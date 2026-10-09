# 别再把数据库密码提交进 Git 了：Spring Boot 4 集成 Jasypt 保姆级教程

> 本 README 位于 `springboot4/jasypt` 分支，从 `template`（Spring Boot 4.1.1 / JDK 25）拉出。
> 分支只讲一件事：**给配置文件里的敏感项加密**，其余部分与模板保持一致。

```bash
git clone -b springboot4/jasypt git@github.com:springvortex/lab.git
cd lab
```

---

## 一、先说痛点：你的密码正在被提交进 Git

写完一个 Spring Boot 项目，`application-dev.yaml` 大概长这样：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo
    username: root
    password: 123456          # ← 问题就在这一行
```

本地跑没问题。但只要做三件事里的任意一件，它就会出问题：

1. **提交到 Git** —— 哪怕是私有仓库。历史记录里的密码是永久性的，改密码得改历史（`filter-repo`
   重写整个仓库），团队里每个人都要重新 clone。实习生 clone 完顺手传个网盘？你已经输了。
2. **打进 Docker 镜像** —— `application-prod.yaml` 跟着 jar 一起进镜像，谁拿到镜像都能 `unzip` 看。
   镜像在私服上放三年，三年里离职的每个人手里都有。
3. **日志/报错里被打出来** —— 配置绑定失败时 Spring 会把整个 `Environment` 的关键信息打出来。

常见的「解决方案」有两个，都不太顶用：

| 做法 | 问题 |
|---|---|
| 生产环境用环境变量 `SPRING_DATASOURCE_PASSWORD` | **做对了**，但只解决了生产。dev / test / 联调环境的密码还在仓库里，而联调环境的库往往是能连通生产的 |
| 把配置文件加进 `.gitignore` | 新人 clone 下来跑不起来，只能靠口口相传「找老王要一份配置」。几周后老王也不记得自己改过什么 |

> 顺带说一句：环境变量也不是银弹。`docker inspect`、进程的 `/proc/<pid>/environ`、CI 的日志
> 回显都能看到它。它解决的是「密码不进代码仓库」，不是「密码绝对看不到」。

我们想要的是一种**密码以密文形态出现在仓库里、运行时自动变回明文**的方案。
这就是 Jasypt 干的事。

---

## 二、是什么：Jasypt 到底做了什么

一句话：**Jasypt 是一个「让配置文件支持 `${}` 式加密占位符」的库，加密后的值写成
`ENC(密文)`，应用启动时框架在读取配置的那一刻自动解密。**

写法长这样：

```yaml
spring:
  datasource:
    password: ENC(nKoyS1njhAMI5h0nY9X1YKkMSIFQ2MGtJDPo+Ga4qgQKljseB6Oq17KyxlL1wgHx)
```

运行时，业务代码里 `@Value("${spring.datasource.password}")` 拿到的**已经是明文**
`123456`。代码一个字都不用改。

这背后的机制值得说清楚，因为它是后面所有「为什么它这么配」的根源：

### 它靠什么生效：两条注册路径

它走的是**比自动配置更早的一条路**：在 `Environment` 组装阶段，把每一个 `PropertySource` 用
`EncryptableMapPropertySourceWrapper` 包一层，然后再启动上下文。

starter 的 jar 里同时有两个注册文件（`unzip -l` 实测，4.0.4）：

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    → com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration
META-INF/spring.factories
    → org.springframework.boot.autoconfigure.EnableAutoConfiguration=JasyptSpringBootAutoConfiguration
    → org.springframework.cloud.bootstrap.BootstrapConfiguration=JasyptSpringCloudBootstrapConfiguration
```

而真正的加解密配置在传递依赖 `jasypt-spring-boot` 里，那里另有一个 `spring.factories` 注册了
两个 `EnvironmentPostProcessor` 和一个 `ApplicationListener`。汇合点是
`JasyptSpringBootAutoConfiguration` 上的 `@Import(EnableEncryptablePropertiesConfiguration.class)`，
由它注册 `EnableEncryptablePropertiesBeanFactoryPostProcessor` 去包装属性源。

为什么值得搞清楚这些？因为**启动日志里能看到它生效的证据**：

```
Converting PropertySource applicationConfig: [classpath:/application-dev.yaml] to EncryptableMapPropertySourceWrapper
```

看到这行，说明属性源已经被包上了。由此带来两个结论：

- 解密发生在 **`Environment` 层**，比 `@Value`、`@ConfigurationProperties`、`Environment#getProperty`
  都要早；
- 也就是说，**所有**读配置的路径都被覆盖了，不需要你在每处手动 `decrypt`。

### 它懒加载密钥

加密器本身是懒的：`DefaultLazyEncryptor` 用 `Singleton<>` 持有真正的加密器，
**构造时不创建，第一次 `encrypt` / `decrypt` 才初始化**。所以：

> **如果你的配置里没有任何 `ENC(...)`，就算没配密码，应用也能正常启动。**
> 只有真正去读一个 `ENC(...)` 值时才会报错。

⚠️ **但别拿「能启动」当「配对了」的证据。** 在本项目里恰恰相反：
`application.yaml` 有 `demo: ENC(...)`，`JasyptController` 又在**启动期**用 `@Value("${demo}")`
注入它——一读就触发解密，于是**启动直接失败**（第五节的本地验证里有完整报错）。
懒加载只在「启动期没人读那个 `ENC(...)`」时才帮得上忙。

一句话：`ENC(...)` 值被谁、什么时候读，决定了你是「启动就炸」还是「请求时才炸」。
两种都比「静默用错值」好。

### 它不解决什么

诚实起见，下面这些 Jasypt **做不到**：

- **它不是「加密存储」**：密钥在你手里，谁拿到密钥谁就能解开所有密文。Jasypt 防的是
  「密码躺在 Git 历史里被人看到」，不是「数据库被拖库」。
- **它不能替代密钥管理服务**：如果要更硬的保证，应该上 KMS / Vault / 云厂商的密管服务，
  让密钥根本不落到配置文件或环境变量里。Jasypt 是「低成本、够用」的那一档。
- **密文不可做等值查询**：默认随机 IV，同一个明文每次加密结果都不同，所以密文不能拿来比对、去重或做 `WHERE x = ?`。这是刻意的——否则攻击者能靠「两次密文是否相同」推断明文是否相同。

---

## 三、为什么选它：三个候选摆一起看

| 方案 | 密码在仓库里 | 启动时是否要额外操作 | 复杂度 | 适合 |
|---|---|---|---|---|
| 全用环境变量 | ✅ 不出现 | 否（但要保证注入） | 低 | 生产够用，dev/test 不好管 |
| 配置中心（Nacos / Apollo） | ✅ 不出现 | 否 | 高 | 微服务、多环境统一管理 |
| **Jasypt** | ✅ 只出现密文 | 否 | **低** | 单体 / 中小项目，想「一把梭」解决全部环境 |

Jasypt 的核心优势就一条：**改动小、心智负担低**。
你只需要在现有 YAML 外面套一层 `ENC(...)`，不用引入新组件、不用改启动脚本、
不用让运维加一堆环境变量。而它同时做到了「dev/test/prod 三套密码都能用同一套机制管理」。

---

## 四、怎么用：四步搞定

### 步骤 1：加依赖

⚠️ **Jasypt 不在 Spring Boot 的依赖管理（BOM）里**，必须自己锁版本。

```xml
<properties>
    <!-- 配置项加密：jasypt 4.x 起支持 Boot 3.5+ 与 JDK 17+，不跟随 Boot 依赖管理 -->
    <jasypt.version>4.0.4</jasypt.version>
</properties>

<dependencies>
    <!-- 配置项加密：ENC(...) 包裹的值在 Environment 层透明解密，需显式锁版本 -->
    <dependency>
        <groupId>com.github.ulisesbocchio</groupId>
        <artifactId>jasypt-spring-boot-starter</artifactId>
        <version>${jasypt.version}</version>
    </dependency>
</dependencies>
```

**版本怎么选**（这是最容易栽的一步）：

| Jasypt | 支持的 Spring Boot | 支持的 JDK |
|---|---|---|
| 2.x | Boot 2.x | 8+ |
| 3.0.x | 只到 Boot 3.5 | 8 ~ 21 |
| **4.x** | **Boot 3.5+ / 4.x** | **17+** |

本项目是 Spring Boot 4.1.1 + JDK 25，只有 **4.x** 能用。另外注意：starter 不带 `spring.factories`
之外的注册信息，所以即使版本错了，也不会在编译期报错——只会在运行期表现为「`ENC(...)` 没被解开」
或直接 `ClassNotFoundException`。

传递依赖链：`jasypt-spring-boot-starter:4.0.4` → `jasypt-spring-boot:4.0.4` → `org.jasypt:jasypt:1.9.3`。

### 步骤 2：新增独立配置文件

本项目按「一个主题一个配置文件」来组织，所以 jasypt 的配置独立成
`src/main/resources/config/application-jasypt.yaml`：

```yaml
jasypt:
  encryptor:
    algorithm: PBEWITHHMACSHA512ANDAES_256
    key-obtention-iterations: 1000
    pool-size: 1
    string-output-type: base64
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator
    property:
      prefix: ENC(
      suffix: )
```

然后在 `application.yaml` 的 `include` 里把它叠进去：

```yaml
spring:
  application:
    name: SpringVortexDemo
  profiles:
    active: dev
    include:
      - pub
      - cors
      - jasypt      # ← 新增
```

> **为什么单独一个文件而不是写进 `application-pub.yaml`？**
> 一是职责清晰，加密相关的配置都在一处；二是方便整个去掉——不想要 Jasypt 时删掉 `include` 里
> 那一行 + 删掉文件即可，不会在公共配置里留残渣。
>
> ⚠️ **注意「漏 include」是静默失效。** `application-jasypt.yaml` 写得再对，不挂进 `include`
> 就是一堆死配置：算法回到默认值，而你可能察觉不到——因为默认值恰好也是这套。
> 项目里 `ProfileConfigTest#jasyptPropertiesAreBound` 专门钉死了这件事。

### 步骤 3：按环境配置密钥

这是整个方案里最需要动脑的一步。**密钥（password）不能加密自己**，它必须明文出现，
所以得靠环境的差异来保护它。

**`config/application-dev.yaml`（开发）—— 明文写死，开箱即用：**

```yaml
# 开发环境的加密密钥
jasypt:
  encryptor:
    password: Vortex@2026
```

为什么 dev 可以直接写明文？因为 dev 的密码只用来解 dev 的配置，而 dev 配置本身也没什么值得保护的。
**别把生产密钥写在这。** 更重要的是：**dev 和 prod 必须用不同的密钥**，
这样即使 dev 的密钥泄露，也解不开生产的密文。

**`config/application-prod.yaml`（生产）—— 环境变量：**

```yaml
# 生产环境的加密密钥：从环境变量读，配置文件里不落地明文
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}
```

⚠️ **刻意不给默认值**（不写成 `${JASYPT_ENCRYPTOR_PASSWORD:默认值}`）。
给了默认值等于把「忘记注入环境变量」变成一个静默故障——应用正常启动，
然后在某个请求上抛解密异常。**宁可在启动时就直接失败**（下面会实测这件事）。

> 你可能会看到「Jasypt 是懒加载的，不配密钥也能启动」这种说法。**在本项目里不成立**，
> 原因是 `application.yaml` 里有个 `demo: ENC(...)`，而 `JasyptController` 用
> `@Value("${demo}")` 在**启动期**就要注入它——读到这个 `ENC(...)` 就会触发解密，
> 于是 Bean 创建失败、应用起不来。这比「静默启动、请求时才炸」好得多，实测：
>
> ```
> Error creating bean with name 'jasyptController': Injection of autowired dependencies failed
> Caused by: DecryptionException: Unable to decrypt property: ENC(nKoyS1...)
>            Decryption of Properties failed, make sure encryption/decryption passwords match
> Caused by: org.jasypt.exceptions.EncryptionOperationNotPossibleException
> ```
>
> 「懒加载」的真实含义是：**只要你的 `ENC(...)` 值没有在启动期被读取**（比如它挂在某个
> 懒初始化的 Bean 上），应用就能启动。所以别拿「能启动」当「配对了」的证据。

启动方式：

```bash
# 命令行 / systemd / Docker Compose 都一样，把环境变量注入进去
export JASYPT_ENCRYPTOR_PASSWORD='生产用的那一串密钥'
java -jar spring-vortex-demo.jar --spring.profiles.active=prod

# Docker
docker run -e JASYPT_ENCRYPTOR_PASSWORD='xxx' your-image
```

### 步骤 4：生成密文，替换配置

现在需要一个「把明文变成密文」的工具。我们写了 `JasyptService`（加解密逻辑落在
`JasyptServiceImpl`，内部直接注入 jasypt 的 `StringEncryptor`），
并把它暴露成了 HTTP 接口，本地起服务就能用：

```bash
# 起服务
mvn -o spring-boot:run

# 加密
curl "http://localhost:8000/api/jasypt/encrypt?plainText=my-db-password"
# → {"success":true,"code":200,"message":"操作成功","data":"OdOtqwfwjUqGCv/2l/xsY9gyBz9z/...","traceId":"..."}

# 解密（验证密文对不对）：注意用 --data-urlencode，别手工拼 URL，原因见下方⚠️
curl -G --data-urlencode "cipherText=OdOtqwfwjUqGCv/2l/xsY9gyBz9z/..." \
     "http://localhost:8000/api/jasypt/decrypt"
# → {"success":true,"code":200,"message":"操作成功","data":"my-db-password",...}
```

> ⚠️ **密文里的 `+` 会被 URL 吃掉，必须编码。** 密文默认是 Base64，字符集里含 `+` 和 `/`；
> 而在 query string 里 `+` 的含义是「空格」。所以**手工拼接**的
> `?cipherText=abc+def` 传到服务端会变成 `abc def`，解密直接失败——而且是 **500**，
> 因为服务端认为这是「密钥/密文配置问题」。实测 Base64 密文含 `+` 的概率相当高（六次随机加密里三次都有），
> 别抱侥幸。正确做法是让工具替你编码：curl 用 `-G --data-urlencode`，
> 浏览器/前端用 `encodeURIComponent`。
>
> 顺带一提，这也说明 `encrypt` / `decrypt` 用 GET 传密文并不是个好设计，
> 生产场景请改成 POST + 请求体。

拿到 `data` 里的密文，补上 `ENC(...)` 写进配置：

```yaml
spring:
  datasource:
    password: ENC(OdOtqwfwjUqGCv/2l/xsY9gyBz9z/DMn6mpIpubpsU0kQzRnemsJJTtaAhTxZLp3)
```

**验证透明解密真的生效了**：项目里有个 `/api/jasypt/demo` 接口，它读的是 `application.yaml` 里
一个 `ENC(...)` 包裹的配置值：

```yaml
demo: ENC(nKoyS1njhAMI5h0nY9X1YKkMSIFQ2MGtJDPo+Ga4qgQKljseB6Oq17KyxlL1wgHx)
```

```bash
curl "http://localhost:8000/api/jasypt/demo"
# → data 里是明文，不是 ENC(...) 那一串
```

如果返回的还是 `ENC(....)` 原文，说明解密没生效——回去检查 `include` 和密钥。

---

## 五、本地验证

```bash
# 全量测试（134 个用例）
JAVA_HOME="D:/app/Java/jdk-25.0.2" mvn -o clean test

# 启动
JAVA_HOME="D:/app/Java/jdk-25.0.2" mvn -o spring-boot:run

# 冒烟：下面五条已实测，返回码如注释所示
curl --noproxy '*' "http://localhost:8000/api/jasypt/encrypt?plainText=hello"      # 200 返回密文
curl --noproxy '*' "http://localhost:8000/api/jasypt/encrypt?plainText="           # 400 encrypt.plainText: 明文不能为空
curl --noproxy '*' "http://localhost:8000/api/jasypt/encrypt"                      # 400 缺少必填参数: plainText
curl --noproxy '*' "http://localhost:8000/api/jasypt/decrypt?cipherText=abc"       # 500 非法密文，且不泄露异常细节
curl --noproxy '*' "http://localhost:8000/api/jasypt/demo"                         # 200 配置里的 ENC(...) 已被解成明文

# 往返验证：加密后立刻解回来
# ⚠️ 密文含 + 时手工拼 URL 会被解成空格导致 500，必须用 --data-urlencode
CT=$(curl -s --noproxy '*' "http://localhost:8000/api/jasypt/encrypt?plainText=my-db-password" \
     | python -c "import sys,json;print(json.load(sys.stdin)['data'])")
curl -s -G --data-urlencode "cipherText=$CT" --noproxy '*' \
     "http://localhost:8000/api/jasypt/decrypt"
```

**验证「生产没配环境变量会不会挂」**：

```bash
# 不带环境变量直接以 prod 启动
JAVA_HOME="D:/app/Java/jdk-25.0.2" mvn -o spring-boot:run -Dspring-boot.run.profiles=prod
```

**实测结果是启动直接失败**（这正是我们想要的）：

```
Error creating bean with name 'jasyptController': Injection of autowired dependencies failed
Caused by: DecryptionException: Unable to decrypt property: ENC(nKoyS1...)
           Decryption of Properties failed, make sure encryption/decryption passwords match
Caused by: org.jasypt.exceptions.EncryptionOperationNotPossibleException
```

因为 `JasyptController` 的 `@Value("${demo}")` 在启动期就要读那个 `ENC(...)` 值，
占位符 `JASYPT_ENCRYPTOR_PASSWORD` 解不出来 → 拿不到密钥 → 解密失败 → Bean 创建失败。
**宁可起不来，也不要带着一个随时会炸的配置跑起来。**

覆盖率：Jasypt 相关两个类（`JasyptServiceImpl` / `JasyptController`）
均为 **100% 行 + 分支覆盖**；全项目行覆盖 96.53%。

---

## 六、参考

- 官方仓库：[ulisesbocchio/jasypt-spring-boot](https://github.com/ulisesbocchio/jasypt-spring-boot)
- 模板基线：`template` 分支（Spring Boot 4.1.1 / JDK 25）
- 本项目仓库：[springvortex/lab](https://github.com/springvortex/lab)

```bash
git clone -b springboot4/jasypt git@github.com:springvortex/lab.git
```
