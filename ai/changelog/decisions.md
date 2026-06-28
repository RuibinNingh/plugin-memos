# 决策记录

> 本文件按时间倒序追加,旧条目不删。

---

## 2026-06-28 · MomentFinder 列表 enrichment 必须保序

**背景**:`/moments` 偶发时间线乱序。实际抓取服务端 HTML 后,发现输出顺序已经是 `2026-05-16 -> 2026-04-22 -> 2026-05-04 -> 2026-04-26`,说明乱序发生在主题渲染前。

**原因**:`MomentFinderImpl` 从 memos 拿到 DTO 后,用 `flatMap(vo -> fillOwner(vo, config))` 异步补 Halo owner。`flatMap` 不保证原始元素顺序,owner 查询快慢不一致时会按完成顺序发出,导致列表乱序。

**决定**:所有列表路径中,异步补字段使用 `flatMapSequential(vo -> fillOwner(vo, config))`:

- `listAll()`
- `listBy(tag)`
- `listByTag(...)`

**理由**:

- memos API 返回顺序是当前主题端时间线的基础顺序。
- 主题模板只是遍历 `${moments.listResult}`,不负责排序。
- `flatMapSequential` 仍允许异步处理,但按源序发出元素。

**影响**:

- 以后新增评论数、点赞数、creator 等异步 enrichment 时,同样不能用普通 `flatMap` 直接接到列表流后面。
- 若产品上要求"无视置顶,完全按 createTime 倒序",需要在插件层显式排序,这是另一个行为决策。

---

## 2026-06-27 · 建立 AI 交接目录

**背景**:项目模块已经稳定(后端 8 个 Java 文件 + 一个 Vue 视图 + 构建),但缺一份给"接手者 / AI Agent"的统一说明,以避免每次重读全部源码。

**决定**:在仓库根新增 `ai/` 目录,按 6 个子目录(overview / backend / frontend / operations / troubleshooting / changelog)拆文档。

**理由**:

- overview 三篇(架构 / 运行环境 / 约定)先给"全局图",避免直接读源码浪费时间。
- backend / frontend 按"模块"颗粒度切,与 package / 文件一一对应。
- operations / troubleshooting 单独成集,符合"我有问题先来这里查"的入口习惯。
- changelog 留位置记"为什么这么写",降低后续维护成本。

**代价**:维护两份文档(代码注释 + ai/),信息可能漂移。

**影响**:

- 改 Java / Vue 文件时,**优先**同步 ai/ 对应模块文档。
- 新建文件 / 新模块时,先在 ai/ 加 README 条目再加代码。

---

## 既往决策(摘自源码注释与 git 历史,待持续补充)

### 架构层

- **`Moment` 不入 SchemeManager** —— 来自 `Moment.java` 注释:"Transient Moment type carrying only the GVK ... It is **not** registered in the SchemeManager and never persisted"。理由:memos 是只读视图,落 Halo DB 会带来一致性负担。
- **`@Finder("momentFinder")` 而非 `@Component`** —— `MomentFinderImpl` 注释:"`@Finder` is meta-annotated `@Service`, so no `@Component`"。避免 bean 重复注册。
- **`uid` 大小写保留** —— `MemosMapper` 注释:"Uid case is preserved (memos uids are case-sensitive)"。理由:memos 0.29.x 的 uid 是大小写敏感的字符串,强行 lowercase 会撞名。
- **附件 URL 走 `/memos/proxy/file/**`** —— `MemosProxyEndpoint` 注释:"Theme-side file rendering uses the public `/memos/proxy/file/**` route"。理由:主题模板直出 `<img>` 必须免 token。
- **WebClient 单独配 16MB 缓冲** —— `WebClientConfig` 注释:"A larger in-memory buffer is configured because a single memos page may carry many attachments"。
- **`exchangeToMono` 内 `collectList` 兜住 body** —— `MemosProxyEndpoint` 注释:"`exchangeToMono` releases the upstream ClientResponse as soon as the returned Mono emits, so a lazily-streamed body Flux would be cut off"。
- **`Stats` 永远 `empty()`** —— `MomentFinderImpl` 当前实现未接 memos 评论/点赞端点,占位。
- **匿名访问默认可用** —— `MemosClient` 注释:"Anonymous access already returns only PUBLIC memos; an optional access token is sent as a Bearer header when configured"。

### 构建层

- **`plugin.yaml` 复制到 `META-INF/halo/`** —— `build.gradle` 注释:"Halo 2.x plugin descriptor must live at `META-INF/halo/plugin.yaml` inside the jar"。
- **UI 用 corepack 调 pnpm** —— `ui/build.gradle` 注释:"corepack provides a working pnpm that matches the system Node version, unlike the node-gradle plugin's bundled pnpm (which requires Node 22)"。
- **不写 `id 'com.github.node-gradle.node'`** —— 同上,避免 node-gradle 抢着执行。

### 安全层

来自 `CLAUDE.md`,作为长期约束:

- **不硬编码 token / cookie / Authorization**。
- **不走"用户自定义 host" 代理**,仅信任 `setting.baseUrl`(由超管配置),限定 SSRF 面。
- **token 仅从 `memos-settings.accessToken` 读取**,默认空。

### 主题兼容层

- **`MomentVo` shape 与官方 plugin-moments 对齐** —— `MomentFinder` 注释:"Shape mirrors the official plugin-moments finder so xingdu templates work unchanged"。
- **`ANNO_EXCERPT = "thyuu_post_excerpt"`** —— 主题约定名称,改它意味着主题端模板要联动。
