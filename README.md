# 单表 CRUD 一行 SQL 都不用写 —— Spring Boot 4 接 MySQL + MyBatis-Plus 实战笔记

写后台最开始那几天，我干得最多的事就是：建表、写 `insert`、写 `select`、写分页的 `limit`、再写一条一模一样的 `count(*)`。写到第三张表的时候我意识到——这些活儿里没有任何一句是需要动脑子的。

这篇记的是怎么把这部分体力活交给工具：**数据库用 MySQL，持久层用 MyBatis-Plus，单表增删改查一个字都不用写**。Spring Boot 4.1.1 + JDK 25，代码在本仓库，照着跑就能复现。

```bash
git clone -b springboot4/mysql git@github.com:springvortex/lab.git
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
9. [和 PostgreSQL 那条分支差在哪](#9-和-postgresql-那条分支差在哪)

---

## 1. 这分支是怎么来的

本分支 `springboot4/mysql` 是从姊妹分支 **`springboot4/postgresql`** 拉出来的：

```bash
git switch -c springboot4/mysql springboot4/postgresql
```

`springboot4/postgresql` 本身又是从主脚手架 `template` 拉出来、专门做 PostgreSQL + MyBatis-Plus 集成的。所以**持久层的分层、注解、测试在本分支全都不用重写**——它已经是跑通的状态，我这边只做"把数据库从 PostgreSQL 换成 MySQL"这一件事。

换句话说：这篇文章的重点不是"怎么从零搭一套持久层"（那个看 PostgreSQL 分支的 README），而是**同一套代码换 MySQL 要动哪些地方，以及 MySQL 和 PostgreSQL 行为不一样的地方会在哪里咬你一口**。

改动的文件其实很少：

```
pom.xml                                        # 改：驱动坐标换成 mysql-connector-j
config/application-db.yaml                     # 改：URL / 驱动类 / 用户名
config/MybatisPlusConfig.java                  # 改：分页方言 POSTGRE_SQL → MYSQL
db/schema.sql                                  # 重写：MySQL 建表语句
mapper/DemoUserMapper.xml                      # 改：ILIKE → LIKE
entity/DemoUser.java                           # 改：注释里的类型说明（timestamp → datetime）
README.md                                      # 就是本文
```

业务代码（Controller / Service / DTO / PageResult）**一行没动**。这本身就说明了一件事：MyBatis-Plus 那层抽象是有价值的，换库换不到业务代码上。

脚手架其他部分（统一响应、traceId、异步、跨域、Jackson、Actuator、接口文档）没动，
要看去 **`template`** 分支的 README。

---

## 2. 先说清楚这俩各自图什么

### MySQL 有什么好的

不用我推销——国内大部分后台的数据库就是它，生态和资料量摆在那儿。客观说它的几个实在之处：

- **运维人手最好找**。出了问题随手一搜就有答案，云厂商的托管服务也最成熟。这是"能不能招到人、出问题好不好查"层面的优势，比任何基准测试都实际。
- **InnoDB 的事务和行锁够用**。大部分业务要的就是"要么全成功要么全回滚"，InnoDB 完全能撑。
- **`utf8mb4_0900_ai_ci` 默认大小写不敏感**。`LIKE 'zhang%'` 能匹配 `Zhang...`，不用写 `LOWER()` 包一层。省事，但也是坑（第 7 节说）。
- **8.0 之后补上了不少东西**：窗口函数、CTE、`JSON` 列类型、生成列（本文就用上了生成列来解决一个 PostgreSQL 有原生方案、MySQL 没有的问题）。
- **`AUTO_INCREMENT` 简单直接**。不像 PostgreSQL 还得决定用 `serial` 还是 `identity`。

它的短板也直说：JSON 支持不如 PostgreSQL 的 `jsonb`（MySQL 的 `JSON` 不能直接建普通索引，得靠生成列绕）、没有范围类型、没有部分索引（这是我这次踩得最深的一个坑）、GIS 也弱一些。

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
- **分页插件**。传个 `Page` 进去，自动先跑 `COUNT` 再跑 `LIMIT/OFFSET`，`total`、`pages` 都给你算好。**换数据库只要改一个方言枚举**，这是本文能这么短的原因。
- **逻辑删除**。字段标 `@TableLogic`，`removeById` 变成 `UPDATE ... SET deleted = 1`，查询自动加 `AND deleted = 0`。
- **乐观锁**。字段标 `@Version`，`UPDATE` 自动追加 `AND version = ?` 并 `version + 1`。
- **自动填充**。`@TableField(fill = FieldFill.INSERT)`，插入时自动填 `create_time`。
- **代码生成器**。本文没用（我们手写演示代码），真实项目里它能一口气生成 Entity / Mapper / Service / Controller。

一句话：**MyBatis 管"复杂的 SQL 我来写"，MyBatis-Plus 管"简单的 SQL 你别写了"**。两者不冲突，复杂查询照样写 XML。

---

## 3. 为什么是这两个搭一起

- **MP 对 MySQL 是一等公民**：分页插件里写 `DbType.MYSQL` 就走正确方言，不用自己拼 `LIMIT/OFFSET`。
- **雪花 ID 和 bigint 对得上**：MP 默认生成 19 位 bigint 主键，MySQL 的 `bigint` 正好装下，而且**插入前就知道主键**，不用等数据库回写——这也意味着表列不需要 `AUTO_INCREMENT`。
- **InnoDB + 唯一索引**，配合 MP 的逻辑删除，就是最常规的"防重复注册"方案。
- 真要用 MySQL 特有函数（`DATE_FORMAT`、`IFNULL`、`GROUP_CONCAT`），直接写 XML，MP 不拦你。

---

## 4. 先跑起来看看效果

### 第 1 步：建库建表

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u root -p < src/main/resources/db/schema.sql
```

脚本幂等（`IF NOT EXISTS`），重复执行没问题。它会建一个叫 `demo` 的库和 `demo_user` 表。

⚠️ **`--default-character-set=utf8mb4` 别省**。中文版 Windows 上 `mysql` 客户端默认协商成 `gbk`，
而脚本文件是 UTF-8——少了这个参数，文件里的中文（表名注释、列注释）会被当 GBK 解读后再转存，
库里就留下一串 `涓婚敭锛岄洩鑺?` 这样的乱码。**这不是显示问题，是数据真写坏了**，
而且建表照样成功、不报错。详见第 7 节坑 13。

### 第 2 步：起服务

```bash
export JAVA_HOME=D:/app/Java/jdk-25.0.2    # 终端默认 JDK 可能是 8，不切会编译失败
mvn -o clean verify
java -jar target/spring-vortex-demo-0.0.1.jar --server.port=8000
```

### 第 3 步：调接口

```bash
# 新增
curl --noproxy '*' -X POST localhost:8000/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","age":18}'

# 查详情
curl --noproxy '*' localhost:8000/api/users/1900000000000000001

# 分页（第 1 页，每页 10 条，用户名模糊匹配）
curl --noproxy '*' "localhost:8000/api/users?current=1&size=10&username=ali"

# 按条件查（自定义 SQL，支持邮箱模糊匹配 + 最小年龄）
curl --noproxy '*' "localhost:8000/api/users/search?keyword=example.com&minAge=18"

# 年龄段统计
curl --noproxy '*' localhost:8000/api/users/age-groups

# 修改（带乐观锁）
curl --noproxy '*' -X PUT localhost:8000/api/users/1900000000000000001 \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"new@example.com","age":19}'

# 删除（逻辑删除，数据不会真消失）
curl --noproxy '*' -X DELETE localhost:8000/api/users/1900000000000000001
```

接口文档在 `http://localhost:8000/swagger-ui.html`（非生产环境自动开启）。

> `curl` 走代理会返回奇怪的东西，**记得加 `--noproxy '*'`**。

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
CREATE DATABASE IF NOT EXISTS demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE demo;

CREATE TABLE IF NOT EXISTS demo_user
(
    id          bigint       NOT NULL COMMENT '主键，雪花 ID，由 MyBatis-Plus 生成',
    username    varchar(64)  NOT NULL COMMENT '用户名',
    email       varchar(128) DEFAULT NULL,
    age         int          DEFAULT NULL,
    version     int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，NULL 会导致乐观锁失效',
    deleted     int          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除、1 已删除',
    create_time datetime     DEFAULT NULL,
    update_time datetime     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_demo_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='演示用户表：MySQL + MyBatis-Plus 集成示例';
```

几个"为什么"：

1. **主键为什么不用 `AUTO_INCREMENT`？** 雪花 ID 是 MP 在 Java 侧生成的，插入前主键就已知。只有把 `id-type` 改成 `auto` 时，这里才要写成 `bigint NOT NULL AUTO_INCREMENT`。
2. **`version` 为什么必须 `NOT NULL DEFAULT 0`？** 值为 `NULL` 时 MP 的乐观锁拦截器**静默跳过**，那一行就没有并发保护了，而且不报错。
3. **时间列用 `datetime` 而不是 `timestamp`。** MySQL 的 `timestamp` **会随连接时区做换算**，存进去和读出来可能不是同一个值；`datetime` 不会。要"存进去啥就是啥"就用 `datetime`，跟 Java 的 `LocalDateTime` 正好配对。
4. **字符集 `utf8mb4` + 排序规则 `utf8mb4_0900_ai_ci`。** `utf8mb4` 才能存 emoji；`_ai_ci` 是"重音不敏感 + 大小写不敏感"，也就是 `LIKE` 天生不区分大小写（第 7 节会讲这带来的坑）。
5. **`ENGINE = InnoDB`。** 要事务和行锁就得是 InnoDB，别用 MyISAM。

另外两个说明：

- **为什么用独立的 `demo` 库**：MySQL 的 schema 就是 database，**只有一层**。PostgreSQL 那条分支用的是 `demo` 模式（库 + 模式两层），这里对应成一个叫 `demo` 的库。
- **为什么不用 `spring.sql.init` 让应用启动时自动建表**：那会让"应用启动"依赖"数据库可写"，库不可用时连上下文都起不来，所有测试一起失败。建表交给初始化脚本更稳妥。

⚠️ **MySQL 没有"部分唯一索引"**，这是本次改动里最需要留意的一处。见第 7 节坑 1。

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

⚠️ 时间字段用 `LocalDateTime`（不带时区），对应 MySQL 的 `datetime` 列。要是列类型是 `timestamp`（MySQL 的 `timestamp` 会随时区换算），得改用 `Instant`。

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

条件特别多且带分支、要 join、要用 MySQL 特有函数（`DATE_FORMAT`、`IFNULL`），或者 Wrapper 拼出来的 SQL 性能不行时，写 XML：

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
                AND (username LIKE CONCAT('%', #{keyword}, '%')
                  OR email    LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="minAge != null">
                AND age &gt;= #{minAge}
            </if>
        </where>
        ORDER BY id DESC
    </select>
</mapper>
```

> 这里的 `LIKE` 就是大小写不敏感的——MySQL 8 默认排序规则 `utf8mb4_0900_ai_ci` 里的 `_ci` 是 case insensitive。
> 移植到区分大小写的库（比如 PostgreSQL）得改成 `ILIKE`，或者显式写 `LOWER(...) LIKE LOWER(...)`。

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

**重复数据怎么给明确提示**：用户名撞唯一索引时，MyBatis-Spring 会把驱动的重复键错误翻译成 `DuplicateKeyException`。它既不是 `BusinessException` 也不是 Spring 的 4xx 异常，**不处理就会掉进全局兜底被报成 500「服务内部错误」**——排查方向被带偏，前端也只会弹一句没用的提示。

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
- 原始驱动报错（含索引名 `uk_demo_user_username`）只进日志，不外泄。

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
    url: ${DB_URL:jdbc:mysql://localhost:3306/demo?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true&sslMode=DISABLED&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

`${DB_URL:默认值}` 的意思是：**环境变量存在就用它，不存在就用冒号后面的默认值**。所以换库不用改配置文件：

```bash
DB_URL="jdbc:mysql://10.0.0.12:3306/demo" DB_USERNAME=app DB_PASSWORD=xxx mvn clean verify
```

URL 上五个参数，前三个是"不写会出问题"，后两个是"本地开发用、生产必须去掉"：

| 参数 | 作用 |
|---|---|
| `characterEncoding=UTF-8` | 连接字符集。不写会用服务端默认，中文可能变问号 |
| `serverTimezone=Asia/Shanghai` | 驱动解析时间时用的时区。容器里 JVM 常是 UTC，不设会让接口时间与库差 8 小时 |
| `rewriteBatchedStatements=true` | 把批量 insert 改并成一条多值 INSERT，`saveBatch` 提速的关键。**不配它，批量插入和逐条插入速度差不多** |
| `sslMode=DISABLED` | 本地免证书连接用。⚠️ **生产必须去掉**，否则等于明文传输 |
| `allowPublicKeyRetrieval=true` | MySQL 8+ 默认 `caching_sha2_password`，非 SSL 首次握手需要它才能取到公钥。⚠️ **生产同样要去掉** |

> 想在 `performance_schema.session_connect_attrs` 里认出是哪个服务，可以再加
> `connectionAttributes=program_name:SpringVortexDemo`（对应 PostgreSQL 的 `ApplicationName`）。

> ⚠️ **部署生产务必显式设置这三个环境变量。** 文件里的默认值指向本机库；
> 生产忘了配，应用会「正常启动、正常写入」，只是数据全去了错误的地方——
> **「连错库」比「连不上」危险得多**，往往几周后才被发现。
> 想要「缺环境变量就启动失败」的硬约束，把默认值去掉即可：写 `${DB_URL}` 而不是 `${DB_URL:jdbc:mysql://...}`。

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

⚠️ **`idle-timeout` / `max-lifetime` / `keepalive-time` 三项都必须小于 MySQL 的 `wait_timeout`**（默认 28800 秒 = 8 小时）。否则你会拿到被服务端单方面掐断的"半开连接"，报错信息还很难看懂。

⚠️ **这些是毫秒数字，不是 `30s` 这种 Duration 字符串**。写成 `30s` 会在启动期报
`NumberFormatException: For input string: "30s"`。同项目的 `spring.http.clients.*` 才是 Duration，两套单位别混。

⚠️ **`maximum-pool-size` 不是越大越好**。MySQL 每个连接在服务端都占一个线程和一块内存，开太多会把库拖垮。经验值大致 `CPU核数 × 2 + 磁盘数`，20 对多数中小应用够用。

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
| `auto` | 数据库自增 | 换成它的话，建表列要改成 `bigint NOT NULL AUTO_INCREMENT` |
| `input` | 自己手动 set 主键 | 有外部 ID 体系 |
| `none` | 不生成，不填就是 `null` | 基本不用 |

### 6.4 分页插件：换库就改这一个枚举

```java
@Configuration
@MapperScan("com.zjc.demo.mapper")
public class MybatisPlusConfig {

    public static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());   // 先：乐观锁
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);                               // 后：分页
        return interceptor;
    }
}
```

**这里就是本文最核心的一行改动**：PostgreSQL 分支写的是 `DbType.POSTGRE_SQL`，本分支改成 `DbType.MYSQL`。就这一处，分页 SQL 的方言（`LIMIT/OFFSET` 的写法、`COUNT` 的优化策略）全跟着换了。

`DbType` 是枚举，常见取值：`MYSQL`、`POSTGRE_SQL`、`ORACLE`、`SQL_SERVER`、`DM`（达梦）、`KINGBASE_ES`（人大金仓）等。换数据库基本就是改这一个值。

另外两点别乱改：

- **分页插件必须放最后**。官方明确要求，否则 `COUNT` SQL 可能统计不准。
- `MAX_PAGE_SIZE = 500` 是防呆阀：前端传 `size=100000` 会被**静默截断**成 500。需要严格拒答请在 Controller 层自己校验返回 400。

### 6.5 依赖：三个，少一个都不行

| 坐标 | 作用 | 少了 / 写错会怎样 |
|---|---|---|
| `mybatis-plus-spring-boot4-starter` | Boot 4 专用 starter，传递引入 jdbc + mybatis-spring 4.x | 写成 `-spring-boot3-starter` 会把 Boot 3 的自动配置拉进来，与 Boot 4 打架 |
| `mybatis-plus-jsqlparser` | 分页插件 `PaginationInnerInterceptor` 的载体 | **自 3.5.9 起分页插件被拆出 starter**。不引这个类直接不存在（编译期报错）；勉强绕过则分页「看起来能跑但查的是全表」 |
| `com.mysql:mysql-connector-j` | JDBC 驱动（runtime 即可） | 启动期不报错，第一条 SQL 才 `ClassNotFoundException` |

⚠️ MySQL 驱动的坐标是 **`com.mysql:mysql-connector-j`**，老坐标 `mysql:mysql-connector-java`
**已经废弃**，照老博客抄会拉到一个不再维护的版本。版本由 Boot 的依赖管理统一给出，**不要自己锁版本**。

> `mybatis-plus-jsqlparser` 有三个坐标，对应不同 jsqlparser 版本：
> `mybatis-plus-jsqlparser`（jsqlparser 5.2，默认选它）、`-5.0`、`-4.9`（项目里已有 jsqlparser 4.9 时用，避免冲突）。

**别再引原生 `mybatis-spring-boot-starter`**：MP 的 starter 已经传递带进来了，两套自动配置会打架。

---

## 7. 我踩过的坑

按讨厌程度排序。共同点是：**错了不报错，就是悄悄不对**。

1. **MySQL 没有"部分唯一索引"，逻辑删除后用户名会被永久占用。**
   PostgreSQL 那条分支写的是 `CREATE UNIQUE INDEX ... WHERE deleted = 0`，只约束"还活着的行"，删掉用户后用户名就释放了。**MySQL 不支持这个语法**，只能写普通唯一索引，于是删掉的用户仍占着用户名、同名无法重新注册。8.0 可以用**生成列**绕过（唯一索引允许多个 `NULL`）：

    ```sql
    username_active varchar(64) GENERATED ALWAYS AS (IF(deleted = 0, username, NULL)) STORED,
    UNIQUE KEY uk_demo_user_username (username_active)
    ```

   本分支没启用这个方案（保持演示代码简单），**你的业务如果要"删除后可重新注册"，记得换上去**。
2. **`LIKE` 默认不区分大小写，是"碰巧"的，不是"保障"。** 它来自排序规则 `utf8mb4_0900_ai_ci` 里的 `_ci`。哪天有人把列的排序规则改成 `_bin`，这个行为就变了，而且不会有任何报错。需要明确语义就写 `LOWER(username) LIKE LOWER(...)`。
3. **`timestamp` 会悄悄做时区换算。** MySQL 的 `timestamp` 存的是 UTC、读的时候按连接时区转回来；`datetime` 不会。本分支用 `datetime` 就是为了"存进去啥就是啥"。用 `timestamp` 的话，`serverTimezone` 一配错，时间就整体偏移。
4. **`@Version` 为 `null` 时乐观锁整段跳过，且不报错。** 所以建表必须 `NOT NULL DEFAULT 0`。
   更阴的是：**`insert` 不会把数据库的 `DEFAULT 0` 回写进实体**——所以「插入后拿同一个对象直接更新」是**没有并发保护**的，生成的 SQL 里根本没有 `AND version = ?`。正确做法是先 `selectById` 再改。
5. **手写 SQL 漏了 `AND deleted = 0`。** 逻辑删除的自动追加只对 `BaseMapper` 内置方法生效，手写 XML 它管不着。
6. **XML 里自己写了 `limit`。** 分页插件靠"第一个参数是 `IPage`"识别，自己再写会打架，表现是分页结果莫名其妙。
7. **忘了引 `mybatis-plus-jsqlparser`。** 少它时分页**看起来能跑**——不报错、有数据，只是每次把全表捞出来再内存截断。表小的时候完全没感觉。
8. **`IService` / `ServiceImpl` 在 3.5.17 换了包。** 从 `com.baomidou.mybatisplus.extension.service.*` 迁到 `com.baomidou.mybatisplus.spring.service.*`。照老博客写直接编译不过。
9. **`sslMode=DISABLED` / `allowPublicKeyRetrieval=true` 别带进生产。** 本地免证书连接很方便，但生产带着这两个参数等于接受明文传输。
10. **Hikari 超时写成了 `30s`。** 那是毫秒数字，写 `30s` 启动直接失败——这个还算好的，至少会报错。
11. **MySQL 装完 root 可能是空密码。** 连不上时会报
    `Access denied for user 'root'@'localhost' (using password: NO)`，
    先设一个：`ALTER USER 'root'@'localhost' IDENTIFIED BY '123456';`
12. **MyBatis 一级缓存会让同一事务内两次 `selectById` 返回同一个对象实例。** 想造"两份数据"模拟并发冲突是造不出来的（改了第一份等于改了第二份）。测试里改用 `JdbcTemplate` 直接在库里把 `version` 加 1，模拟另一个事务抢先提交。
13. **`mysql` 客户端不加 `--default-character-set=utf8mb4` 会把中文注释写坏。**
    中文版 Windows 上客户端默认协商成 `gbk`：

    ```
    character_set_client      gbk
    character_set_connection  gbk
    character_set_results     gbk
    character_set_database    utf8mb4   ← 库里明明是 utf8mb4
    ```

    于是 UTF-8 的脚本文件被当成 GBK 解读、再转存成 utf8mb4，注释变成
    `涓婚敭锛岄洩鑺?ID锛局敱 MyBatis-Plus 鐢熸垚` 这种双重编码乱码。
    **危险在于：这不是显示问题，是数据真的写坏了，而且建表语句照样成功、不报错。**
    验证方法——取十六进制看看到底存了什么：

    ```sql
    SELECT HEX(COLUMN_COMMENT) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'demo' AND TABLE_NAME = 'demo_user' AND COLUMN_NAME = 'id';
    -- 正确：E4B8BBE994AE...（E4B8BB = "主" 的 UTF-8 编码）
    -- 坏了：E6B693E5A99A...（E6B693 = "涓" 的 UTF-8 编码，即 GBK 误读的结果）
    ```

    已经写坏了就只能删库重建（`DROP DATABASE demo;` 再跑一遍脚本）。

---

## 8. 怎么自检

**跑测试前必须先把库建好**，否则所有真库用例都会报 `Unknown database 'demo'`：

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u root -p < src/main/resources/db/schema.sql
```

然后：

```bash
export JAVA_HOME=D:/app/Java/jdk-25.0.2
mvn -o clean verify
```

**本机实测（MySQL 8.0，建库后）：149 个用例全通过，`BUILD SUCCESS`，
行覆盖率 97.00%、分支覆盖率 92.11%，均过 0.80 门禁。**

⚠️ **跑测试需要一个真实的 MySQL**。Mapper 层和 Controller 层的集成测试是打真库的，
不是 H2 内存库，也不是 Mockito 打桩。

为什么非得是真库？因为分页方言、`datetime` 与 `LocalDateTime` 的映射、逻辑删除追加的
`AND deleted = 0`、乐观锁的 `WHERE version = ?`，**全都是"跑在别的数据库上才暴露"的行为**。
用 H2 打桩能证明代码逻辑通顺，证明不了 MySQL 上真的对。

好消息是这些测试类标了 `@Transactional`，**跑完自动回滚**，不会在库里留垃圾数据。

### 跑挂了怎么排查

三种报错都很典型，看信息就能定位：

**① 库不存在**

```
java.sql.SQLSyntaxErrorException: Unknown database 'demo'
```

回头执行一遍 `schema.sql` 即可。脚本幂等，重复执行没问题。

**② 认证失败**

```
java.sql.SQLException: Access denied for user 'root'@'localhost' (using password: YES)
```

配置里的默认账号密码是本机开发用的（`root` / `123456`），**跟你机器上的不一定一致**。
改它不用动配置文件，用环境变量覆盖即可：

```bash
DB_PASSWORD=你的真实密码 mvn -o clean verify
DB_URL="jdbc:mysql://10.0.0.12:3306/demo" DB_USERNAME=app DB_PASSWORD=xxx mvn -o clean verify
```

**③ 表不存在**

```
java.sql.SQLSyntaxErrorException: Table 'demo.demo_user' doesn't exist
```

库连上了但表没建，同样是执行 `schema.sql` 的问题。注意 URL 里的库名要和脚本里 `USE` 的一致。

### 改了配置没生效？按这个顺序查

1. `application.yaml` 的 `include` 里有 `db` 吗？漏了整个 profile 不加载，而且**不报错**。
2. 改的是 `config/application-db.yaml` 里的 key 吗？写错 key 不报错，只是静默失效。
3. 环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 有没有设？它们会覆盖文件里的默认值。
4. `mapper-locations` 和你 XML 的实际路径对得上吗？

### 几条常用的 SQL

```sql
-- 看当前有哪些连接（配合 URL 上的 connectionAttributes 很好用）
SELECT PROCESSLIST_USER, PROCESSLIST_HOST, PROCESSLIST_DB, PROCESSLIST_COMMAND
FROM performance_schema.threads WHERE TYPE = 'FOREGROUND';

-- 看服务端超时，对照 Hikari 的 idle-timeout / max-lifetime
SHOW VARIABLES LIKE 'wait_timeout';

-- 确认逻辑删除确实没删数据
SELECT COUNT(*) FROM demo.demo_user WHERE deleted = 1;
```

覆盖率报告在 `target/site/jacoco/index.html`。

---

## 9. 和 PostgreSQL 那条分支差在哪

两个分支的**业务代码、DTO、测试用例完全相同**，差异只在下面这些地方。这张表也顺便说明了"换个数据库到底要动什么"：

| 关注点 | `springboot4/postgresql` | `springboot4/mysql`（本分支） |
|---|---|---|
| JDBC 驱动坐标 | `org.postgresql:postgresql` | `com.mysql:mysql-connector-j` |
| 驱动类 | `org.postgresql.Driver` | `com.mysql.cj.jdbc.Driver` |
| 默认端口 | 5432 | 3306 |
| 命名空间 | 库 + 模式两层，靠 `currentSchema=demo` | schema 即 database，一层，库名就叫 `demo` |
| 批量插入提速 | `reWriteBatchedInserts=true` | `rewriteBatchedStatements=true` |
| 连接标识 | `ApplicationName` → `pg_stat_activity` | `connectionAttributes=program_name:...` → `session_connect_attrs` |
| 无时区时间类型 | `timestamp` | `datetime`（MySQL 的 `timestamp` 会做时区换算） |
| 大小写不敏感匹配 | `ILIKE`（`LIKE` 区分大小写） | `LIKE` 即可（默认排序规则 `_ci`） |
| 局部唯一索引 | 支持 `... WHERE deleted = 0` | **不支持**，见第 7 节坑 1 的生成列方案 |
| 主键自增（改用 `auto` 时） | `bigserial` / `identity` | `bigint NOT NULL AUTO_INCREMENT` |
| 分页方言 | `DbType.POSTGRE_SQL` | `DbType.MYSQL` |
| 表 / 列注释 | 独立的 `COMMENT ON` 语句 | 建表时内联 `COMMENT` |
| **语句报错后的事务** | 整个事务 aborted，后续语句全部失败 | 只有该语句失败，事务可继续（见下） |

最后一条会实实在在影响测试写法。PostgreSQL 下一条语句报错会让整个事务进入 aborted 状态，
后续语句全部拒绝——所以"重复用户名"那个用例不能加 `@Transactional`（第一次插入成功后事务已经不可用），
只能让两次插入各自独立提交、再手工清理。**MySQL 下语句级错误不污染事务**，所以本分支直接用了
`@Transactional`，测试结束时统一回滚，代码也简洁不少。

---

## 附录：这分支到底改了什么

| 文件 | 改了啥 |
|---|---|
| `pom.xml` | 驱动换成 `com.mysql:mysql-connector-j`（runtime） |
| `config/application-db.yaml` | URL 换 MySQL、驱动类换 `com.mysql.cj.jdbc.Driver`、默认用户 `root` |
| `config/MybatisPlusConfig.java` | 分页方言 `POSTGRE_SQL` → `MYSQL` |
| `db/schema.sql` | 重写。建 `demo` 库 + `demo_user` 表，`utf8mb4` / InnoDB / 内联 COMMENT |
| `mapper/DemoUserMapper.xml` | `ILIKE` → `LIKE` |
| `entity/DemoUser.java` | 注释里 `timestamp` → `datetime` 说明 |
| `controller/DemoUserController.java`、`OpenApiConfig.java` | 注释与 `@Tag` 文案 |
| `DemoUserDuplicateUsernameIntegrationTest` | PostgreSQL 下不能加 `@Transactional`，MySQL 可以，去掉手工清理 |
| `DatabaseConfigTest`、`MybatisPlusConfigTest` | 断言里的库类型 / 方言跟着改 |

脚手架其他部分（统一响应、traceId、异步、跨域、Jackson、Actuator、接口文档）没动，
要看去 **`template`** 分支的 README。
