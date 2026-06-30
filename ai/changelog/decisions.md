# 决策记录

> 本文件按时间倒序追加,旧条目不删。

---

## 2026-06-29 · 插件版本从 1.0.0-SNAPSHOT 升到 1.1.0

**背景**:`build.gradle` 之前没有显式 `version`,依赖 Gradle 默认 `1.0.0-SNAPSHOT`,与生产 jar 形态区分不开,无法在插件 API / Console 中识别"本轮带缓存的版本"。

**决定**:`build.gradle` 加 `version = '1.1.0'`,移除 SNAPSHOT 后缀。

**理由**:
- 与生产常见发布版本号一致,便于在 Halo Console 列表、API `spec.version` 中一眼看出。
- 后续若有 v1.1.x 修复,直接递增;破坏性变更走 v1.2.0+。

**影响**:
- 构建产物的 jar 名称会从 `plugin-memos-1.0.0-SNAPSHOT.jar` 变为 `plugin-memos-1.1.0.jar`。
- 部署到开发/生产时,**旧的 `-SNAPSHOT.jar` 要先清掉再放新 jar**,否则 Halo 可能仍按旧 jar 加载。
- Halo 的 Plugin resource 不会自动同步 `spec.version` 字段;需要从 Console 或 API 刷新资源 / 重启 Halo,让版本号反映到 `status`。

---

---

## 2026-06-29 · 1.1.2 · 正文剥离内联 hashtag(标签去重)

**背景**:memos 正文里 `#标签` 是文本的一部分(常见在末尾,如 `#AI #思考`);插件又把同样的标签抽进 `spec.tags`,星度主题在 `moment-item.html` footer(`.moment-tags`)再渲染一遍可点击链接。结果每条 memo 的标签在正文和 footer 各出现一次,视觉重复。

**决定**:`MemosMapper.buildSpec` 在渲染 `content.html` 前,先用 `stripInlineTags(raw, memo.getTags())` 把正文里与 `spec.tags` 对应的 `#标签` 纯文本去掉;`content.setRaw` 仍保留原始 markdown(带标签)。

**实现要点**:
- 按 tag 长度降序、字面 `String.replace("#"+tag)` 替换,避免 `#foo` 误伤 `#foobar`,且对 CJK 标签安全(不依赖 `\w` 词边界)。
- 替换后清理行末空格、折叠 3+ 连续空行、`strip()` 去首尾空白,避免留下空 `<p>` / 多余 `<br>`。
- 只影响渲染后的 `html`;`raw`、`excerptOf`(本就用正则去 `#`)、footer 标签、顶部筛选导航都不受影响。

**影响**:
- 正文不再出现 `#AI #思考` 文本;标签只在 footer(可点击)+ 顶部平铺筛选出现。
- 若用户在正文中间内联使用 `#标签` 描述,也会被去掉——符合"标签统一在 footer 展示"的取向。

---

## 2026-06-29 · 1.1.1 · 主题端 memos 内容换行修复

**背景**:memos 0.29.x 编辑器里回车生成的是单 `\n`;commonmark 默认把单 `\n` 视作"软换行"(渲染为空格),导致主题端 `/moments` 上多行 memo(常见用法)被压成一段。Console 端 `marked.parse` 默认也是软换行,行为一致地不对。

**决定**:
- 后端 `MemosMapper.renderMarkdown` 新增 `toHardBreaks` 预处理:把不在空行内的 `\n` 升级为 commonmark 的硬换行 `  \n`(末尾两空格 + 换行 → `<br>`);不引入 `commonmark-ext-gfm` 等额外依赖。
- 前端 `MemosView.renderMarkdown` 改用 `marked.parse(content, { async: false, breaks: true })`,与后端行为对齐。

**理由**:
- mappers 内做"行末两空格"的轻量级预处理比引入新的 commonmark extension jar 简单、零风险;`MomentContent.raw` 仍是原始 markdown,`excerptOf` 与 `MomentCommentSubject` 的 Jsoup 净化不受影响。
- Console `breaks: true` 与 GFM 兼容,符合 memos 编辑器预期。

**影响**:
- 历史 memos:`\n` 现在显示为 `<br>`,无段落分隔需求的单行 memo 视觉无变化。
- 已有主题 `MomentContent.html` 字段结构没变(仍是 `<p>...<br>...</p>`),模板无需改。
- Console `MemosView.vue` 输出会多 `<br>`,主题 `.memo-content` 的 SCSS 已设 `word-break: break-word`,溢出与换行安全。

---

## 2026-06-29 · 图片附件引入本地压缩派生缓存

**背景**:Memos 与 Halo 可以本地互通,真正慢的是博客服务器到访问者浏览器这一段。直接让主题页加载 Memos 原图会造成首屏慢、流量大。

**决定**:

- 保留原图 API:`/memos/proxy/file/**`。
- 新增压缩图 API:`/memos/proxy/image/**`。
- 压缩图缓存写入 Halo 插件根目录下的 `memos/cache/images/`,不写入 Memos 数据目录。
- JPEG/JPG 输出 JPEG;PNG 无透明通道输出 JPEG;PNG 有透明通道保留 PNG;GIF/SVG/APNG 回退原图。
- Console 新增"刷新图片缓存"按钮,调用 `/apis/console.api.memos.plugin.halo.run/v1alpha1/cache/refresh`。

**影响**:

- "不缓存"的边界改为"不缓存 memo 数据,只缓存图片派生文件"。
- `MemosMapper` 与 `MemosView.vue` 都参与图片 URL 选择,格式规则变化时要同步。
- 删除或改名 `/memos/proxy/file/**` 会破坏原图查看和压缩失败回退。

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
- **原图 URL 走 `/memos/proxy/file/**`** —— `MemosProxyEndpoint` 提供公开原图路由。理由:点开/下载原图和压缩失败回退都依赖它。
- **压缩图 URL 走 `/memos/proxy/image/**`** —— `MemosImageEndpoint` 提供公开压缩图路由。理由:主题列表/Console 缩略图需要减少博客到浏览器传输体积。
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
