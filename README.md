# SpringVortexDemo

> 本仓库是 Spring Boot 4 脚手架仓库。**`main` 分支只做导航，不放任何代码**，它是别人 clone 下来看到的第一个分支，作用是告诉你去哪个分支拿什么。

## 分支一览

| 分支 | 用途 | Spring Boot | JDK | 拉出自 | 拉出日期 | 状态 | 跳转 |
|------|------|-------------|-----|--------|----------|------|------|
| **`main`** | 默认分支，纯索引：仅本 README + `LICENSE` + `.gitignore` | — | — | — | 2026-09 | 稳定 | [进入](https://github.com/springvortex/lab/tree/main) |
| **`template`** | **当前主用**的 Spring Boot 4 脚手架，日常改动落在这里 | 4.1.1 | 25 | `sample/boot4-jdk25` | 2026-10-05 | 活跃 | [进入](https://github.com/springvortex/lab/tree/template) |
| **`sample/boot4-jdk25`** | Boot 4 基座，`template` 的上游，**通常只读** | 4.1.1 | 25 | — | 2026-09 | 稳定 | [进入](https://github.com/springvortex/lab/tree/sample/boot4-jdk25) |
| **`sample/boot3-jdk21`** | Boot 3 基座 | 3.5.16 | 21 | `sample/boot4-jdk25` | 2026-10-05 | 稳定 | [进入](https://github.com/springvortex/lab/tree/sample/boot3-jdk21) |
| **`sample/boot2-jdk8`** | Boot 2 基座（JDK 8） | 2.7.18 | 8 | `sample/boot3-jdk21` | 2026-10-05 | 稳定 | [进入](https://github.com/springvortex/lab/tree/sample/boot2-jdk8) |
| **`sample/boot2-jdk11`** | Boot 2 基座（JDK 11） | 2.7.18 | 11 | `sample/boot2-jdk8` | 2026-10-05 | 稳定 | [进入](https://github.com/springvortex/lab/tree/sample/boot2-jdk11) |
| **`springboot4/feature`** | Boot 4 新特性教程文章 + 可运行示例 | 4.1.1 | 25 | `sample/boot4-jdk25` | 2026-10-02 | 稳定 | [进入](https://github.com/springvortex/lab/tree/springboot4/feature) |
| **`springboot4/i18n`** | 教程分支：响应消息国际化，一个 `Accept-Language` 请求头切换中英文（附保姆级教程） | 4.1.1 | 25 | `template` | 2026-10-06 | 活跃 | [进入](https://github.com/springvortex/lab/tree/springboot4/i18n) |

> **该拉哪条？** 起新项目用当前主用的 `template`。JDK 跑不到 25 时，按上表右侧的 Boot / JDK 组合挑对应的 `sample/*` 基座。四个基座功能等价，差异只在版本和对应的 API 替换（`javax.*` ↔ `jakarta.*`、`RestTemplate` ↔ `RestClient`、springdoc 1.x ↔ 3.x 等），各自 README 里都有完整的版本对照表。

## 怎么用

```bash
# 只想看看模板长什么样
git switch template

# 基于模板建新项目（推荐：同一仓库拉分支）
git switch template && git pull --ff-only
git switch -c feature/your-project

# 或者：独立新仓库
git clone --branch template --single-branch <本仓库地址> your-project
cd your-project && rm -rf .git && git init

# 需要 JDK 21 / 11 / 8 的版本时，把分支名换成对应的基座即可
git clone --branch sample/boot3-jdk21 --single-branch <本仓库地址> your-project
```

> 注意：远端 `origin/HEAD` 指向 `main`。从 `template` 或其他非 `main` 分支提 PR / push 时
> **要显式指定目标分支**，别默认合到 `main`。

---
