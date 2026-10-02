# SpringVortexDemo

> 本仓库是 Spring Boot 4 脚手架仓库。**`main` 分支只做导航，不放任何代码**——
> 它是别人 clone 下来看到的第一个分支，作用是告诉你去哪个分支拿什么。

## 分支一览

| 分支 | 用途 | 内容 | 状态 |
|------|------|------|------|
| **`main`** | 默认分支，纯索引 | 仅本 README + `LICENSE` + `.gitignore` | 稳定 |
| **`template`** | **基座**：Spring Boot 4 脚手架 | 完整可运行工程（零业务耦合），含 README 全套文档 | 稳定 |

### `template` 分支包含什么

一份开箱可用的单体后端骨架，用来派生新项目：

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
- **质量门禁**：JaCoCo + 112 个测试用例，行/分支覆盖率 100%
- **工程规范**：`.editorconfig` / `.gitattributes` / MIT `LICENSE`

详细说明、升级注意事项、以及**派生新项目的完整步骤**都在 `template` 分支的 `README.md` 里。

## 分支策略

```
main                          ← 你在这里：纯索引，无代码
└── template                  基座：Spring Boot 4 脚手架，零业务耦合
    ├── xxxxx-mysql           教程分支：集成 MySQL
    ├── xxxxx-redis           教程分支：集成 Redis
    └── …                     每个教程分支只讲一个主题
```

三条约定：

1. `main` 只做导航，**不写代码**（保持 `git ls-tree main` 永远只有文档与许可文件）。
2. 任何教程 / 实验分支**一律从 `template` 拉取**，不要在别的教程分支上继续叠功能，避免主题互相污染。
3. 新建教程分支后，回到本文件「新分支登记处」补一行，保持索引可查。

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
```

> 注意：远端 `origin/HEAD` 指向 `main`。从 `template` 提 PR / push 时**要显式指定目标分支**，
> 别默认合到 `main`。

## 新分支登记处

每新增一个教程分支，在这里追加一行：

| 分支 | 主题 | 基于 | 创建日期 | 说明 |
|------|------|------|----------|------|
| `template` | Spring Boot 4 脚手架基座 | — | 2026-09 | 派生新项目的起点 |
| _（待补充）_ | | | | |

---
