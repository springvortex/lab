-- 补 sys_user.gender 的字段注释。
-- 已执行过的脚本不能改（Flyway 校验 checksum），要调整就新加一个版本号更大的脚本。
-- PostgreSQL 的 COMMENT ON 是事务性的，失败整条回滚。

COMMENT ON COLUMN public.sys_user.gender IS '性别：0 未知、1 男、2 女';
