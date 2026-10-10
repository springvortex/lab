-- 建系统用户表。
--
-- 命名与目录约定：
--   版本号 = 日期 + 当日序号（V20261010.1），版本号在 ddl / dml 两个目录之间是全局唯一的，
--   不能各用一套。结构变更放 ddl/，初始化数据放 dml/。
--
-- 两条硬规矩：
--   1. 已执行过的脚本不能再改——Flyway 会校验 checksum，改了启动直接失败；要调整请新增一个脚本；
--   2. 不用 IF NOT EXISTS——让「表已存在」直接报错，比默默跳过更容易发现有人绕过 Flyway 建表。
--
-- Flyway 连的就是目标库，脚本里不需要写库名。

CREATE TABLE public.sys_user
(
    -- 雪花 ID，由 MyBatis-Plus 生成，不依赖数据库序列
    id              bigint       NOT NULL,
    username        varchar(64)  NOT NULL,
    -- 密文，不存明文
    password        varchar(128) NOT NULL,
    nickname        varchar(64),
    real_name       varchar(64),
    phone           varchar(20),
    email           varchar(128),
    avatar          varchar(255),
    -- 0 未知 / 1 男 / 2 女
    gender          integer      NOT NULL DEFAULT 0,
    birthday        date,
    -- 0 禁用 / 1 启用 / 2 锁定
    status          integer      NOT NULL DEFAULT 1,
    dept_id         bigint,
    last_login_time timestamp,
    last_login_ip   varchar(64),
    remark          varchar(500),
    -- 0 未删 / 1 已删
    deleted         integer      NOT NULL DEFAULT 0,
    create_by       bigint,
    create_time     timestamp,
    update_by       bigint,
    update_time     timestamp,
    PRIMARY KEY (id)
);

-- 部分唯一索引：只约束未删除的行，删掉的用户不占登录名
CREATE UNIQUE INDEX uk_sys_user_username ON public.sys_user (username) WHERE deleted = 0;

CREATE INDEX idx_sys_user_dept ON public.sys_user (dept_id) WHERE deleted = 0;
CREATE INDEX idx_sys_user_status ON public.sys_user (status) WHERE deleted = 0;

COMMENT ON TABLE public.sys_user IS '系统用户表';
COMMENT ON COLUMN public.sys_user.username IS '登录名，唯一';
COMMENT ON COLUMN public.sys_user.password IS '密码密文，不存明文';
COMMENT ON COLUMN public.sys_user.status IS '状态：0 禁用、1 启用、2 锁定';
COMMENT ON COLUMN public.sys_user.deleted IS '逻辑删除标记：0 未删除、1 已删除';
