# 单表 CRUD 一行 SQL 都不用写 —— Spring Boot 4 接 PostgreSQL + MyBatis-Plus 实战笔记

写后台最开始那几天，我干得最多的事就是：建表、写 `insert`、写 `select`、写分页的 `limit`、再写一条一模一样的 `count(*)`。写到第三张表的时候我意识到——这些活儿里没有任何一句是需要动脑子的。

这篇记的是怎么把这部分体力活交给工具：**数据库用 PostgreSQL，持久层用 MyBatis-Plus，单表增删改查一个字都不用写**。Spring Boot 4.1.1 + JDK 25，代码在本仓库，照着跑就能复现。

```bash
git clone -b springboot4/postgresql git@github.com:springvortex/lab.git
```

---

## 目录

1. [这分支是怎么来的](#1-这分支是怎么来的)
2. [先说清楚这俩各自图什么](#2-先说清楚这俩各自图什么)
3. [为什么是这两个搭一起](#3-为什么是这两个搭一起)
4. [先跑起来看看效果](#4-先跑起来看看效果)
5. [怎么用：从建表到接口](#5-怎么用从建表到接口)
6. [配置怎么改，能改哪些值](#6-配置怎么改能改哪些值)
7. [我踩过的坑](#7-我踩过的坑)
8. [怎么自检](#8-怎么自检)

---

## 1. 这分支是怎么来的

先交代背景。本分支 `springboot4/postgresql` 是从主脚手架分支 **`template`** 拉出来的：

```bash
git switch -c springboot4/postgresql template
```

`template` 是 Boot 4.1.1 + JDK 25 的脚手架，统一响应、traceId、异步、跨域、Jackson、Actuator、接口文档这些它都有了，但**不带任何持久层**。我拉这个分支只干一件事：把 PostgreSQL + MyBatis-Plus 接上去，顺带给一套能直接抄的分层范例。

所以本分支新增的东西：

```
pom.xml                                              # 改：加 3 个依赖
lombok.config                                        # 新增
config/application-db.yaml                           # 新增：数据源 + 连接池 + MyBatis-Plus
application.yaml                                     # 改：include 加 db
config/MybatisPlusConfig.java                        # 新增：Mapper 扫描 + 分页/乐观锁拦截器
config/MybatisPlusMetaObjectHandler.java             # 新增：时间字段自动填充
entity/DemoUser.java                                 # 新增：实体，注解全在这儿
mapper/DemoUserMapper.java + .xml                    # 新增：继承 BaseMapper + 自定义 SQL
service/DemoUserService.java + impl/                 # 新增
controller/DemoUserController.java                   # 新增：7 个接口
dto/DemoUserSaveRequest.java、DemoUserAgeGroup.java  # 新增：入参 VO 与统计 VO
web/PageResult.java                                  # 新增：分页响应结构
db/schema.sql                                        # 新增：建表脚本，幂等
```

脚手架其他部分（统一响应、traceId、异步、跨域、Jackson、Actuator、接口文档）没动，
要看去 **`template`** 分支的 README。

---

## 2. 先说清楚这俩各自图什么

### PostgreSQL 有什么好的

开源关系型数据库，这几年国内存在感越来越高。跟 MySQL 的差异不是"哪个更快"，而是**能表达的东西更多**：

- **类型真丰富**。`jsonb`（可建索引的 JSON）、数组、范围类型（`daterange`、`int4range`）、`uuid`、`citext`。那些"要存一点结构化扩展信息"的需求，不用再搞 EAV 表或者硬塞字符串。
- **索引花样多**。B-tree 之外有 GIN（数组、jsonb、全文检索）、GiST（地理位置、范围）、BRIN（时序大表）。
- **`ILIKE` 大小写不敏感匹配**是自带的。MySQL 靠排序规则碰巧不区分大小写，PG 得显式写 `ILIKE`——看着麻烦，但语义明确，换个排序规则也不会悄悄变。
- **部分索引（partial index）**。本文就用上了：唯一索引可以只约束"未删除的行"，`CREATE UNIQUE INDEX ... WHERE deleted = 0`。逻辑删除场景下它解决了一个很烦人的问题，第 5 节细说。
- **`timestamptz` 真带时区**。跨时区系统用它省心很多。
- **`currentSchema` 能写在 JDBC URL 上**，多业务共享一个库时用 schema 隔离，比一个库开到底干净。
- 协议友好（PostgreSQL License，类似 BSD），没有 MySQL 那种被收购后的授权焦虑。

也别神化：它有 `VACUUM` 要操心，连接是进程模型所以连接数不能开太大（这也是为什么必须用连接池）。但对绝大多数后台业务来说，它很稳。

### MyBatis-Plus 又是什么

先说清楚和 MyBatis 的关系：MyBatis-Plus（后面简称 MP）是 MyBatis 的**增强工具**，不是替代品。它在 MyBatis 外面包了一层，**没改 MyBatis 任何东西**。

MyBatis 有个众所周知的特点：SQL 你全权掌控，灵活，但单表的 `insert` / `select` / `update` 也得自己写。MP 补的就是这块：

- **单表 CRUD 零代码**。Mapper 继承 `BaseMapper<T>`，里面什么都不用写，`insert` / `selectById` / `updateById` / `deleteById` / `selectList` / `selectPage` 全有了。MP 启动时按你实体上的注解把 SQL 生成好。
- **条件构造器**：

    ```java
    Wrappers.<DemoUser>lambdaQuery()
        .like(StringUtils.hasText(username), DemoUser::getUsername, username)
        .orderByDesc(DemoUser::getId);
    ```

    用 Lambda 引用字段（`DemoUser::getUsername`），字段名写错**编译期就报错**，不用等运行时才发现列名拼错。而且 `.like(条件开关, 字段, 值)` 第一个参数为 `false` 时整段条件不拼进 SQL，省掉一堆手写 `if`。
- **分页插件**。传个 `Page` 进去，自动先跑 `COUNT` 再跑 `LIMIT/OFFSET`，`total`、`pages` 都给你算好。
- **逻辑删除**。字段标 `@TableLogic`，`removeById` 变成 `UPDATE ... SET deleted = 1`，查询自动加 `AND deleted = 0`。
- **乐观锁**。字段标 `@Version`，`UPDATE` 自动追加 `AND version = ?` 并 `version + 1`。
- **自动填充**。`@TableField(fill = FieldFill.INSERT)`，插入时自动填 `create_time`。
- **代码生成器**。本文没用（我们手写演示代码），真实项目里它能一口气生成 Entity / Mapper / Service / Controller。

一句话：**MyBatis 管"复杂的 SQL 我来写"，MyBatis-Plus 管"简单的 SQL 你别写了"**。两者不冲突，复杂查询照样写 XML。

---

## 3. 为什么是这两个搭一起

- **MP 对 PostgreSQL 是一等公民**：分页插件里写 `DbType.POSTGRE_SQL` 就走正确方言，不用自己拼 `LIMIT/OFFSET`。
- **雪花 ID 和 bigint 对得上**：MP 默认生成 19 位 bigint 主键，PG 的 `bigint` 正好装下，而且**插入前就知道主键**，不用等数据库回写。
- **MP 的逻辑删除 + PG 的部分唯一索引**，能干净解决"删了的用户名无法重新注册"这个经典问题。
- 真要用数据库特有语法（`ILIKE`、`jsonb` 操作符），直接写 XML，MP 不拦你。

---

## 4. 先跑起来看看效果

> ⚠️ **本分支已移除演示业务**（`HelloController` / `DemoUserController` 及其 Service、Entity、
> Mapper、DTO），建表脚本 `db/schema.sql` 也一并删除了。保留下来的只有 **jasypt 配置项加密**
> 与脚手架的基础设施（统一响应、traceId、跨域、Jackson、Actuator、接口文档）。
> 直接跳到「第 2 步：起服务」。

### 第 1 步：起服务

```bash
export JAVA_HOME=D:/app/Java/jdk-25.0.2    # 终端默认 JDK 可能是 8，不切会编译失败
mvn -o clean verify
java -jar target/spring-vortex-demo-0.0.1.jar --server.port=8000
```

### 第 2 步：调接口

```bash
# 加密一段明文（拿到密文后补上 ENC(...) 就能写进配置文件）
curl --noproxy '*' "localhost:8000/api/jasypt/encrypt?plainText=my-db-password"

# 解密（密文含 + / 时要让 curl 替你编码，见下方说明）
curl --noproxy '*' -G --data-urlencode "cipherText=<上一步返回的密文>" \
     localhost:8000/api/jasypt/decrypt

# 健康检查
curl --noproxy '*' localhost:8000/actuator/health
```

接口文档在 `http://localhost:8000/swagger-ui.html`（非生产环境自动开启）。

> `curl` 走代理会返回奇怪的东西，**记得加 `--noproxy '*'`**。
>
> ⚠️ 密文默认是 Base64，可能含 `+`；在 query string 里 `+` 表示空格，手工拼接会导致解密失败。
> 用 `-G --data-urlencode` 让 curl 处理。

---

## 5. 怎么用：从建表到接口

整体四层，从上往下看一遍：

```
DemoUserSaveRequest (dto)  只收客户端该填的字段，不用实体接参
        ↓ BeanUtils.copyProperties(request, user)
DemoUser (entity)          @TableName / @TableId(ASSIGN_ID) / @Version / @TableLogic + 填充注解
        ↓
DemoUserMapper             extends BaseMapper<DemoUser>，接口里什么都不用写
        ↓
DemoUserService(+Impl)     extends ServiceImpl<Mapper, Entity>，实现类通常也是空的
        ↓
DemoUserController         分页用 PageResult.of(IPage) 包装，不直接序列化 IPage
```

### 5.1 建表：几个反直觉的地方

```sql
CREATE SCHEMA IF NOT EXISTS demo;

CREATE TABLE IF NOT EXISTS demo.demo_user
(
    id          bigint       PRIMARY KEY,                -- 不是 bigserial！
    username    varchar(64)  NOT NULL,
    email       varchar(128),
    age         integer,
    version     integer      NOT NULL DEFAULT 0,          -- 乐观锁，必须 NOT NULL DEFAULT 0
    deleted     integer      NOT NULL DEFAULT 0,          -- 逻辑删除
    create_time timestamp,
    update_time timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_demo_user_username
    ON demo.demo_user (username)
    WHERE deleted = 0;                                    -- 部分唯一索引
```

三个"为什么"：

1. **主键为什么不用 `bigserial`？** 雪花 ID 是 MP 在 Java 侧生成的，插入前主键就已知，数据库不需要序列。只有把 `id-type` 改成 `auto` 时才要换成 `bigserial`。
2. **`version` 为什么必须 `NOT NULL DEFAULT 0`？** 值为 `NULL` 时 MP 的乐观锁拦截器**静默跳过**，那一行就没有并发保护了，而且不报错。
3. **唯一索引为什么带 `WHERE deleted = 0`？** 用普通唯一约束的话，删掉一个用户后他的用户名被永久占用、再也无法注册。部分索引只约束"还活着的行"，删了之后用户名就释放了。

另外两个说明：

- **为什么建在独立 schema 而不是 `public`**：`public` 是所有角色默认都有 `CREATE` 权限的地方，业务表堆在那儿既难管理，也让"这张表属于哪个应用"变得不可考。
- **没有用 `spring.sql.init` 让应用启动时自动建表**：那会让"应用启动"依赖"数据库可写"，库不可用时连上下文都起不来，所有测试一起失败。建表交给初始化脚本更稳妥。

> 脚本里的对象名都显式写了 `demo.` 前缀，所以无论 `currentSchema` 是否生效，表都一定建在 `demo` 下。

### 5.2 实体：注解都在这儿

```java
@Data
@TableName("demo_user")
public class DemoUser implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)      // 雪花 ID，MP 本地生成
    private Long id;

    private String username;
    private String email;
    private Integer age;

    @Version                                // 乐观锁
    private Integer version;

    @TableLogic                             // 逻辑删除
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)          // 插入时自动填
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)   // 插入和更新都填
    private LocalDateTime updateTime;
}
```

| 注解 | 效果 |
|---|---|
| `@TableName` | 类名和表名不一致时必写，否则 MP 按类名转下划线去猜 |
| `@TableId(type = ASSIGN_ID)` | 雪花 ID，19 位 bigint，**不与数据库交互** |
| `@Version` | `UPDATE` 自动追加 `AND version = ?` 并 `version + 1` |
| `@TableLogic` | `removeById` 变成 `UPDATE ... SET deleted = 1`；查询自动加 `AND deleted = 0` |

⚠️ 雪花 ID 19 位，超过 JS 的 `Number` 安全整数上限。脚手架开了 Jackson 的 `Long → String` 全局转换（`JacksonConfig`），所以响应里 `id` 是字符串，前端不用处理。**你要是关了那个开关就会中招**。

⚠️ 时间字段用 `LocalDateTime`（不带时区），对应 `timestamp` 列。列类型是 `timestamptz` 的话得换成 `OffsetDateTime` / `Instant`。

⚠️ `@TableField(fill = ...)` **必须和 `MybatisPlusMetaObjectHandler` 成对出现**：字段标了但处理器没赋值 → 写库是 `null`；处理器赋值了但字段没标 → 不生效。这是 MP 自动填充最常见的坑。

### 5.3 Mapper：继承就完事了

```java
public interface DemoUserMapper extends BaseMapper<DemoUser> {
    // 什么都不用写，单表 CRUD 全有了

    IPage<DemoUser> selectByCondition(IPage<DemoUser> page,
                                      @Param("keyword") String keyword,
                                      @Param("minAge") Integer minAge);

    List<DemoUserAgeGroup> selectAgeGroupSummary();
}
```

继承 `BaseMapper<DemoUser>` 之后一个字都不用写。下面两个是**自定义查询**，SQL 写在 `resources/mapper/DemoUserMapper.xml`。

### 5.4 Service：继承 `ServiceImpl`

```java
@Service
public class DemoUserServiceImpl extends ServiceImpl<DemoUserMapper, DemoUser>
        implements DemoUserService {

    @Override
    public IPage<DemoUser> searchByCondition(IPage<DemoUser> page, String keyword, Integer minAge) {
        return baseMapper.selectByCondition(page, keyword, minAge);   // 直接用父类的 baseMapper
    }
}
```

⚠️ **`IService` / `ServiceImpl` 在 3.5.17 换了包**：从 `com.baomidou.mybatisplus.extension.service.*`
迁到了 `com.baomidou.mybatisplus.spring.service.*`。照着老博客写会直接编译不过。

泛型顺序是 `<Mapper, Entity>`，容易出错的是**继承了 `ServiceImpl` 却忘了 `implements XxxService`**，那样注入接口时找不到实现类。

### 5.5 分页查询：两种方式

**方式 A：条件构造器**

```java
@GetMapping("/api/users")
public ApiResponse<PageResult<DemoUser>> page(
        @RequestParam(defaultValue = "1") @Min(1) long current,
        @RequestParam(defaultValue = "10") @Min(1) long size,
        @RequestParam(required = false) String username) {

    LambdaQueryWrapper<DemoUser> wrapper = Wrappers.<DemoUser>lambdaQuery()
            .like(StringUtils.hasText(username), DemoUser::getUsername, username)
            .orderByDesc(DemoUser::getId);

    IPage<DemoUser> page = demoUserService.page(new Page<>(current, size), wrapper);
    return ApiResponse.success(PageResult.of(page));
}
```

**方式 B：自定义 SQL**

条件特别多且带分支、要 join、要用数据库专属函数（比如 `ILIKE`），或者 Wrapper 拼出来的 SQL 性能不行时，写 XML：

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
                  OR email    ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="minAge != null">
                AND age &gt;= #{minAge}
            </if>
        </where>
        ORDER BY id DESC
    </select>
</mapper>
```

手写 SQL 的四条规矩：

| # | 规矩 | 不遵守会怎样 |
|---|---|---|
| 1 | **逻辑删除条件要自己写** `AND deleted = 0` | MP 的自动追加只对 `BaseMapper` 内置方法生效，手写 SQL 会把已删数据查出来 |
| 2 | **分页靠第一个参数是 `IPage`**，XML 里别写 `limit` | 自己写会与插件改写打架，分页结果错乱 |
| 3 | **多参数必须 `@Param`** | XML 只能写 `#{param1}` / `#{arg0}`，字段改名就全乱 |
| 4 | **`resultType` 写短名要登记别名包** | `type-aliases-package` 没覆盖到会启动期报 `Cannot find class: Xxx` |

另外：XML 里 `<`、`>=` 要写成 `&lt;`、`&gt;=`，否则 XML 解析直接失败。

返回的 VO 放 `dto` 包，列名 `age_group` / `user_count` 会按驼峰规则自动映射到 `ageGroup` / `userCount`。注意计数是 `Long`，会被全局的 `Long → String` 一并转成字符串（`"userCount": "3"`），想保持数字就把字段类型改成 `Integer`。

### 5.6 为什么分页结果不直接返回 `IPage`

`IPage` 的实现类 `Page` 上带着 `optimizeCountSql`、`searchCount`、`countId`、`maxLimit` 这些纯内部字段，直接序列化会把它们全吐给前端——既冗余，又等于把分页实现细节固化成了对外契约，以后换分页组件就变成破坏性变更。

所以脚手架加了个 `PageResult` 转一道：

```java
public static <T> PageResult<T> of(IPage<T> page) {   // records / total / current / size / pages
```

`records` 为空时返回空列表而不是 `null`，前端不用写 `data.records && data.records.length` 这种防御判断。

### 5.7 时间字段不用手 set

```java
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

用的是 `strictXxxFill`，**不会覆盖已有值**：字段非空时直接跳过，所以"回补历史数据、导入时手动指定时间"不会被抹掉。

⚠️ `strictXxxFill` 对"实体里根本没有这个字段"的情况**静默跳过、不抛异常**。给新表加字段忘了改实体，不会炸，但也**没有任何提示**。

⚠️ 它依赖 MP 的 `TableInfo` 缓存，**脱离 Spring 容器直接 new 出来调会抛 `NullPointerException`**——它的测试必须起容器。

### 5.8 乐观锁、逻辑删除、重复数据

**乐观锁**（`PUT /api/users/{id}`）：

```java
DemoUser existing = requireUser(id);
BeanUtils.copyProperties(request, existing);   // 拷到已落库的实体上，version 保持不变

if (!demoUserService.updateById(existing)) {   // 影响行数 0 = 版本冲突
    throw new BusinessException(ApiResponseConstant.CONFLICT);   // 409
}
```

关键在**拷到"从库里查出来的那个实体"上**，而不是新建一个。这样 `id` / `version` / `deleted` / 时间字段都保持原值，乐观锁和逻辑删除不会被绕过。

**逻辑删除**（`DELETE /api/users/{id}`）：就一句 `removeById(id)`，实际执行 `UPDATE demo_user SET deleted = 1 WHERE id = ? AND deleted = 0`。

**重复数据怎么给明确提示**：用户名撞唯一索引时，MyBatis-Spring 会把 SQLState `23505` 翻译成 `DuplicateKeyException`。它既不是 `BusinessException` 也不是 Spring 的 4xx 异常，**不处理就会掉进全局兜底被报成 500「服务内部错误」**——排查方向被带偏，前端也只会弹一句没用的提示。

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

两点：

- **别在插入前先查一次 `exists()` 来防重复**：查完到插入之间有时间窗，并发下照样撞索引。唯一索引是唯一可靠的防线，这里做的是"冲突发生后给出可读原因"。
- 原始驱动报错（含约束名 `uk_demo_user_username`）只进日志，不外泄。

### 5.9 入参校验

```java
@Data
public class DemoUserSaveRequest implements Serializable {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过 64 个字符")
    private String username;

    @Email(message = "邮箱格式不正确")     // @Email 对 null 放行，"选填"语义天然成立
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Min(0) @Max(150)
    private Integer age;                  // 用 Integer 而不是 int，才能区分"没传"和"传了 0"
}
```

⚠️ Controller 参数上**必须写 `@Valid`**，漏了不会报错，只是**静默不校验**。

⚠️ `BeanUtils.copyProperties` 用的是 Spring 的 `org.springframework.beans.BeanUtils`（参数顺序「源, 目标」）。
**别用 `org.apache.commons.beanutils.BeanUtils`**——它参数顺序正好相反，抄过来会**静默拷反方向**。
另外它按属性名隐式拷贝，**没有编译期保护**，字段名改了只会在运行时静默拷不过去。

---

## 6. 配置怎么改，能改哪些值

配置集中在 `src/main/resources/config/application-db.yaml`，靠 `application.yaml` 的 `include` 挂进来。拆出来单独放是因为这块内容最长、改动最频繁，塞进 `application.yaml` 会把「端口 / 应用名 / profile 激活」这些骨架配置淹掉。

```yaml
# application.yaml
spring:
  profiles:
    active: dev
    include:
      - pub
      - cors
      - db          # ← 数据库配置在这里
```

| 文件 | 放什么 |
|---|---|
| `config/application-db.yaml` | **全部**数据库配置：数据源 URL / 账号 / 密码、HikariCP、MyBatis-Plus |
| `application.yaml` | 只加一行 `include: - db` |
| `application-{dev,test,prod}.yaml` | 不放数据库配置；换库走环境变量 |

### 6.1 数据源：三项都是环境变量优先

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/postgres?currentSchema=demo&ApplicationName=SpringVortexDemo&reWriteBatchedInserts=true}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:123456}
    driver-class-name: org.postgresql.Driver
```

`${DB_URL:默认值}` 的意思是：**环境变量存在就用它，不存在就用冒号后面的默认值**。所以换库不用改配置文件：

```bash
DB_URL="jdbc:postgresql://db.internal:5432/app" DB_USERNAME=app DB_PASSWORD=xxx mvn clean verify
```

URL 上三个 PG 专属参数：

| 参数 | 作用 |
|---|---|
| `currentSchema=demo` | 把 `search_path` 钉在 `demo` 模式上。不写会走默认的 `"$user", public`，业务表散落进 `public` |
| `ApplicationName` | 出现在 `pg_stat_activity.application_name`，线上排障时一眼认出是哪个服务 |
| `reWriteBatchedInserts` | 把批量 insert 改写成多值 INSERT，批插快好几倍。`saveBatch` 不配它等于没提速 |

> ⚠️ **部署生产务必显式设置这三个环境变量。** 文件里的默认值指向本机库；
> 生产忘了配，应用会「正常启动、正常写入」，只是数据全去了错误的地方——
> **「连错库」比「连不上」危险得多**，往往几周后才被发现。
> 想要「缺环境变量就启动失败」的硬约束，把默认值去掉即可：写 `${DB_URL}` 而不是 `${DB_URL:jdbc:postgresql://...}`。

### 6.2 连接池（HikariCP）

```yaml
    hikari:
      pool-name: VortexHikari
      connection-timeout: 30000
      validation-timeout: 5000
      idle-timeout: 600000
      max-lifetime: 1800000
      keepalive-time: 120000
      minimum-idle: 5
      maximum-pool-size: 20
```

| 配置项 | 当前值 | 含义 | 怎么调 |
|---|---|---|---|
| `pool-name` | `VortexHikari` | 连接池名字，日志和 JMX 里显示 | 随便起 |
| `connection-timeout` | `30000` | 从池里拿连接最多等多久（毫秒） | 别设太长 |
| `validation-timeout` | `5000` | 校验连接是否可用的超时 | **必须小于 `connection-timeout`** |
| `idle-timeout` | `600000` | 空闲连接多久被回收（10 分钟） | 必须小于 `max-lifetime` |
| `max-lifetime` | `1800000` | 连接最长存活（30 分钟） | 要比数据库侧超时短 |
| `keepalive-time` | `120000` | 空闲连接多久探活一次（2 分钟） | 别超过 `idle-timeout` |
| `minimum-idle` | `5` | 最少保留几个空闲连接 | 流量平稳时别设太大 |
| `maximum-pool-size` | `20` | 池里最多几个连接 | 见下面 |

⚠️ **这些是毫秒数字，不是 `30s` 这种 Duration 字符串**。写成 `30s` 会在启动期报
`NumberFormatException: For input string: "30s"`。同项目的 `spring.http.clients.*` 才是 Duration，两套单位别混。

⚠️ **`maximum-pool-size` 不是越大越好**。PostgreSQL 是进程模型，每连接一个进程，开太多会把库拖垮。经验值大致 `CPU核数 × 2 + 磁盘数`，20 对多数中小应用够用。

### 6.3 MyBatis-Plus：能改哪些

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.zjc.demo.entity,com.zjc.demo.dto
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    banner: false
    db-config:
      id-type: assign_id
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

| 配置项 | 当前值 | 能改成什么 |
|---|---|---|
| `mapper-locations` | `classpath*:/mapper/**/*.xml` | XML 放哪儿。**改了目录不报错**，只是扫描不到 → 方法绑定失败 |
| `type-aliases-package` | 实体 + DTO 两个包 | 配了之后 XML 的 `resultType` 能写短名 |
| `map-underscore-to-camel-case` | `true` | 下划线列名 ↔ 驼峰字段名自动映射，**别关** |
| `log-impl` | `Slf4jImpl` | 开发期改 `StdOutImpl` 可在控制台直接看 SQL |
| `banner` | `false` | 关掉启动时的 MP 大 logo |
| `id-type` | `assign_id` | 见下表 |
| `logic-delete-field` | `deleted` | 你表里逻辑删除字段叫什么 |
| `logic-delete-value` / `logic-not-delete-value` | `1` / `0` | 已删除 / 未删除的值 |

`id-type` 的取值：

| 取值 | 含义 | 什么时候用 |
|---|---|---|
| `assign_id` | 雪花 ID，MP 本地生成 19 位 bigint（**默认，推荐**） | 绝大多数场景 |
| `assign_uuid` | UUID 字符串 | 主键不想是数字，或要跨库合并数据 |
| `auto` | 数据库自增 | 换成它的话，建表得改成 `bigserial` / `identity` |
| `input` | 自己手动 set 主键 | 有外部 ID 体系 |
| `none` | 不生成，不填就是 `null` | 基本不用 |

### 6.4 分页插件：拦截器顺序有讲究

```java
@Configuration
@MapperScan("com.zjc.demo.mapper")
public class MybatisPlusConfig {

    public static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());   // 先：乐观锁
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);                               // 后：分页
        return interceptor;
    }
}
```

- **分页插件必须放最后**。官方明确要求，否则 `COUNT` SQL 可能统计不准。
- **`DbType.POSTGRE_SQL` 要写死**。不指定 MP 会靠 JDBC 元数据猜数据库类型，连接池还没建连时可能猜错。
- `MAX_PAGE_SIZE = 500` 是防呆阀：前端传 `size=100000` 会被**静默截断**成 500。需要严格拒答请在 Controller 层自己校验返回 400。

### 6.5 依赖：三个，少一个都不行

| 坐标 | 作用 | 少了 / 写错会怎样 |
|---|---|---|
| `mybatis-plus-spring-boot4-starter` | Boot 4 专用 starter，传递引入 jdbc + mybatis-spring 4.x | 写成 `-spring-boot3-starter` 会把 Boot 3 的自动配置拉进来，与 Boot 4 打架 |
| `mybatis-plus-jsqlparser` | 分页插件 `PaginationInnerInterceptor` 的载体 | **自 3.5.9 起分页插件被拆出 starter**。不引这个类直接不存在（编译期报错）；勉强绕过则分页「看起来能跑但查的是全表」 |
| `org.postgresql:postgresql` | JDBC 驱动（runtime 即可） | 启动期不报错，第一条 SQL 才 `ClassNotFoundException` |

> `mybatis-plus-jsqlparser` 有三个坐标，对应不同 jsqlparser 版本：
> `mybatis-plus-jsqlparser`（jsqlparser 5.2，默认选它）、`-5.0`、`-4.9`（项目里已有 jsqlparser 4.9 时用，避免冲突）。

**别再引原生 `mybatis-spring-boot-starter`**：MP 的 starter 已经传递带进来了，两套自动配置会打架。

---

## 7. 我踩过的坑

按讨厌程度排序。共同点是：**错了不报错，就是悄悄不对**。

1. **`@Version` 为 `null` 时乐观锁整段跳过，且不报错。** 所以建表必须 `NOT NULL DEFAULT 0`。
   更阴的是：**`insert` 不会把数据库的 `DEFAULT 0` 回写进实体**——所以「插入后拿同一个对象直接更新」是**没有并发保护**的，生成的 SQL 里根本没有 `AND version = ?`。正确做法是先 `selectById` 再改。
2. **手写 SQL 漏了 `AND deleted = 0`。** 逻辑删除的自动追加只对 `BaseMapper` 内置方法生效，手写 XML 它管不着。
3. **XML 里自己写了 `limit`。** 分页插件靠"第一个参数是 `IPage`"识别，自己再写会打架，表现是分页结果莫名其妙。
4. **忘了引 `mybatis-plus-jsqlparser`。** 少它时分页**看起来能跑**——不报错、有数据，只是每次把全表捞出来再内存截断。表小的时候完全没感觉。
5. **`IService` / `ServiceImpl` 在 3.5.17 换了包。** 从 `com.baomidou.mybatisplus.extension.service.*` 迁到 `com.baomidou.mybatisplus.spring.service.*`。照老博客写直接编译不过。
6. **Hikari 超时写成了 `30s`。** 那是毫秒数字，写 `30s` 启动直接失败——这个还算好的，至少会报错。
7. **`BeanUtils.copyProperties` 静默拷不过去。** 按属性名隐式拷贝，没有编译期保护。发现"传上来的值没落库"，先核对两边字段名。
8. **自动填充没配对。** 字段标了注解但处理器没赋值 → 写库 `null`；处理器赋值了但字段没标 → 不生效。
9. **MyBatis 一级缓存会让同一事务内两次 `selectById` 返回同一个对象实例。** 想造"两份数据"模拟并发冲突是造不出来的（改了第一份等于改了第二份）。测试里改用 `JdbcTemplate` 直接在库里把 `version` 加 1，模拟另一个事务抢先提交。
10. **默认用户名跟安装方式有关，别照抄。** 官方安装包（Windows / Linux）建的是 `postgres` 用户；
    但 macOS 上用 Homebrew 装的，默认用户是**当前的 macOS 用户名**（不是 `postgres`），
    两边照抄对方的都会报 `role "xxx" does not exist`。不确定就先用 `psql -U postgres -l` 试一下。

---

## 8. 怎么自检

> ⚠️ **本分支不包含测试**。`release/v1.0.0` 是**发布产物分支**，`src/test` 整个目录、
> 测试依赖（`spring-boot-starter-webmvc-test`）与 JaCoCo（覆盖率报告 + 0.80 门禁）
> 都已被移除 —— 打出来的包更小，`mvn package` 也不用再等测试跑完。
> 需要完整测试与覆盖率门禁请切到 `springboot4/postgresql` 或 `template`。

所以这里的「自检」是**手工冒烟**：起服务，按下面的顺序把关键路径走一遍。

**第 0 步：建表**（只需一次，脚本幂等）

```bash
psql -h localhost -U postgres -d postgres -f src/main/resources/db/schema.sql
```

**第 1 步：起服务**

```bash
export JAVA_HOME=D:/app/Java/jdk-25.0.2
mvn -o clean package
java -jar target/spring-vortex-demo-0.0.1.jar
```

**第 2 步：按顺序验这几条**

```bash
# ① 健康检查：应该返回 status=UP，且能看到 db 组件
curl --noproxy '*' http://localhost:8000/actuator/health

# ② 接口文档能打开
curl --noproxy '*' -o /dev/null -w "%{http_code}\n" http://localhost:8000/v3/api-docs

# ③ 写一条，再读回来（验证 PostgreSQL 真的连上了、表结构对得上）
curl --noproxy '*' -X POST http://localhost:8000/api/users \
     -H 'Content-Type: application/json' \
     -d '{"username":"smoke-test","age":30,"email":"smoke@test.com"}'
curl --noproxy '*' 'http://localhost:8000/api/users?current=1&size=10'

# ④ 分页与逻辑删除（是这几个里最容易在别的库上翻车的）
curl --noproxy '*' 'http://localhost:8000/api/users/age-groups'
```

①③ 是必看项：① 不通说明服务没起来，③ 不通基本就是库或表的问题。

### 起不来怎么排查

两种报错都很典型，看信息就能定位：

**① 连不上 / 认证失败**

```
org.postgresql.util.PSQLException: FATAL: password authentication failed for user "postgres"
```

配置里的默认账号密码是本机开发用的（`postgres` / `123456`），**跟你机器上的不一定一致**。
改它不用动配置文件，用环境变量覆盖即可：

```bash
DB_PASSWORD=你的真实密码 mvn -o clean verify
DB_URL="jdbc:postgresql://db.internal:5432/app" DB_USERNAME=app DB_PASSWORD=xxx mvn -o clean verify
```

**② 表不存在**

```
org.postgresql.util.PSQLException: ERROR: relation "demo_user" does not exist
```

说明库连上了但表没建，回头执行一遍 `schema.sql` 即可。脚本幂等，重复执行没问题。

### 改了配置没生效？按这个顺序查

1. `application.yaml` 的 `include` 里有 `db` 吗？漏了整个 profile 不加载，而且**不报错**。
2. 改的是 `config/application-db.yaml` 里的 key 吗？写错 key 不报错，只是静默失效。
3. 环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 有没有设？它们会覆盖文件里的默认值。
4. `mapper-locations` 和你 XML 的实际路径对得上吗？

### 几条常用的 SQL

```sql
-- 看连接池里现在有哪些连接（配合 URL 上的 ApplicationName 很好用）
SELECT application_name, state, count(*) FROM pg_stat_activity GROUP BY 1, 2;

-- 确认逻辑删除确实没删数据
SELECT count(*) FROM demo.demo_user WHERE deleted = 1;
```

---

## 附录：这分支到底改了什么

| 文件 | 改了啥 |
|---|---|
| `pom.xml` | 加 3 个依赖：MP starter（注意是 boot4 版）、jsqlparser、PostgreSQL 驱动 |
| `lombok.config` | 新增 |
| `config/application-db.yaml` | 新增。数据源 + Hikari 连接池 + MyBatis-Plus 全局配置 |
| `application.yaml` | 改。`include` 加 `db` |
| `config/MybatisPlusConfig.java` | 新增。`@MapperScan` + 分页/乐观锁拦截器 |
| `config/MybatisPlusMetaObjectHandler.java` | 新增。时间字段自动填充 |
| `entity/DemoUser.java` | 新增。`@TableId` / `@Version` / `@TableLogic` / `@TableField(fill)` |
| `mapper/DemoUserMapper.java` + `.xml` | 新增。继承 `BaseMapper` + 两个自定义查询 |
| `service/DemoUserService.java` + `impl/` | 新增。继承 `ServiceImpl` |
| `controller/DemoUserController.java` | 新增。7 个接口（增删改查、两种分页、统计） |
| `dto/DemoUserSaveRequest.java`、`DemoUserAgeGroup.java` | 新增。入参 VO 与统计 VO |
| `web/PageResult.java` | 新增。分页响应结构，替代直接序列化 `IPage` |
| `db/schema.sql` | 新增。幂等建表脚本 |

> 本分支在 `springboot4/postgresql` 基础上**移除了测试体系**：删掉 `src/test` 整个目录、
> `spring-boot-starter-webmvc-test` 依赖、JaCoCo 插件与其版本/门禁属性，
> `lombok.config` 里只服务覆盖率的那条注释也一并去掉。定位是「可直接发布的干净产物」。

脚手架其他部分（统一响应、traceId、异步、跨域、Jackson、Actuator、接口文档）没动，
要看去 **`template`** 分支的 README。
