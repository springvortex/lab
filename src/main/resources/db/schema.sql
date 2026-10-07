-- 演示表：与 com.zjc.demo.entity.DemoUser 一一对应。
--
-- 执行方式：
--   mysql -h 127.0.0.1 -u root -p < src/main/resources/db/schema.sql
--
-- 脚本幂等（IF NOT EXISTS），可重复执行。
--
-- MySQL 的 schema 就是 database，没有第二层命名空间。所以 PostgreSQL 分支里的 demo 模式，
-- 在这里对应一个名为 demo 的库。

CREATE DATABASE IF NOT EXISTS demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE demo;

CREATE TABLE IF NOT EXISTS demo_user
(
    -- 主键不用 auto_increment：雪花 ID 由 MyBatis-Plus 在本地生成，数据库侧不需要自增。
    -- 若把 mybatis-plus.global-config.db-config.id-type 改成 auto，这里换成 bigint NOT NULL AUTO_INCREMENT。
    id          bigint       NOT NULL COMMENT '主键，雪花 ID，由 MyBatis-Plus 生成',
    username    varchar(64)  NOT NULL COMMENT '用户名',
    email       varchar(128) DEFAULT NULL,
    age         int          DEFAULT NULL,
    -- 乐观锁：NOT NULL DEFAULT 0 是硬性要求。值为 NULL 时 MP 的乐观锁拦截器会静默跳过，
    -- 该行将失去并发保护且不报错。
    version     int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，NULL 会导致乐观锁失效',
    -- 逻辑删除：0 未删除 / 1 已删除，与 @TableLogic 一致
    deleted     int          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除、1 已删除',
    -- datetime 不带时区，对应 Java 的 LocalDateTime（PostgreSQL 分支用的是 timestamp）
    create_time datetime     DEFAULT NULL,
    update_time datetime     DEFAULT NULL,
    PRIMARY KEY (id),
    -- 用户名唯一。注意：MySQL 没有「局部唯一索引」，这条约束覆盖全表——
    -- 被逻辑删除的行（deleted = 1）仍占着用户名，同名无法重新注册。
    -- 想保留「删除后可复用」的语义，改用生成列把已删除行置为 NULL（唯一索引允许多个 NULL）：
    --   username_active varchar(64) GENERATED ALWAYS AS (IF(deleted = 0, username, NULL)) STORED,
    --   UNIQUE KEY uk_demo_user_username (username_active)
    UNIQUE KEY uk_demo_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='演示用户表：MySQL + MyBatis-Plus 集成示例';
