# MomentCommentSubject(评论挂载)

**位置**:`src/main/java/run/halo/memos/MomentCommentSubject.java`

**职责**:把"memos-backed moment"接入 Halo 自带的**评论系统**。

## 1. 为什么需要

Halo 的评论是按 `Ref(group, kind, name)` 挂到任意扩展对象上的。`Moment` 本身不入 SchemeManager,但 GVK 存在 → 评论系统能用 `Ref(moment.halo.run, Moment, memos-xxx)` 找到"一个挂着评论的目标"。

`CommentSubject` 是 Halo 的 SPI:实现三个方法即可让评论系统"看得见"这个对象并显示正确标题/链接。

## 2. 三个 SPI 方法

```java
public class MomentCommentSubject implements CommentSubject<Moment> {

    @Override
    public Mono<Moment> get(String name) {
        // 把 "memos-{uid}" 反向 → 调 memos → 返回实时 Moment
    }

    @Override
    public Mono<SubjectDisplay> getSubjectDisplay(String name) {
        // 返回 (预览文本, /moments/{name} 链接, "瞬间" 类型名)
    }

    @Override
    public boolean supports(Ref ref) {
        // Ref.group == "moment.halo.run" && Ref.kind == "Moment" 才 true
    }
}
```

## 3. 关键点

### 3.1 `name` 编码规则

```text
memos API 资源 name:   "memos/{uid}"
Moment.metadata.name:   "memos-{uid}"
CommentSubject.get():   接收 "memos-{uid}",内部用 MemosMapper.uidFromMomentName 反解
```

`supports(ref)` 校验的是 `group + kind`,**不校验 name 形态**;校验由 `get` 兜底(取不到就 404)。

### 3.2 净化逻辑

```java
.map(raw -> Jsoup.clean(raw, Safelist.none()))  // 去 HTML
.map(raw -> raw.length() > 100 ? raw.substring(0, 100) : raw)
```

- 用 `Safelist.none()` 而非 `Safelist.basic()` —— **memos 写什么就显示什么文本**,避免把链接/图片 tag 误伤。
- 截 100 字是给评论列表的预览用,**不是 HTML 渲染**。

### 3.3 `externalLinkProcessor.processLink("/moments/" + name)`

走 Halo 的"对外链接"处理(配置站点 `externalUrl` 时会改 host),确保评论页跳转到主题的 `/moments/{name}` 时域名正确。

## 4. 怎么改

### 4.1 改预览长度

`PREVIEW_MAX_LENGTH = 100` 是硬编码;若要"管理员可配",加到 `settings.yaml`。

### 4.2 改净化策略

- 想要富文本预览:换 `Safelist.relaxed()` 并在显示侧再做转义。
- **不要** 在这里做 markdown 渲染(本类只给评论系统喂元数据)。

### 4.3 改 `supports` 条件

```java
@Override
public boolean supports(Ref ref) {
    if (ref == null) return false;
    return Objects.equals(gvk.group(), ref.getGroup())
        && Objects.equals(gvk.kind(), ref.getKind());
}
```

`gvk` 是从 `Moment.class` 反查出来的,改 `Moment` 的 `@GVK` 会自动联动。**不要**硬编码字符串。

## 5. 一处易错点

`get(name)` 内部用 `settingFetcher.get("base")` 拿配置;若 `memos-settings` 未填,会回退到 `http://127.0.0.1:5230` + 空 token —— 此时**匿名访客**就能拉到 PUBLIC memos,符合预期;但若 memos 实例强制鉴权,会 401,此时 `getSubjectDisplay` 会抛 5xx,**评论页会 500**。要让管理员在控制台填 token。