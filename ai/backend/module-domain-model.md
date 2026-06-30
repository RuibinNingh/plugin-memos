# 领域模型 / DTO / 转换器

本模块由 3 部分组成:

1. `Moment` + 嵌套类型 — Halo 内存对象,带 GVK 但不入 SchemeManager。
2. `vo.*` — 主题模板和后续扩展用的视图对象。
3. `MemosMapper` — `MemoDto ↔ Moment` 的纯函数转换器(无状态,可单测)。

---

## 1. `Moment`

**位置**:`src/main/java/run/halo/memos/Moment.java`

```java
@GVK(group = "moment.halo.run", version = "v1alpha1", kind = "Moment",
    plural = "moments", singular = "moment")
public class Moment extends AbstractExtension { ... }
```

**关键事实**

- **不入 SchemeManager**:全文搜不到 `Moment` 被 `client.create/update`;`MomentCommentSubject` 也是现拉现转。
- **嵌套类型**:`MomentSpec` / `MomentContent` / `MomentMedia` / `MomentMediaType` / `MomentVisible`。
- **`metadata.name = "memos-" + uid`**:这条规则在 `MemosMapper.toMomentName` 强制,反向解在 `MemosMapper.uidFromMomentName`。

**怎么改**

| 改什么 | 影响 |
| --- | --- |
| 新增 `MomentSpec` 字段 | 主题端 Thymeleaf 模板若未消费就不影响,**但**前台 JSON Schema 会变;同步给 `module-finder.md` 看 Finder 端 Vo 怎么映射。 |
| 改 `MomentVisible` 枚举 | 不影响现存 memos 流程(Mapper 总是写 `PUBLIC`),但 `MemosClient.MemoDto.visibility` 是 `String`,先扩枚举再消费。 |
| 改 `@GVK` | 评论挂载的 `Ref` 校验会失配,**不推荐**。 |

## 2. VO 层

| VO | 字段 | 用途 |
| --- | --- | --- |
| `MomentVo` | `metadata`, `spec`, `owner`, `stats` | Finder 返回给主题模板的对象;**Shape 与官方 plugin-moments 一致**,thyuu-xingdu 主题可直接消费。 |
| `ContributorVo` | `name`, `avatar`, `bio`, `displayName` | 头像/昵称展示;`from(User)` 工厂把 Halo `User` 拍平。 |
| `Stats` | `upvote`, `totalComment`, `approvedComment` | **目前恒为 `Stats.empty()`** —— memos 没有评论子模块。 |
| `MomentTagVo` | `name`, `permalink`, `momentCount` | 标签云。`permalink` 由 `URLEncoder` 编码后拼 `/moments?tag=...`。 |

**怎么改**

- `MomentVo` 字段顺序/类型改 ⇒ 主题模板可能不兼容,**先核对 thyuu-xingdu 用法再动**。
- `Stats` 现在是"占位"语义;若要真接 memos 评论数,需要在 `MemosClient` 加 `GET /api/v1/memos/{id}/comments` 然后在 `MomentFinderImpl.toVo` 里填充。

## 3. `MemosMapper`

**位置**:`src/main/java/run/halo/memos/sync/MemosMapper.java`

**职责**:

- `toMoment(MemoDto, baseUrl)` → 构造 `Moment`(`metadata.name = memos-{uid}` + `spec` + 注解)。
- `toMomentName(memosName)` / `uidFromMomentName(momentName)` — 双向转换,**uid 大小写保留**。
- `renderMarkdown` — 用 `commonmark` 0.22.0 把 `content` 渲染成 `MomentContent.html`。
- `resolveUrl(AttachmentDto)` — 通过 `ImageUrlSupport` 选择附件 URL: 大 JPEG/PNG 图片走 `/memos/proxy/image/attachments/{uid}/{filename}` 压缩缓存;不适合压缩的附件走 `/memos/proxy/file/attachments/{uid}/{filename}` 原图。

**关键常量**

```java
public static final String ANNO_EXCERPT = "thyuu_post_excerpt";
public static final String ANNO_PINNED  = "memos.plugin.halo.run/pinned";
```

**注解语义**:`ANNO_EXCERPT` 供 thyuu-xingdu 主题做摘要,`ANNO_PINNED` 标记置顶。前者是主题约定,改它要联动改主题。

**怎么改**

- **改 `resolveUrl` 的前缀** ⇒ 必须同步 `ImageUrlSupport`、`MemosProxyEndpoint`、`MemosImageEndpoint`,否则压缩图或原图会挂。
- **改 markdown 渲染器** ⇒ 同步更新 `build.gradle` 的 `commonmark` 依赖版本;commonmark 与 flexmark 不兼容,**别混用**。
- **新增 moment 字段** ⇒ 在 `buildSpec` 增加 setter;前端 `MemosView` 不消费这些字段,但 Finder 端要确认 Vo 也带上。

## 4. 一处易错点

`MemosMapper.excerptOf` 用了**正则**剥掉 markdown 标记做摘要:

```java
content.replaceAll("[#*`>\\[\\]()!~-]", "").trim();
```

这是给"摘要"用的近似算法,**不是 HTML 净化**;真正的评论挂载净化在 `MomentCommentSubject` 用 Jsoup `Safelist.none()`。两者职责不同,不要合并。
