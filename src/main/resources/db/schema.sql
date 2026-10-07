-- 演示表：与 com.zjc.demo.entity.DemoUser 一一对应，建在独立的 demo 模式（schema）下。
--
-- 执行方式（任选其一）：
--   psql -h localhost -U jiancai.zhong -d postgres -f src/main/resources/db/schema.sql
--   psql -h localhost -U jiancai.zhong -d postgres -c "$(cat src/main/resources/db/schema.sql)"
--
-- 本文件全部幂等（IF NOT EXISTS），可重复执行。
--
-- 为什么用独立 schema 而不是全塞进 public：
-- public 是 PostgreSQL 的默认模式，所有角色默认都有 CREATE 权限，业务表堆在那里既难管理，
-- 也让「这张表属于哪个应用」变得不可考。独立 schema 还能一次性授权/回收，迁移与清理都干净。
--
-- 注意：JDBC URL 上必须带 currentSchema=demo（已在 application-db.yaml 配置），
-- 否则连接会走默认的 search_path（"$user", public），建的表对象名前不加 schema 前缀就会落到 public 去。
-- 这里的对象名都显式写了 demo. 前缀，因此无论 currentSchema 是否生效，表都一定建在 demo 下。

CREATE SCHEMA IF NOT EXISTS demo;

CREATE TABLE IF NOT EXISTS demo.demo_user
(
    -- 主键不用 bigserial：雪花 ID 由 MyBatis-Plus 在本地生成，数据库侧不需要序列。
    -- 若把 mybatis-plus.global-config.db-config.id-type 改成 auto，这里要换成 bigserial / identity。
    id          bigint       PRIMARY KEY,
    username    varchar(64)  NOT NULL,
    email       varchar(128),
    age         integer,
    -- 乐观锁：NOT NULL DEFAULT 0 是硬性要求。值为 NULL 时 MP 的乐观锁拦截器会静默跳过，
    -- 该行将失去并发保护且不报错。
    version     integer      NOT NULL DEFAULT 0,
    -- 逻辑删除：0 未删除 / 1 已删除，与 @TableLogic 一致
    deleted     integer      NOT NULL DEFAULT 0,
    -- timestamp 不带时区，对应 Java 的 LocalDateTime。
    -- 若需要跨时区精确语义，请改用 timestamptz + OffsetDateTime。
    create_time timestamp,
    update_time timestamp
);

-- 用户名唯一。用「部分唯一索引」而非普通唯一约束：只约束未删除的行，
-- 否则删掉一个用户后，他的用户名会被永久占用、无法重新注册。
CREATE UNIQUE INDEX IF NOT EXISTS uk_demo_user_username
    ON demo.demo_user (username)
    WHERE deleted = 0;

-- 分页场景按主键倒序，与 DemoUserController 的 orderByDesc(DemoUser::getId) 对齐
CREATE INDEX IF NOT EXISTS idx_demo_user_id ON demo.demo_user (id DESC);

COMMENT ON SCHEMA demo IS '演示模式：PostgreSQL + MyBatis-Plus 集成示例';
COMMENT ON TABLE demo.demo_user IS '演示用户表：PostgreSQL + MyBatis-Plus 集成示例';
COMMENT ON COLUMN demo.demo_user.id IS '主键，雪花 ID，由 MyBatis-Plus 生成';
COMMENT ON COLUMN demo.demo_user.version IS '乐观锁版本号，NULL 会导致乐观锁失效';
COMMENT ON COLUMN demo.demo_user.deleted IS '逻辑删除标记：0 未删除、1 已删除';
