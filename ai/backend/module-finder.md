# MomentFinder / MomentFinderImpl(主题端 Finder)

**位置**:
- `src/main/java/run/halo/memos/finders/MomentFinder.java`(接口)
- `src/main/java/run/halo/memos/finders/impl/MomentFinderImpl.java`(实现)

**作用**:暴露给 Thymeleaf 模板的对象,名 `${momentFinder}`(`@Finder("momentFinder")`)。**所有方法实时拉 memos,无缓存**。

## 1. 接口

```java
public interface MomentFinder {
    Flux<MomentVo> listAll();
    Mono<ListResult<MomentVo>> list(Integer page, Integer size);
    Mono<ListResult<MomentVo>> list(Map<String, Object> params);
    Flux<MomentVo> listBy(String tag);
    Mono<MomentVo> get(String momentName);
    Flux<MomentTagVo> listAllTags();
    Mono<ListResult<MomentVo>> listByTag(int pageNum, Integer pageSize, String tagName);
}
```

`Shape` 与官方 plugin-moments 的 `MomentFinder` 一致 —— thyuu-xingdu 主题按官方接口写的,本插件做了"协议对齐"。

## 2. 实现要点

### 2.1 `@Finder` 注解(不要改成 `@Component`)

```java
@Finder("momentFinder")
@RequiredArgsConstructor
public class MomentFinderImpl implements MomentFinder { ... }
```

`@Finder` 来自 `run.halo.app.theme.finders.Finder`,**它本身 meta-`@Service`**;再叠 `@Component` 会让 bean 重复注册。

### 2.2 关键常量

```java
private static final int DEFAULT_SIZE = 10;
private static final int TAG_AGGREGATE_LIMIT = 100;
private static final int LIST_ALL_MAX_PAGES = 50;
```

- `DEFAULT_SIZE` — `list(page, size=null)` / `list(params)` 兜底。
- `TAG_AGGREGATE_LIMIT` — `listAllTags()` / `listAll()` 单次 pageSize(拿够多才能聚合)。
- `LIST_ALL_MAX_PAGES` — `listAll` 防爆上限。

### 2.3 分页算法 `walkToPage`

memos 使用**不透明的 `nextPageToken`**;跳到第 N 页必须顺序走 token。`walkToPage` 用 `expand + take(page) + last` 实现:

```java
private Mono<PageResult> walkToPage(Config config, int page, int size, String tagName) {
    return fetchPage(config, size, null, tagName)
        .expand(prev -> StringUtils.hasText(prev.nextPageToken())
            ? fetchPage(config, size, prev.nextPageToken(), tagName)
            : Mono.empty())
        .take(page)
        .last()
        .onErrorResume(error -> Mono.just(new PageResult(List.of(), null)));
}
```

**坑**:`.last()` 在 expand 还没就绪时会无限等,所以 `.onErrorResume` 兜空 —— 一旦 memos 报错,`listByTag` 返回空页而不是 500。

### 2.4 `fillOwner`

```java
private Mono<MomentVo> fillOwner(MomentVo vo, Config config) {
    String ownerName = config.owner();
    if (!StringUtils.hasText(ownerName)) return Mono.just(vo);
    return client.fetch(User.class, ownerName)
        .map(ContributorVo::from)
        .doOnNext(vo::setOwner)
        .thenReturn(vo)
        .defaultIfEmpty(vo);
}
```

`defaultIfEmpty(vo)` 关键:**Halo 找不到 `ownerName` 时不抛错,而是返回无 owner 的 vo**,主题模板不会因 owner 缺失而崩。

### 2.5 列表顺序必须保留

`listAll()` / `listBy(tag)` / `listByTag(...)` 在 `MemosMapper.toMoment` 后都会调用 `fillOwner` 补 Halo 用户信息。`fillOwner` 内部会 `client.fetch(User.class, ownerName)`,这是异步 IO。

**必须用 `flatMapSequential(vo -> fillOwner(vo, config))`,不要换成普通 `flatMap`。**

原因:

- memos API 返回的数组顺序就是主题端时间线的基础顺序。
- `flatMap` 会按异步完成先后发出元素,不保证原始顺序。
- owner 查询快慢不一致时,`/moments` 服务端 HTML 里会直接乱序,表现为 2026-04-22 排在 2026-05-04 前面这类问题。
- 主题模板只是按 `${moments.listResult}` 遍历,通常不是乱序源头。

如果以后新增异步 enrichment(例如评论数、点赞数、creator 查询),同样要保序:优先 `flatMapSequential`,或先 `collectList` 后按 `releaseTime` 明确排序。

### 2.6 Stats 字段

`Stats` 永远 `empty()`(memos 没评论子模块);**不要**把点赞/评论数填上去除非 `MemosClient` 真的拉了对应端点。

## 3. 怎么改

### 3.1 加新方法(例如"按时间范围过滤")

1. 接口加签名。
2. 实现里用 `MemosClient.listPage(...)` 拉数据 + `MemosMapper.toMoment` 转。
3. **不要**返回 `Moment`;要返回 `MomentVo`(主题端只认 Vo)。

### 3.2 调整分页行为

- `LIST_ALL_MAX_PAGES` 调大会增加 memos 压力,**慎改**。
- `walkToPage` 的 `.take(page)` 改为 `.take(page+1)` 会显著放大请求量,不要轻易动。

### 3.3 改 owner 行为

`Config.owner` 默认 `admin`,来自设置 `memos-settings.owner`。如果想支持"每条 moment 自带 owner":

- 在 `MemoDto` 加 `creator` 字段(需要 memos 端提供);
- `toVo` 拿到后写到 `Moment.metadata.annotations`,然后 `fillOwner` 优先读注解。

## 4. 测试建议

- `MemosClient` 注入 `MockWebServer`(OkHttp 内置的),在 `MomentFinderImpl` 上做集成测试。
- `MemosMapper` 是纯函数,单测覆盖 `excerptOf` / `toMomentName` / `uidFromMomentName` 即可。
