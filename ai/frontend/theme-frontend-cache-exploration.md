# 主题前端探索报告 · 缓存更新对 `/moments` 的影响

> 报告类型:探索性 / 摸底盘
> 范围:插件 `plugin-memos` 引入图片压缩缓存后,**对主题(thyuu-xingdu 等)在 `/moments` 与 `/moments/{name}` 页面**的实质影响、可做的优化、必避的坑。
> 立场:不动主题代码也能跑起来,但有 6 处可拿的优化、1 处待办、3 处注意事项。

---

## 0. 一句话结论

主题端 **不需要改 Thymeleaf 模板就能用上压缩缓存**——`MemosMapper` 已在 `toMoment` 时把 `MomentMedia.url` 改写为 `/memos/proxy/image/attachments/{uid}/{filename}`。但要拿到最佳首屏体验,主题需要做 4 件事:消费 `medium[]` 而不是 `content.html` 里的 `<img>`、对大图设 `loading=lazy` 与 `srcset/sizes`、点开原图的链接要显式走 `/memos/proxy/file/`、并把对 404/500 的兜底文案对齐新路由。

---

## 1. 这次缓存更新到底改了什么(代码层)

| 新增/改动 | 位置 | 对主题的实质影响 |
| --- | --- | --- |
| `cache/ImageUrlSupport.java` | 新增 | 集中决策:大 JPEG/PNG 走 `/memos/proxy/image/`,否则走 `/memos/proxy/file/`。`MemosMapper` 调它,主题不需要再判断。 |
| `cache/ImageCacheService.java` | 新增 | 真做压缩:`w{width}-q{quality}-{hash16}.jpg` / `.png`,写到 `<pluginsRoot>/memos/cache/images/attachments/{uid}/`。缩放用 BICUBIC;PNG 有 alpha 保留 PNG,无 alpha 走 JPEG 质量 86+。 |
| `cache/ImageCacheSettings.java` + `ImageCacheProperties.java` | 新增 | 把 `memos-settings` 的 8 个新字段(`imageCacheEnabled/Width/Quality/MinBytes/WarmupEnabled/WarmupPageSize/WarmupMaxPages/MaxAgeDays`)包成 record。 |
| `cache/ImageCacheWarmupService.java` + `config/SchedulingConfig.java` | 新增 | `@Scheduled(initialDelay=60s, fixedDelay=30min)` 拉最近 `warmupPageSize×warmupMaxPages` 条 memos,串行预热,同时清理超过 `maxAgeDays` 的旧文件。 |
| `endpoint/MemosImageEndpoint.java` | 新增 | 公开 `GET /memos/proxy/image/attachments/{uid}/{filename}`;支持 `?w=…&q=…` 覆盖;命中走 30 天 immutable 缓存 + `X-Memos-Image-Cache: HIT|MISS` 头;不命中/出错 302 回退到 `/memos/proxy/file/` 同路径。 |
| `endpoint/MemosCacheEndpoint.java` | 新增 | Console 用的 `GET/POST /apis/console.api.memos.plugin.halo.run/v1alpha1/cache/{status,refresh,clear}`。**主题不直接消费**,只是知晓这条 URL 的存在。 |
| `sync/MemosMapper.java` | 改 | `resolveUrl(AttachmentDto)` → `ImageUrlSupport.displayUrl(att)`。**这是主题受益的关键一跳**。 |
| `extensions/settings.yaml` | 改 | 多 8 个 `imageCache*` 字段(默认 enabled=true, width=1600, quality=82, minBytes=300KB, warmup=开, maxAge=30 天)。 |
| `ui/src/views/MemosView.vue` | 改 | 顶部加"刷新图片缓存"按钮,调用 `POST /apis/.../v1alpha1/cache/refresh` 并展示 `created/hits/failed`。**与主题无关**。 |

不变的边界(也是 `ai/changelog/decisions.md` 这次再次强调的):

- `Moment` 仍不入 SchemeManager,仍只承担 GVK 锚点。
- `MemosClient` 仍每次直连 memos,**memo 数据 / 列表 / 标签不缓存**。
- `/memos/proxy/file/**` 原图路由保留且继续提供(点开原图 + 压缩失败兜底)。
- 主题路由仍是 `/moments`、`/moments/page/{page:\\d+}`、`/moments/{momentName:\\S+}`。

---

## 2. 主题端契约(未变,只是确认)

主题模板能拿到的根对象仍是 `MomentVo`,在 `ai/backend/module-domain-model.md` 与 `ai/backend/module-finder.md` 里都写了。这里只列与本次缓存变更**最相关**的几条:

```text
${momentFinder}
  ├─ listAll()             : Flux<MomentVo>
  ├─ list(page,size)       : Mono<ListResult<MomentVo>>
  ├─ listByTag(page,size,tag) : Mono<ListResult<MomentVo>>     ← /moments 列表
  ├─ get(name)             : Mono<MomentVo>                    ← /moments/{name} 详情
  ├─ listAllTags()         : Flux<MomentTagVo>
  └─ listBy(tag)           : Flux<MomentVo>

MomentVo {
  metadata: { name = "memos-{uid}", annotations: { thyuu_post_excerpt, memos.plugin.halo.run/pinned } },
  spec: MomentSpec {
    content: MomentContent { raw, html, medium: List<MomentMedia> },
    releaseTime: Instant,
    visible: PUBLIC,
    owner: String,
    tags: Set<String>,
    approved: Boolean
  },
  owner: ContributorVo { name, avatar, bio, displayName },
  stats: Stats { upvote, totalComment, approvedComment }    ← 永远 empty()
}
```

`MomentMedia` 的字段(注意:**没有 originalUrl**):

```text
type: PHOTO | VIDEO | POST | AUDIO
url: String         ← 已经被 mapper 改写为 /memos/proxy/image/** 或 /memos/proxy/file/**
originType: String  ← memos 上传的原始 MIME
```

> 主题端**没有** `getExternalLink()` 这种字段。如果主题想拿 memos 用户在附件里填的"外部图床直链",目前**拿不到**——`MemosMapper` 把它直接当 `url` 用了,等于隐藏了"原图链接 vs 压缩图链接"这一信息。这是改进点,见 §7。

---

## 3. 主题端契约(这次"暗改")

### 3.1 `medium[].url` 已经是压缩图

来源:`MemosMapper.resolveUrl → ImageUrlSupport.displayUrl`:

```java
public static String displayUrl(AttachmentDto attachment) {
    if (StringUtils.hasText(attachment.getExternalLink())) {
        return attachment.getExternalLink();          // ① 外部图床直链优先
    }
    if (shouldUseCachedImage(attachment)) {
        return optimizedUrl(attachment);              // ② 走 /memos/proxy/image/**
    }
    return originalUrl(attachment);                   // ③ 其余走 /memos/proxy/file/**
}
```

`shouldUseCachedImage` 的判断:

- 必须是 `image/jpeg` / `image/jpg` / `image/png`;其余(GIF / SVG / APNG / video)直接走原图。
- 没有"按大小阈值"判断——`imageCacheMinBytes` 只在 `ImageCacheService` 后端生成阶段生效,**不影响主题拿到的 URL**。所以主题对所有可压缩的图都拿到的是 `/memos/proxy/image/...`;但被压缩的文件**只有超过 300KB 的那部分**——小图访问 `/memos/proxy/image/...` 时,后端会直接 302 回退到 `/memos/proxy/file/...`,对浏览器透明。

### 3.2 markdown 里的图片**没有**被改写

`MemosMapper.renderMarkdown` 走 commonmark 0.22.0,**不**做图片 URL 重写。所以如果用户在 memos 里写 `![xxx](https://memos-host:5230/file/attachments/abc/photo.jpg)`,`content.html` 里的 `<img src=...>` 还是 memos 原 URL,不会被压缩。

**含义**:主题若想"所有图片都走压缩",必须**优先用 `${spec.content.medium}`**,而不是 `${spec.content.html}`。

### 3.3 Console 端的对照参考(只为对齐,不强制)

`ui/src/views/MemosView.vue` 已经做了:

```ts
function attachmentUrl(att)        // 原图 → 打开/下载走这个
function displayAttachmentUrl(att) // 展示图 → 缩略图走这个,带 ?image cache 判定
```

主题端没有 `MomentMedia.originalUrl` 字段,要么在主题里自己复制同样的判定,要么直接用 `medium.url`(见 §5.1 与 §7)。

---

## 4. 主题端必须知道的事实

### 4.1 公开路由(主题页可直接 `<img src=...>` 引用)

| 路径 | 用途 | 注意事项 |
| --- | --- | --- |
| `/memos/proxy/image/attachments/{uid}/{filename}` | 压缩图(可带 `?w=…&q=…` 覆盖) | 30 天 `Cache-Control: public, max-age=…, immutable`,有 `X-Memos-Image-Cache: HIT\|MISS` 头。 |
| `/memos/proxy/file/attachments/{uid}/{filename}` | 原图(免 token) | 直接透传 memos,**没有** `Cache-Control` 头,浏览器默认行为。 |
| `/memos/proxy/file/...` 其它子路径 | 也走 `MemosProxyEndpoint` 的 `proxy` handler | 用于视频、文件附件等。 |

主题拿到的 `medium.url` 已经是 `PROXY_BASE + "/image/" + attachmentPath` 或 `"/file/" + attachmentPath`,**直接 `src` 即可**。

### 4.2 缓存目录与失效

- 缓存文件落在 `<pluginsRoot>/memos/cache/images/attachments/{uid}/w{width}-q{quality}-{digest16}.{ext}`。
- `width` 默认 1600,`quality` 默认 82(已 clamp 到 [50,95]),`minSourceBytes` 300KB。
- 后台每 30 分钟预热并清理超过 `imageCacheMaxAgeDays`(默认 30 天)的旧文件。
- 主题不需要自己清理;**不要**在主题里写到 `memos/cache/...`。

### 4.3 公共/原图路由的 302 行为

- `/memos/proxy/image/...` 在以下情况**会 302**:
  - `imageCacheEnabled = false`;
  - 文件 < `imageCacheMinBytes`;
  - 源图是 GIF / SVG / APNG / 无法 decode;
  - 源图 PNG 但路径中带 `acTL` chunk(动图判定);
  - 任何 `onError` 兜底。
- 主题若用 `fetch`/`axios` 自己拉图,记得 follow redirect;`<img>` 默认会 follow。

### 4.4 渲染差异(主题如果用了 `${spec.content.html}` 就要小心)

- 后端 `MomentContent.html` 由 `commonmark` 渲染;前端 Console 的 `MemosView` 走 `marked` 渲染。
- 同一段 markdown 在主题页(后端渲染)和 Console(前端渲染)里**HTML 不完全一致**(`escaping`、列表处理、换行等)。这是插件一直存在的差异,不是本次缓存引入的。

---

## 5. 主题端建议做的事(可勾选清单)

> 所有项都是"加分项"——`/moments` 默认就能跑。这里按收益从高到低排。

### 5.1 优先用 `${spec.content.medium}`,不要抠 `${spec.content.html}` 里的 `<img>`

原因(再强调):`medium[].url` 已被 mapper 改写为压缩图;`content.html` 里的图片是 memos 原 URL,绕过了压缩。

推荐模板片段:

```html
<div class="moment-media" th:if="${moment.spec.content.medium != null}">
  <a th:each="m : ${moment.spec.content.medium}"
     th:if="${m.type.name() == 'PHOTO'}"
     th:href="${m.url}" target="_blank" rel="noopener">
    <img th:src="${m.url}" th:alt="${m.originType}"
         loading="lazy" decoding="async" />
  </a>
</div>
```

### 5.2 加 `srcset` 与 `sizes` 走不同 width 的缓存

`/memos/proxy/image/...` 支持 `?w=…&q=…` 覆盖。后端会把 width 限制在 [1, 2560],quality 限制在 [50, 95]。

```html
<img
  th:src="${m.url}"
  th:srcset="|${m.url}?w=480 480w, ${m.url}?w=960 960w, ${m.url}?w=1600 1600w|"
  sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
  loading="lazy" decoding="async"
/>
```

注意:

- 同一个原始 URL 通过不同 `?w=` 会得到**不同的缓存文件**(`digest` 不一样),所以浏览器命中后**不会跨 srcset 复用**——这是缓存细节,不是 bug。
- 想要 1 个文件多分辨率,改用 `<picture>` + `<source type="image/avif" srcset="...">`?当前后端只出 JPEG/PNG,不输出 AVIF/WebP。这是改进点,见 §7。

### 5.3 点开原图的链接,显式走 `/memos/proxy/file/`

如 §3.1 与 §2 末所述,`medium.url` 在"该图被压缩"时是 `/memos/proxy/image/...`。如果主题想"点击看原图",且没有 §7 提到的 `originalUrl` 字段,目前只能让 `<a href="${m.url}">` 走压缩图(< 300KB 的图其实会自动 302 到 `/file/...`,行为可接受);若坚持要点开看原图,主题里需要根据 `m.originType` 或 `m.url` 自带 `?image` 模式判定后,改成 `/memos/proxy/file/...`。

短期可接受的折中:**点击 = 走压缩图**。原图入口放在 Console 端或脚手架页(`/moments/{name}?full=1`)。

### 5.4 `loading=lazy` / `decoding=async` / 瀑布流 `aspect-ratio`

- `loading="lazy"` 在首屏瀑布流上**收益大**;但要小心,瀑布流的 `aspect-ratio: 1/1` 会强制所有图按 1:1 渲染,长图会裁切。`thyuu-xingdu` 若要做 Pinterest 风,可考虑 `aspect-ratio: auto` + `object-fit: cover`,或后端给一份"按原比例"的小图。
- `decoding="async"` 让浏览器主线程不被大图 decode 阻塞。

### 5.5 列表分页的"占位"建议

后端 `MomentFinderImpl.walkToPage` 在 memos 报错时返回空页(`onErrorResume`),不会 500。**主题可以放一个"暂时没有更多 / memos 不可达"占位**,而不是循环请求"加载更多"。

### 5.6 `data-cache-hit` 调试钩子

后端压缩图响应带 `X-Memos-Image-Cache: HIT|MISS`。主题若要做"调优可视化",可以读这头(需要 fetch);否则忽略。

### 5.7 主题设置项透传

如果 thyuu-xingdu 想给作者一个"关闭压缩缓存"开关,直接读 `${site.configMap}` 即可(见 `plugin.yaml` 的 `configMapName: memos-configMap`),无需扩展 Halo setting。但**建议**改设置走 Halo 主题设置(`theme.yaml`),而不是直接吃插件 setting——避免耦合。

---

## 6. 主题端**不要**做的事

| 不要 | 原因 |
| --- | --- |
| 在主题里硬编码 `/memos/proxy/file/...` 拼字符串 | 路由/路径前缀是**插件契约**,改了主题要同步;直接用 `medium.url`。 |
| 在主题里写自己的图片压缩逻辑 | 重复造轮子,且压缩后写不到 `<pluginsRoot>/memos/cache/`。 |
| 在主题里读 `${moment.content.html}` 然后用正则抠 `<img src>` | markdown 里的图是 memos 原 URL,不会压缩;优先 `medium[]`。 |
| 在主题里做"加载更多时调 `/memos/proxy/file/...` 预热 | 没有意义;`ImageCacheWarmupService` 已经每 30 分钟拉一遍。 |
| 假设 `Stats` 有真实数据 | 永远是 `Stats.empty()`;`totalComment`/`upvote` 永远 0。 |
| 假设 `${moment.content.raw}` 是 HTML | 它是 markdown 原文,要在主题里渲染 HTML 就用 `${moment.content.html}`(后端 commonmark 已渲染)。 |
| 把缓存目录写到 `src/main/resources/static` | 缓存文件应在运行时写到 `<pluginsRoot>/memos/cache/`,不要混进 jar。 |

---

## 7. 主题端可见的"未知 / 待办"

| 编号 | 项 | 说明 | 影响主题的等级 |
| --- | --- | --- | --- |
| T-1 | `MomentMedia` 没有 `originalUrl` 字段 | 想"点击看原图"的主题拿不到原 URL,要么自己拼 `/memos/proxy/file/...`,要么改 mapper 加字段。 | 中 |
| T-2 | Markdown 内联图片不压缩 | `content.html` 里的 `<img src="memos原URL">` 仍直连 memos。 | 中(若主题靠 markdown 展示图才相关) |
| T-3 | 没有 AVIF / WebP 输出 | 后端只输出 JPEG / PNG,`picture/srcset` 走现代格式失效。 | 低 |
| T-4 | 缓存文件命名是 `w{width}-q{quality}-{hash16}.{ext}` | 一个原图最多生成 2 个文件(同 width 不同 ext);但 `?w=480` 与 `?w=960` 是**不同**文件,可能撑大缓存。 | 低 |
| T-5 | `X-Memos-Image-Cache` 头不可被主题读到(`<img>` 没有暴露) | 想做"调优可视化"得自己 fetch。 | 低 |
| T-6 | 没有 `Cache-Control: stale-while-revalidate` | 30 天 immutable,意味着缓存 URL 改了 width/quality 后,旧 URL 在浏览器里**继续命中**;改 `imageCacheWidth` 后需要等浏览器刷新本地缓存。 | 低 |
| T-7 | 主题端没有"清除缓存"按钮 | 改设置后,旧尺寸的缓存文件要等 `maxAgeDays` 自然过期(默认 30 天);急的话只能在 Console 调 `POST /apis/.../cache/clear`。 | 中 |
| T-8 | `imageCacheMinBytes` 与"按大小判断"逻辑分散 | 后端生成阶段用一次(`>minBytes` 才压缩),但 `ImageUrlSupport.shouldUseCachedImage` 不看大小——所有 JPEG/PNG 都拿 `/image/` URL,然后被 302 回退。**这是当前实现,行为正确,但耦合点在两处**。 | 低(主题无感) |

---

## 8. 给 thyuu-xingdu 的具体建议(最小动作)

按"必做 / 应做 / 可做"三档列:

### 必做(下次主题发版前)

- [ ] 复查 `moments.html` / `moment.html`,**确认图片来源是 `${moment.spec.content.medium}`**,而不是 `${moment.spec.content.html}` 中的 `<img>`。
- [ ] 复查 `<img>` 是否带 `loading="lazy"` 与 `decoding="async"`(列表密度大时省首屏)。
- [ ] `<a href>` 点开原图时,若不想显示压缩图,在主题里**显式替换** `/image/` 为 `/file/`(可写个 Thymeleaf 工具函数)。

### 应做(性能/可观测性)

- [ ] 加 `srcset` + `sizes`,覆盖 480/960/1600 三档,搭配 `?w=`。
- [ ] 瀑布流里给 `<img>` 加 `aspect-ratio` 与 `object-fit: cover`,统一视觉(取决于设计偏好)。
- [ ] 列表"加载更多"按钮加个降级提示(避免 memos 不可达时一直转)。

### 可做(未来)

- [ ] 在主题设置里加"压缩图最长边"开关,override `imageCacheWidth`(需要主题与插件之间共享 configMap,或用 Halo theme setting 转发——这是耦合点,要权衡)。
- [ ] 给"刷新图片缓存"做一个跨页跳转链接(深链到 `/console/memos`,但需 console 已登录)。

---

## 9. 验证清单(主题开发完之后,跑一遍)

```bash
# 1. /moments 列表图都走 /memos/proxy/image/
curl -sS http://192.168.0.145:8092/moments \
  | grep -oE '"/memos/proxy/(image|file)/[^"]+"' | sort -u
# 期望:image/attachments/* 出现,且 file/attachments/* 出现得很少(只在视频/GIF/SVG)

# 2. 第一次访问 image 路由是 MISS,第二次是 HIT
curl -sS -D - -o /dev/null http://192.168.0.145:8092/memos/proxy/image/attachments/<uid>/<file>.jpg
# 看 X-Memos-Image-Cache: MISS
curl -sS -D - -o /dev/null http://192.168.0.145:8092/memos/proxy/image/attachments/<uid>/<file>.jpg
# 看 X-Memos-Image-Cache: HIT

# 3. ?w=480 与 ?w=960 是不同文件
ls -la <pluginsRoot>/memos/cache/images/attachments/<uid>/
# 应该看到 w480-… 和 w960-… 两个 jpg

# 4. GIF / SVG 应该回退 file
curl -sS -D - -o /dev/null http://192.168.0.145:8092/memos/proxy/image/attachments/<uid>/anim.gif
# 看 Location: /memos/proxy/file/...   (302)

# 5. Console 的"刷新图片缓存"应该返回 created/hits/failed
curl -sS -X POST http://192.168.0.145:8092/apis/console.api.memos.plugin.halo.run/v1alpha1/cache/refresh
```

主题端可观测的副作用:`/moments` 首屏体积应该**显著下降**(大图由 ~10MB 降到 ~200KB/张),Network 面板里 `image/...` 请求的 `content-length` 应该是 20~300KB 级别。

---

## 10. 引用 / 索引

- 架构全局:`ai/overview/01-architecture.md`
- 主题端路由:`ai/backend/module-theme-router.md`
- Finder 行为:`ai/backend/module-finder.md`、`ai/backend/module-domain-model.md`
- 缓存机制:`ai/backend/module-image-cache.md`
- 反向代理:`ai/backend/module-proxy-endpoint.md`
- 决策记录(本次):`ai/changelog/decisions.md` §2026-06-29
- 排障手册:`ai/troubleshooting/theme-render-fail.md`
- 关键源码:
  - `src/main/java/run/halo/memos/sync/MemosMapper.java`(改写 URL)
  - `src/main/java/run/halo/memos/cache/ImageUrlSupport.java`(URL 决策)
  - `src/main/java/run/halo/memos/endpoint/MemosImageEndpoint.java`(公开压缩图)
  - `src/main/java/run/halo/memos/cache/ImageCacheService.java`(压缩 + 缓存)
  - `src/main/java/run/halo/memos/cache/ImageCacheWarmupService.java`(预热 + 过期清理)
  - `src/main/java/run/halo/memos/finders/impl/MomentFinderImpl.java`(主题端入口)
