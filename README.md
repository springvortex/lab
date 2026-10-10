# 脚手架先搭好，剩下只管写业务

Spring Boot 4.1.1 + JDK 25 的后端模板：统一响应、全局异常、链路追踪、参数校验、MyBatis-Plus 全套配好，
拿到就能开新模块，不用每次从零配一遍。

```bash
git clone -b release/v1.0.0 git@github.com:springvortex/lab.git
```

---

## 1. 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 4.1.1 | 父 POM 统一管理版本 |
| JDK | 25 | 构建和运行都必须是 25 |
| PostgreSQL | 17.6 | 驱动 42.7.13 |
| MyBatis-Plus | 3.5.17 | 另有 `mybatis-plus-jsqlparser`——分页插件自 3.5.9 起被拆出，不引会编译不过 |
| springdoc | 3.1.1 | Swagger UI，**大版本要跟 Boot 对齐**（2.x 对应 Boot 3，3.x 对应 Boot 4） |
| jasypt | 4.0.4 | 配置项加密，4.x 起才支持 Boot 4 |
| Lombok | — | 只用于编译期生成代码，不进运行期 classpath |

另外还有 Actuator、RestClient、AspectJ、Validation、devtools，都在 `pom.xml` 里，每个依赖上方有一行用途说明。

## 2. 跑起来

**第一步：确认 JDK 25**

```bash
java -version        # 不是 25 的话 mvn 会直接失败
export JAVA_HOME=/path/to/jdk-25
```

**第二步：数据库**

默认配置指向远程库（见 `application-db.yaml`）。本地开发想换库用环境变量，不用改文件：

```bash
export DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public"
export DB_USERNAME=postgres DB_PASSWORD=123456
```

建表脚本是幂等的，跑一次就行：

```bash
psql "$DB_URL" -f src/main/resources/db/schema.sql
```

**第三步：启动**

```bash
mvn spring-boot:run
```

| 入口 | 地址 |
|---|---|
| Swagger UI | http://localhost:8000/swagger-ui/index.html |
| 健康检查 | http://localhost:8000/actuator/health |

Swagger 打开默认停在「系统 API」分组。

## 3. 项目结构

```
com.zjc.demo
├── common/                     全局公共，不依赖任何业务
│   ├── constant/web/           响应码、错误码契约、traceId 常量
│   ├── constant/system/        业务枚举字典（Gender、UserStatus）
│   ├── dict/                   字典契约与注册表
│   ├── entity/                 BaseEntity
│   └── web/                    ApiResponse、PageResult
├── core/                       基础设施，与业务无关
│   ├── aop/                    WebLogAspect：接口出入参与耗时
│   ├── async/                  MDC 透传 + 异步耗时埋点
│   ├── client/                 出站请求的 traceId 透传
│   ├── config/                 各项配置
│   ├── exception/              BusinessException + 全局处理器
│   └── filter/                 TraceIdFilter
├── system/                     业务模块：系统管理
│   ├── controller/             SysUserController、DictController
│   └── dto/ entity/ mapper/ service/
└── jasypt/                     业务模块：配置项加解密
```

业务模块内部一律 `controller / service / service.impl / mapper / entity / dto`；
`common` 和 `core` 里不出现任何业务概念。

## 4. 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/sys/users` | 新增用户 |
| GET | `/api/sys/users/{id}` | 用户详情 |
| GET | `/api/sys/users` | 分页查询（keyword / status / deptId） |
| PUT | `/api/sys/users/{id}` | 修改，`password` 留空表示不改密码 |
| PUT | `/api/sys/users/{id}/status` | 改状态 |
| DELETE | `/api/sys/users/{id}` | 删除（逻辑删除） |
| GET | `/api/sys/dicts` | 列出全部字典类型 |
| GET | `/api/sys/dicts/{type}` | 查字典项，如 `user-status` |
| GET | `/api/jasypt/encrypt` | 生成密文 |
| GET | `/api/jasypt/decrypt` | 解开密文 |

新增字典不用改配置：写一个实现 `DictItem` 的枚举就会被自动收录，
类型名由类名转 kebab-case 得到（`UserStatus` → `user-status`）。

## 5. 已经替你定好的约定

**响应体的 `code` 就是 HTTP 状态码。** 不是「HTTP 200 + 业务码」那套，网关重试、APM 告警、
前端拦截器都能直接按状态码工作。响应结构固定为 `success + code + message + data + traceId + timestamp`。

**异常分两类。** 可预期的业务失败抛 `BusinessException`，全局处理器按 WARN 记一行、不打堆栈；
程序缺陷抛其他运行时异常，走 ERROR + 完整堆栈。响应体里永远不含堆栈。

**traceId 贯穿全链路。** `TraceIdFilter` 生成或沿用上游下发的 ID → 写进 MDC → 输出到日志 →
回填响应体 → 出站请求自动带上 → 异步线程由 `TaskDecorator` 透传。
排查时一条命令捞完整链路：`grep '<traceId>' logs/*/*.log`。

**实体继承 `BaseEntity`。** 主键（雪花 ID）、逻辑删除、审计字段都在基类，时间字段由
`MybatisPlusMetaObjectHandler` 自动填充，业务代码不用手写 `setCreateTime`。

**入参 DTO 不继承 `BaseEntity`。** 基类里的字段全由服务端掌管，暴露给前端就等于让客户端能改主键、
伪造创建时间、甚至传 `deleted=1` 绕开逻辑删除。DTO 只收客户端真正该填的字段。

**新增文档分组要同步排除清单。** `OpenApiConfig` 里每个业务分组都要在 `defaultApi()` 的
`packagesToExclude` 里登记，否则接口会在两个分组里各出现一次。

## 6. 配置怎么改

配置拆成几个文件，由 `application.yaml` 的 `profiles.include` 叠加：

| 文件 | 放什么 |
|---|---|
| `application.yaml` | 骨架：端口、应用名、profile 开关 |
| `config/application-pub.yaml` | 全环境通用：虚拟线程、Tomcat、Jackson 时区、Actuator 暴露清单 |
| `config/application-cors.yaml` | 跨域、Long 转 String |
| `config/application-db.yaml` | 数据源、Hikari、MyBatis-Plus |
| `config/application-jasypt.yaml` | 加密算法与前后缀 |
| `config/application-{dev,test,prod}.yaml` | 环境差异项：接口文档开关、加密密钥 |

优先级：`application-{profile}.yaml` > include 进来的 profile，所以 dev / prod 能覆盖公共值。

几个常改的：

```yaml
app.cors.allowed-origins      # 默认 * ，生产必须收敛到具体域名
app.jackson.long-to-string    # 雪花 ID 转字符串，默认开
server.port                   # 默认 8000
management.endpoints.web.exposure.include   # 默认 health,info,metrics
```

`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 三个环境变量是给部署用的，文件里的默认值只是本地开发方便。

## 7. 加一个新业务模块

以 `order` 为例，照着 `system` 抄一遍：

1. `order/entity/Order.java` —— 继承 `BaseEntity`，标 `@TableName`，业务字段自己加
2. `order/mapper/OrderMapper.java` —— `@Mapper` + `extends BaseMapper<Order>`
3. `resources/mapper/order/OrderMapper.xml` —— 只在需要自定义 SQL 时建，`namespace` 写接口全限定名
4. `order/service/OrderService.java` + `impl/` —— `extends IService` / `ServiceImpl<Mapper, Entity>`
5. `order/controller/OrderController.java` —— 路径完整写在方法上，不在类上加 `@RequestMapping`
6. `OpenApiConfig` 加一个 `GroupedOpenApi` Bean，**并把包名加进 `defaultApi()` 的 `packagesToExclude`**
7. 建表语句加进 `db/schema.sql`

`@MapperScan("com.zjc.demo.**.mapper")` 和 `type-aliases-package: com.zjc.demo.**.entity` 都是通配的，
新模块不用改这两处配置。

## 8. 踩过的坑

**1. `mvn clean` 和 devtools 会打架。** clean 清空 `target` 后，devtools 可能在「XML 已拷贝、
class 还没编译完」的空档重启，报 `Could not resolve type alias 'Xxx'` 或 `Failed to parse mapping resource`。
这是假象，别去改代码 —— 重新 `mvn compile` 或重启一次就正常。

**2. `mapper-locations` 必须用双星。** 只有 `classpath*:/mapper/**/*.xml` 能扫到子目录；
写成单星 `/mapper/*.xml` 时子目录里的 XML **静默不加载**，表现为「自定义查询找不到 statement」。

**3. XML 注释里不能出现连续两个短横。** XML 规范禁止，会直接解析失败。
`logback-spring.xml` 里踩过一次，改完 XML 一定要验证：

```bash
python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('文件路径')"
```

**4. `@Version` 为 `null` 时乐观锁整段跳过。** 建表必须是 `NOT NULL DEFAULT 0`，否则这张表看起来
有乐观锁、实际零保护，而且不报错。当前 `sys_user` 没有开乐观锁。

**5. `strictUpdateFill` 遇到非空值会跳过。** 更新场景下实体是从库里查出来的、`updateTime` 必然非空，
用它就永远填不上。`MybatisPlusMetaObjectHandler.updateFill` 因此改成了直接赋值。

**6. `{@link}` 不能指向 Lombok 生成的方法。** getter 是编译期生成的，javadoc 工具看不到，
`-Xdoclint` 会报「找不到引用」。校验命令：

```bash
javadoc -Xdoclint:all,-missing -d /tmp/doc -cp "$(cat target/cp.txt)" $(find src/main/java -name "*.java")
```

## 9. 容器化

```bash
mvn clean package -DskipTests
docker build -t spring-vortex-demo .
docker run -p 8000:8000 -e DB_URL=... spring-vortex-demo
```

Dockerfile 是三阶段：Maven 构建 → jarmode 分层 → 运行时。日常改代码只影响 `application` 层，
镜像推送只剩一小层。

两个细节：

- **`extract --layers` 必须带 `--launcher`。** 不加的话 `spring-boot-loader` 层是空的，容器起不来。
  Boot 4 用的是 `-Djarmode=tools`，Boot 3 时代的 `layertools` 已废弃。
- **容器时区默认 UTC**，而 `LocalDateTime.now()` 按 JVM 时区落库，所以 ENTRYPOINT 里带了
  `-Duser.timezone=Asia/Shanghai`。删掉的话时间会差 8 小时。

## 10. 自检清单

```bash
# 编译 + 打包
mvn clean package -DskipTests

# javadoc 规范（0 错 0 警告）
javadoc -Xdoclint:all,-missing -d /tmp/doc -cp "$(cat target/cp.txt)" $(find src/main/java -name "*.java")

# 起服务后
curl localhost:8000/actuator/health          # status 应为 UP
curl localhost:8000/api/sys/dicts            # ["gender","user-status"]
curl localhost:8000/api/sys/users?current=1&size=10
```

> 本机若配了 HTTP 代理，curl 打 localhost 会走代理导致连接失败，先
> `export no_proxy=localhost,127.0.0.1`。
