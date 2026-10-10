-- 系统用户表。幂等（IF NOT EXISTS），可重复执行：
--   psql "postgresql://wechat:<密码>@129.204.226.206:5432/wechat" -f src/main/resources/db/schema.sql
--
-- 注意：pg_hba.conf 是按库放行的，库名必须是配置里那个（wechat），写成 postgres 会被拒绝。

CREATE TABLE IF NOT EXISTS public.sys_user
(
    -- 雪花 ID，由 MyBatis-Plus 生成，不依赖数据库序列
    id
    bigint
    NOT
    NULL,
    username
    varchar
(
    64
) NOT NULL,
    -- 密文，不存明文
    password varchar
(
    128
) NOT NULL,
    nickname varchar
(
    64
),
    real_name varchar
(
    64
),
    phone varchar
(
    20
),
    email varchar
(
    128
),
    avatar varchar
(
    255
),
    -- 0 未知 / 1 男 / 2 女
    gender integer NOT NULL DEFAULT 0,
    birthday date,
    -- 0 禁用 / 1 启用 / 2 锁定
    status integer NOT NULL DEFAULT 1,
    dept_id bigint,
    last_login_time timestamp,
    last_login_ip varchar
(
    64
),
    remark varchar
(
    500
),
    -- 0 未删 / 1 已删
    deleted integer NOT NULL DEFAULT 0,
    create_by bigint,
    create_time timestamp,
    update_by bigint,
    update_time timestamp,
    PRIMARY KEY
(
    id
)
    );

-- 部分唯一索引：只约束未删除的行，删掉的用户不占登录名
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username
    ON public.sys_user (username)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_sys_user_dept ON public.sys_user (dept_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_sys_user_status ON public.sys_user (status) WHERE deleted = 0;

COMMENT
ON TABLE public.sys_user IS '系统用户表';
COMMENT
ON COLUMN public.sys_user.username IS '登录名，唯一';
COMMENT
ON COLUMN public.sys_user.password IS '密码密文，不存明文';
COMMENT
ON COLUMN public.sys_user.status IS '状态：0 禁用、1 启用、2 锁定';
COMMENT
ON COLUMN public.sys_user.deleted IS '逻辑删除标记：0 未删除、1 已删除';
