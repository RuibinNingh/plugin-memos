# 后端模块地图

> 本目录是 `src/main/java/run/halo/memos/**` 的**模块化说明**。每个子文件 = 一个 Maven 子模块职责单元,带"入口类 / 干什么 / 怎么改"三段式。

## 模块清单

| 文件 | 入口类 | 职责一句话 |
| --- | --- | --- |
| [module-plugin-entry.md](./module-plugin-entry.md) | `MemosPlugin`, `ModelConst` | 插件入口 + 模板常量 |
| [module-domain-model.md](./module-domain-model.md) | `Moment`, `MomentVo`/`ContributorVo`/`Stats`/`MomentTagVo`, `MemosMapper` | 领域对象 + DTO 转换 |
| [module-memos-client.md](./module-memos-client.md) | `MemosClient` | memos API 的 WebClient 封装 |
| [module-finder.md](./module-finder.md) | `MomentFinder`, `MomentFinderImpl` | 主题端 Finder(`/moments` 列表/详情/标签) |
| [module-comment-subject.md](./module-comment-subject.md) | `MomentCommentSubject` | 接入 Halo 评论体系 |
| [module-theme-router.md](./module-theme-router.md) | `MomentRouter` | 主题端 `/moments` 路由 |
| [module-proxy-endpoint.md](./module-proxy-endpoint.md) | `MemosProxyEndpoint` | Console `/apis/.../proxy/**` 代理 + 公开文件代理 |
| [module-config-webclient.md](./module-config-webclient.md) | `WebClientConfig` | 专用 WebClient Bean(16MB 缓冲) |

## 谁依赖谁

```text
MemosPlugin  ───────────────────────────────────────────  (无显式依赖)
ModelConst   ───────────────────────────────────────────  (纯常量)

MomentCommentSubject ──> MemosClient, MemosMapper
MomentFinder / Impl  ──> MemosClient, MemosMapper,
                         ReactiveExtensionClient (User),
                         ReactiveSettingFetcher
MomentRouter          ──> MomentFinder, ReactiveSettingFetcher
MemosProxyEndpoint    ──> WebClient(memosWebClient), ReactiveSettingFetcher
MemosClient           ──> WebClient(memosWebClient)
MemosMapper           ──> commonmark Parser/Renderer(无 Spring 状态)

WebClientConfig       ──> (暴露 memosWebClient Bean)
```

## 修改前的 3 个自检

1. **改的类是不是"无状态"?** 除 `MomentRouter`/`MomentFinderImpl` 持有 Spring 注入依赖外,`MemosMapper` / `MemosClient` 都是 stateless bean。
2. **改的字段是否被 `Moment` 内存结构暴露?** 改 `Moment` 的 schema 等于改评论挂载契约,先看 `module-comment-subject.md`。
3. **改的路径是否被前端硬编码?** `MemosProxyEndpoint` 改了 `groupVersion` / `ROUTE_PREFIX`,前端 `MemosView.vue` 里的 `PROXY_BASE` 也要同步。