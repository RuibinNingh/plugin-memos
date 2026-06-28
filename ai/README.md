# AI 交接手册 · plugin-memos

> 这是一份**给 AI Agent / 新成员**看的"项目地图 + 交接说明",目的是让接手者用最少时间理解 plugin-memos 是什么、由哪些模块组成、哪里能改、哪里不能改。

---

## 1. 这份手册怎么读

| 如果你是…… | 先看这些文件 |
| --- | --- |
| 新接手的 AI / 工程师 | 本文件 → [overview/01-architecture.md](./overview/01-architecture.md) → [overview/02-runtime-env.md](./overview/02-runtime-env.md) |
| 只改后端 Java | [overview/01-architecture.md](./overview/01-architecture.md) → [backend/README.md](./backend/README.md) → 各子模块文档 |
| 只改 Console UI | [frontend/README.md](./frontend/README.md) |
| 需要部署/排障 | [operations/README.md](./operations/README.md) → [troubleshooting/README.md](./troubleshooting/README.md) |
| 想知道"为什么这么写" | [changelog/README.md](./changelog/README.md) + 源码内注释 |

---

## 2. 项目一句话定义

`plugin-memos` 是 **Halo 2.x 插件**,把自托管 [memos](https://usememos.com/) 的公共动态**实时反向代理**到 Halo 端,提供:

1. **主题端(`/moments`)** — 通过 Finder + Theme Router,让主题(thyuu-xingdu 等)用 Thymeleaf 模板渲染瞬间列表/详情。
2. **Console 端(`/console/memos`)** — 一个 Vue 3 页面,实时展示 memos 公共动态,支持时间线 / 瀑布流切换和分页加载。

**核心特征**:

- **不持久化**:`Moment` 只是 GVK 标注的内存对象,从未注册到 SchemeManager,从未落盘。
- **不缓存**:每次请求都直连 memos(`MemosClient`),所有数据**实时**拉取。
- **不做鉴权代理**:受保护 API 仅在用户填了 `accessToken` 时透传 `Authorization: Bearer …`。

---

## 3. 目录树(代码层)

```text
plugin-memos/
├── src/main/java/run/halo/memos/
│   ├── MemosPlugin.java              # 插件主类(空生命周期)
│   ├── Moment.java                   # GVK=moment.halo.run/Moment 内存对象
│   ├── MomentCommentSubject.java     # 接入 Halo 评论体系
│   ├── MomentRouter.java             # 主题路由 /moments
│   ├── ModelConst.java               # 模板常量
│   ├── config/WebClientConfig.java   # 专用 WebClient(16MB 缓冲)
│   ├── endpoint/MemosProxyEndpoint.java  # Console /api 反向代理 + 公开文件代理
│   ├── finders/MomentFinder.java          # Finder 接口
│   ├── finders/impl/MomentFinderImpl.java # Finder 实现(实时拉 memos)
│   ├── vo/{MomentVo,ContributorVo,MomentTagVo,Stats}.java
│   ├── client/MemosClient.java           # memos API 客户端
│   └── sync/MemosMapper.java             # MemoDto ↔ Moment 转换器
├── src/main/resources/
│   ├── plugin.yaml                  # 插件清单(GVK、依赖、setting 名)
│   ├── logo.png
│   └── extensions/settings.yaml     # memos-settings 表单
├── ui/                              # Console 前端(Vue 3 + Vite)
│   ├── package.json
│   ├── vite.config.ts
│   ├── build.gradle                 # 通过 corepack 调 pnpm
│   └── src/
│       ├── index.ts                 # definePlugin 入口,挂 /memos 路由
│       └── views/MemosView.vue      # 唯一视图
├── build.gradle                     # Java 21, Halo 2.25.0 平台
├── settings.gradle                  # include 'ui'
└── docs/vendor/memos-api/README.md  # Memos 0.29.1 API 摘要
```

---

## 4. AI 文件夹结构

```text
ai/
├── README.md                        # 本文件
├── overview/                        # 全局认知
│   ├── 01-architecture.md           # 架构图、模块依赖、关键流程
│   ├── 02-runtime-env.md            # 运行环境/端口/容器
│   └── 03-conventions.md            # 命名、注释、提交规范
├── backend/                         # 后端 Java 模块
│   ├── README.md                    # 后端地图
│   ├── module-plugin-entry.md       # MemosPlugin / ModelConst
│   ├── module-domain-model.md       # Moment / VO / Mapper
│   ├── module-memos-client.md       # MemosClient(WebClient 包装)
│   ├── module-finder.md             # MomentFinder / Impl
│   ├── module-comment-subject.md    # MomentCommentSubject
│   ├── module-theme-router.md       # MomentRouter(/moments 路由)
│   ├── module-proxy-endpoint.md     # MemosProxyEndpoint(代理)
│   └── module-config-webclient.md   # WebClientConfig
├── frontend/                        # 前端 Vue 模块
│   ├── README.md
│   ├── module-entry.md              # src/index.ts
│   ├── module-view.md               # MemosView.vue
│   └── module-build.md              # vite / build.gradle / pnpm
├── operations/                      # 部署 / 配置 / 运维
│   ├── README.md
│   ├── deploy-dev.md                # 开发 Halo 起服务
│   ├── deploy-build.md              # 构建可发布 jar
│   ├── settings-form.md             # 插件设置项含义
│   └── memos-integration.md         # 与本地 memos 容器对接
├── troubleshooting/                 # 排障手册
│   ├── README.md
│   ├── proxy-404-502.md             # 代理不通
│   ├── theme-render-fail.md         # /moments 渲染失败
│   └── memos-api-changes.md         # memos API 升级导致的字段漂移
└── changelog/                       # 决策/变更记录(给后来人"考古")
    ├── README.md
    └── decisions.md
```

---

## 5. 关键不变量(改了要谨慎)

下列约定是当前实现成立的前提,**任何模块若违反,必先在 `changelog/decisions.md` 留一笔**:

1. **`Moment` 不入 SchemeManager** — 它只为评论系统提供 GVK 锚点,被 `MomentCommentSubject.get(name)` 现拉现转。
2. **uid 大小写敏感** — `memos-{uid}` 必须能反向解析出 `memos/{uid}` 才能 GET 单条;`MemosMapper.toMomentName / uidFromMomentName` 这对函数不要改语义。
3. **附件 URL 走 `/memos/proxy/file/**`** — 该公开路由是 `MemosProxyEndpoint` 提供的,改路径会断图。
4. **匿名默认即可** — memos 公开 API 默认只返回 PUBLIC,accessToken 仅在实例强鉴权时配置;不要"为安全强制要求 token"。
5. **`build.gradle` 把 `plugin.yaml` 复制到 `META-INF/halo/`** — Halo 2.x jar 规范要求,删了会找不到插件。
6. **前端经由 `/apis/api.memos.plugin.halo.run/v1alpha1/proxy/**` 走 Halo 转发** — Vue 端不要直接打 memos 地址(否则 Docker 内网解析失败)。

---

## 6. 一行开始写代码

```bash
# 启开发实例
sg docker -c './gradlew haloServer'

# 只改 Java 时
./gradlew :classes -x pnpmInstall -x processUiResources

# 只改 UI 时
( cd ui && corepack pnpm@9.15.9 install && pnpm run dev )
```