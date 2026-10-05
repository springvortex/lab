# SpringVortexDemo

> 本仓库是 Spring Boot 4 脚手架仓库。**`main` 分支只做导航，不放任何代码**——
> 它是别人 clone 下来看到的第一个分支，作用是告诉你去哪个分支拿什么。

## 分支一览

| 分支 | 用途 | Spring Boot | JDK | 状态 |
|------|------|-------------|-----|------|
| **`main`** | 默认分支，纯索引：仅本 README + `LICENSE` + `.gitignore` | — | — | 稳定 |
| **`template`** | **当前主用**的 Spring Boot 4 脚手架，日常改动落在这里 | 4.1.1 | 25 | 活跃 |
| **`sample/boot4-jdk25`** | Boot 4 基座，`template` 的上游，**通常只读** | 4.1.1 | 25 | 稳定 |
| **`sample/boot3-jdk21`** | Boot 3 基座 | 3.5.16 | 21 | 稳定 |
| **`sample/boot2-jdk8`** | Boot 2 基座（JDK 8） | 2.7.18 | 8 | 稳定 |
| **`sample/boot2-jdk11`** | Boot 2 基座（JDK 11） | 2.7.18 | 11 | 稳定 |
| **`springboot4/feature`** | Boot 4 新特性教程文章 + 可运行示例 | 4.1.1 | 25 | 稳定 |

> **该拉哪条？** 起新项目用当前主用的 `template`。JDK 跑不到 25 时，按上表右侧的 Boot / JDK 组合
> 挑对应的 `sample/*` 基座。四个基座功能等价，差异只在版本和对应的 API 替换
> （`javax.*` ↔ `jakarta.*`、`RestTemplate` ↔ `RestClient`、springdoc 1.x ↔ 3.x 等），
> 各自 README 里都有完整的版本对照表。

### 脚手架包含什么

下面这份清单对 `template` 与 `sample/boot4-jdk25` 同样适用——两条分支当前内容完全一致。

- **技术栈**：Spring Boot 4.1.1 / Spring Framework 7 / Java 25 / Maven
- **Web 层**：`spring-boot-starter-webmvc`（Boot 4 里 `-web` 的改名）、虚拟线程默认开启
- **统一响应**：`ApiResponse`，body 里的 `code` **就是** HTTP 状态码
- **链路追踪**：traceId 三层闭环 —— `TraceIdFilter`（入站）→ `MdcTaskDecorator`（`@Async` 异步）→
  `TraceIdPropagationInterceptor`（`RestClient` 出站透传）
- **全局异常处理**：`GlobalExceptionHandler`，含 Spring 6.1+ 的 `HandlerMethodValidationException` 兜底
- **配置分层**：`application.yaml` + `config/application-pub.yaml`（公共）+
  `config/application-{dev,test,prod}.yaml`（只写差异）
- **日志**：logback 按级别分文件 + 异步队列 + 生产落控制台（容器化下 `docker logs` 可见）
- **接口文档**：springdoc 3.1.1 + 程序化 `OpenApiConfig`，按模块分组（非生产开启、生产自动关闭）
- **监控**：actuator，含 K8s liveness / readiness 探针
- **质量门禁**：JaCoCo + 112 个测试用例，行 / 分支覆盖率 100%
- **工程规范**：`.editorconfig` / `.gitattributes` / MIT `LICENSE`

详细说明、升级注意事项、以及**派生新项目的完整步骤**都在 `template` 分支的 `README.md` 里
（`sample/boot4-jdk25` 的内容与之一致，用 `git show <分支名>:README.md` 也能看）。

## 分支策略

```
main                        ← 你在这里：纯索引，无代码
├── template                当前主用的 Boot 4 脚手架（Boot 4.1.1 / JDK 25）
│   ├── xxxxx-mysql         教程分支：集成 MySQL
│   └── xxxxx-redis         教程分支：集成 Redis
├── sample/boot4-jdk25      Boot 4 基座：template 的上游，通常只读
├── sample/boot3-jdk21      Boot 3 基座：Boot 3.5.16 / JDK 21
├── sample/boot2-jdk11      Boot 2 基座：Boot 2.7.18 / JDK 11
├── sample/boot2-jdk8       Boot 2 基座：Boot 2.7.18 / JDK 8
└── springboot4/feature     教程分支：Boot 4 新特性文章 + 可运行示例
```

三条约定：

1. `main` 只做导航，**不写代码**（保持 `git ls-tree main` 永远只有文档与许可文件）。
2. 任何教程 / 实验分支**一律从对应的脚手架分支拉取**（当前主用 `template`，老 JDK 用对应的
   `sample/*` 基座），不要在别的教程分支上继续叠功能，避免主题互相污染。
3. 新建教程分支后，回到本文件「分支说明」补一行，保持索引可查。

> `template` 承担日常改动后，`sample/boot4-jdk25` 原则上不再直接提交——只在「从基座同步」这类场景
> 才有改动。其余 `sample/*` 基座各自长期维护，改一边记得评估另一边。

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

## 分支说明

| 分支 | 定位 | 拉出自 | 拉出日期 | 说明 |
|------|------|--------|----------|------|
| `main` | 索引 | — | 2026-09 | 只有文档与许可文件，不放代码 |
| `template` | Boot 4 脚手架（当前主用） | `sample/boot4-jdk25` | 2026-10-05 | 与其上游内容一致，日常改动落在这里 |
| `sample/boot4-jdk25` | Boot 4 基座 | — | 2026-09 | `template` 的上游，通常只读 |
| `sample/boot3-jdk21` | Boot 3 基座 | `sample/boot4-jdk25` | 2026-10-05 | Boot 3.5.16 / JDK 21 |
| `sample/boot2-jdk8` | Boot 2 基座 | `sample/boot3-jdk21` | 2026-10-05 | Boot 2.7.18 / JDK 8 |
| `sample/boot2-jdk11` | Boot 2 基座 | `sample/boot2-jdk8` | 2026-10-05 | Boot 2.7.18 / JDK 11 |
| `springboot4/feature` | Boot 4 新特性教程 | `sample/boot4-jdk25` | 2026-10-02 | 《Spring Boot 4 到底新在哪》文章 + 示例 |

> 四个 `sample/*` 基座分支的历史在版本改造时被重写过，彼此互不构成祖先关系（唯一例外是 `template`
> 与 `sample/boot4-jdk25`，二者当前指向同一份内容）。所以跨分支搬运改动请走 `cherry-pick` 而不是 `merge`。

---
