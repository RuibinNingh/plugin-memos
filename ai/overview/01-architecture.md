# 01 · 项目架构

> 目标:用一张图 + 几段话,让 AI/新成员 5 分钟内知道请求是怎么流过 plugin-memos 的。

## 1. 全局架构图

```text
┌──────────────────────────────────────────────────────────────────┐
│                          Halo 2.25.0                             │
│                                                                  │
│  ┌──────────────┐    GET /moments          ┌─────────────────┐   │
│  │ Theme Router │ ───────────────────────▶ │ MomentRouter    │   │
│  │ (Thymeleaf)  │                          │ (theme route)   │   │
│  └──────────────┘                          └────────┬────────┘   │
│                                                     │            │
│                                                     ▼            │
│                                            ┌─────────────────┐   │
│                                            │ MomentFinderImpl│   │
│                                            │  (@Finder)      │   │
│                                            └────────┬────────┘   │
│                                                     │            │
│   Console /memos            ┌─────────────────┐     │            │
│   (Vue: MemosView)  ──axios──▶│ MemosProxyEndpoint│◀────┘            │
│   GET /apis/.../proxy/**    │ (CustomEndpoint) │  via WebClient   │
│                             └────────┬────────┘     │            │
│                                      │              │            │
│                                      ▼              ▼            │
│                              ┌──────────────────────────┐        │
│                              │ MemosClient (WebClient)  │        │
│                              │ + WebClientConfig(16MB)  │        │
│                              └────────────┬─────────────┘        │
│                                           │                      │
│   /moments/{name}  ◀──MomentCommentSubject──┤                      │
│   Halo 评论挂载点                          │                      │
└───────────────────────────────────────────┼──────────────────────┘
                                            │
                                            ▼
                                  ┌──────────────────┐
                                  │  memos :5230     │
                                  │  (外部自托管)    │
                                  └──────────────────┘
```

## 2. 数据流(三条主路径)

### 路径 A · Console 实时列表(反向代理)

1. 用户在 Halo Console 打开 `/memos` 页面(`ui/src/views/MemosView.vue`)。
2. 前端 `axiosInstance.get('/apis/api.memos.plugin.halo.run/v1alpha1/proxy/api/v1/memos')`。
3. Halo 把该路径交给 `MemosProxyEndpoint.endpoint()`(CustomEndpoint,挂在 `api.memos.plugin.halo.run/v1alpha1`)处理。
4. `proxy()` 取出 `setting.baseUrl/accessToken`,用 `WebClient.memosWebClient` 直接转发到 memos。
5. 响应通过 `exchangeToMono` 透传:状态码、Content-Type、ETag 全部保留;body 用 `collectList()` 暂存 DataBuffer 再交给 Spring Writer(避免流被截断)。

### 路径 B · 主题端 `/moments` 列表

1. 用户访问 `/moments`(`MomentRouter` 通过 `RouterFunction` 注册)。
2. `handlerFunction()` 调用 `momentList(request)` → `MomentFinder.listByTag(page, size, tag)`。
3. `MomentFinderImpl` 通过 `MemosClient.listPage()` 翻页拉数据,用 `walkToPage()` 走分页 token 到达目标页。
4. `MemosMapper.toMoment(MemoDto, baseUrl)` 把 memos DTO 转为 `Moment`,再装进 `MomentVo`。
5. `fillOwner()` 用 `ReactiveExtensionClient` 拉 Halo `User` 转 `ContributorVo`。
6. 模板 `moments.html` 渲染(`Map.of("moments", listResult, "tags", tags, "title", title, TEMPLATE_ID, "moments")`)。

### 路径 C · Halo 评论挂载

1. Halo 评论系统拿到 `Ref(group=moment.halo.run, kind=Moment, name=memos-xxx)`。
2. 遍历所有 `CommentSubject` bean,`MomentCommentSubject.supports(ref)` 返回 true。
3. `get(name)` 反向解出 uid → 调 `MemosClient.getMemo` → 实时拿正文。
4. `getSubjectDisplay(name)` 用 Jsoup 净化 + 截取 100 字 + 拼 `/moments/{name}` 链接。

## 3. 模块依赖图

```text
Plugin 入口层
  └── MemosPlugin ── 无依赖 ── 生命周期空

扩展定义层
  └── Moment (GVK) ──> MomentCommentSubject
                └──> MomentFinderImpl ──> MemosClient
                                     ──> MemosMapper
                                     ──> ReactiveSettingFetcher
                                     ──> ReactiveExtensionClient (User)

主题路由层
  └── MomentRouter ──> MomentFinder
                  ──> ReactiveSettingFetcher

代理层
  └── MemosProxyEndpoint ──> WebClient(memosWebClient)
                        ──> ReactiveSettingFetcher

基础设施
  └── config.WebClientConfig(Bean: memosWebClient)
  └── client.MemosClient(注入 memosWebClient)
```

## 4. 关键概念 / 命名

| 名称 | 含义 |
| --- | --- |
| `Moment` | 内存中的"瞬间"对象,GVK=moment.halo.run/v1alpha1,Moment。不入 SchemeManager。 |
| `MemoDto` | memos API 直接反序列化出的 DTO(`memos/{uid}` 形态)。 |
| `memos-{uid}` | `Moment.metadata.name` 的固定格式。`MemosMapper` 负责双向转换。 |
| `baseUrl` | 插件设置中的 memos 服务根地址(Docker 内常用 `http://172.17.0.1:5230`)。 |
| `accessToken` | 可选 Bearer token。仅在 memos 实例强制鉴权时配置。 |
| `PROXY_BASE` | 前端常量:`/apis/api.memos.plugin.halo.run/v1alpha1/proxy`。 |
| `/memos/proxy/file/**` | 公开原图代理路由,点开/下载原图走它(免 token)。 |
| `/memos/proxy/image/**` | 公开压缩图缓存路由,主题列表和 Console 缩略图优先走它。 |

## 5. 设计取舍(给后来人避坑)

| 取舍 | 理由 |
| --- | --- |
| **memo 数据不缓存** | 插件文档明确 memo 数据实时;缓存会让置顶/标签变更延迟显示,得不偿失。图片附件只缓存压缩派生文件。 |
| **不入 SchemeManager** | memos 数据是只读视图,落 Halo DB 反而带来一致性负担。 |
| **uid 大小写保留** | memos 0.29.1 的 uid 是大小写敏感的字符串,强行 lowercase 会撞名。 |
| **附件 URL 走公开代理** | 原图走 `/memos/proxy/file/**`,压缩图走 `/memos/proxy/image/**`;主题模板直出 `<img>` 必须免 token。 |
| **`Moment` 仍带 GVK** | 让 Halo 评论系统能挂载;它要的只是 group/kind/name 三元组。 |
| **WebClient 单独配 16MB 缓冲** | 单页 memos 附件多(图片+文件),默认 256KB 不够。 |
