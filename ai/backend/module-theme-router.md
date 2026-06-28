# MomentRouter(主题端路由)

**位置**:`src/main/java/run/halo/memos/MomentRouter.java`

**职责**:为 `/moments` 路径注册 Reactive Router,把 `MomentFinder` 喂给主题模板 `moments.html` / `moment.html`。

## 1. 路由表

| 路径 | 模板 | handler |
| --- | --- | --- |
| `GET /moments` | `moments` | `handlerFunction()` |
| `GET /moments/page/{page:\\d+}` | `moments` | `handlerFunction()`(同一 handler,page 从 path 取) |
| `GET /moments/{momentName:\\S+}` | `moment` | `handlerMomentDefault()` |

```java
@Bean
RouterFunction<ServerResponse> momentRouterFunction() {
    return route(GET("/moments").or(GET("/moments/page/{page:\\d+}")), handlerFunction())
        .andRoute(GET("/moments/{momentName:\\S+}"), handlerMomentDefault());
}
```

## 2. 模板数据

`Map.of(...)` 喂给 Thymeleaf 的 key:

| key | 来源 | 说明 |
| --- | --- | --- |
| `moments` | `momentFinder.listByTag(page, size, tag)` 的结果(包成 `UrlContextListResult`) | 列表页 |
| `moment` | `momentFinder.get(name)` | 详情页 |
| `_templateId` | 常量 `"moments"` 或 `"moment"` | **必须**,主题用来识别当前页 |
| `tags` | `momentFinder.listAllTags()` | 列表页侧栏标签云 |
| `title` | `setting.base.title`,默认 `"瞬间"` | 浏览器标题 |

`UrlContextListResult` 是 Halo 主题的"分页对象",带 `nextUrl` / `prevUrl`,`PageUrlUtils.nextPageUrl/prevPageUrl` 自动生成。

## 3. URL 翻页 + Tag 透传

```java
.nextUrl(appendTagParam(PageUrlUtils.nextPageUrl(path, totalPage), tagVal))
.prevUrl(appendTagParam(PageUrlUtils.prevPageUrl(path), tagVal))
```

`appendTagParam` 在下一页/上一页 URL 上**拼回 `?tag=xxx`**,这样主题端的"下一页"按钮不会丢 tag 过滤。**改它会断分页**。

## 4. `tag` 取参

```java
String tagVal = request.queryParam(TAG_PARAM).filter(StringUtils::hasText).orElse(null);
```

- `TAG_PARAM = "tag"`,与 Finder 端 `listByTag` 的入参对应。
- 空字符串视为未传 tag(`filter(StringUtils::hasText)` 过滤)。

## 5. 怎么改

### 5.1 加新路径(例:`/moments/tag/{tag}`)

1. 在 `momentRouterFunction()` 加 `.andRoute(GET("/moments/tag/{tag}"), ...)`.
2. 复用 `handlerFunction()` 即可 —— `tag` 通过 query 传也行,通过 path 取也容易。

### 5.2 换模板名

`Map.of(..., TEMPLATE_ID, "moments", ...)` 中的 `"moments"` 改成 `"moments-v2"` 后,主题侧 `moments.html` 必须重命名。**改前先看主题仓库**。

### 5.3 改 `DEFAULT_PAGE_SIZE`

`private static final int DEFAULT_PAGE_SIZE = 10;` 是兜底;实际值来自设置 `base.pageSize`。

```java
return settingFetcher.get("base")
    .map(item -> item.path("pageSize").asInt(DEFAULT_PAGE_SIZE))
    .defaultIfEmpty(DEFAULT_PAGE_SIZE)
```

## 6. 与主题端约定

- thyuu-xingdu 主题的 `moments.html` 直接消费 `${moments.listResult}` / `${tags}` / `${title}` —— **不要在 router 层做额外的包装/转换**。
- 若主题改了消费方式,优先改主题,不要在 router 里塞兼容层。