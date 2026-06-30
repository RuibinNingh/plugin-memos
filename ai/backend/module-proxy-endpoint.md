# MemosProxyEndpoint(反向代理)

**位置**:`src/main/java/run/halo/memos/endpoint/MemosProxyEndpoint.java`

**职责**:把"前端 → Halo → memos"的 HTTP 链路透明化。

挂了两条路由:

| 路径 | Bean | 用途 |
| --- | --- | --- |
| `GET /apis/api.memos.plugin.halo.run/v1alpha1/proxy/**` | `endpoint()`(`CustomEndpoint`) | Console 端 axios 调用,走 Halo 鉴权 |
| `GET /memos/proxy/file/**` | `publicMemosFileProxyRouterFunction()`(`@Bean`) | 公开原图代理(点开/下载原图,以及压缩失败回退) |

压缩图不在本类处理;`GET /memos/proxy/image/**` 属于 `MemosImageEndpoint`,会优先返回本地压缩缓存,失败时回退到 `/memos/proxy/file/**`。

## 1. 核心流程

```text
request.path = "/apis/api.memos.plugin.halo.run/v1alpha1/proxy/api/v1/memos"
              ↓
suffixAfterPrefix(path) = "/api/v1/memos"
              ↓
target = baseUrl + "/api/v1/memos?pageSize=20"
              ↓
WebClient.get(target)
  .header("Authorization", "Bearer " + accessToken)  // 有 token 才加
              ↓
exchangeToMono(upstream -> ... collectList ... builder.body(...))
```

## 2. 关键设计点

### 2.1 `suffixAfterPrefix`

```java
private String suffixAfterPrefix(String fullPath) {
    int idx = fullPath.indexOf(ROUTE_PREFIX);
    if (idx < 0) return "";
    return fullPath.substring(idx + ROUTE_PREFIX.length());
}
```

**注意**:用 `indexOf(ROUTE_PREFIX)` 而不是 `startsWith`,这样当路径前缀是 `/memos/proxy/file` 也能正确切出 `/file/...` 后面的内容(因为 `ROUTE_PREFIX = "/proxy"`)。

### 2.2 透传与 body 收集

```java
return spec.exchangeToMono(upstream -> {
    HttpHeaders httpHeaders = upstream.headers().asHttpHeaders();
    ServerResponse.BodyBuilder builder = ServerResponse.status(upstream.statusCode());
    MediaType contentType = httpHeaders.getContentType();
    if (contentType != null) builder.contentType(contentType);
    if (httpHeaders.getETag() != null) builder.eTag(httpHeaders.getETag());
    return upstream.bodyToFlux(DataBuffer.class)
        .collectList()
        .flatMap(buffers -> builder.body(Flux.fromIterable(buffers), DataBuffer.class));
});
```

- 状态码、Content-Type、ETag 全部原样回传。
- **为什么 `collectList`?** `exchangeToMono` 在 lambda 返回的 Mono emit 时就 release 上游 `ClientResponse`;若 lambda 内部只 `return body` 流式给 Spring writer,writer 还没读完上游就被关了 → body 被截断。所以**先把 buffer 收齐,再交给 writer**。
- 16MB 上限由 `WebClientConfig` 兜底(`maxInMemorySize(16 * 1024 * 1024)`),单次代理超过会 `DataBufferLimitException` → 走 `onErrorResume` 返回 400 + JSON。

### 2.3 错误兜底

```java
.onErrorResume(error -> ServerResponse.badRequest()
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(Map.of("error", error.getMessage() == null ? "proxy error" : error.getMessage())))
```

任何上游异常 → 400 + JSON。前端 `MemosView.vue` 接住 `error.message` 显示给用户。

## 3. 怎么改

### 3.1 改路由前缀

`ROUTE_PREFIX = "/proxy"` 和 `groupVersion = api.memos.plugin.halo.run/v1alpha1` 是**对外契约**。

- 改 `groupVersion` ⇒ 前端 `MemosView.vue` 的 `PROXY_BASE` 要同步。
- 改 `ROUTE_PREFIX` ⇒ `suffixAfterPrefix` 的语义不变(因为它用 `indexOf` 找),但**改 `ROUTE_PREFIX` 字符串** ⇒ 公开文件路由也要同步,否则会被错误地切走。

### 3.2 加方法(例:POST 转发)

**目前只 GET** —— 写 memo 到 memos 需要鉴权+防止 XSS,需要更小心:

1. `RequestPredicates` 换成 `GET.or(POST)`,handler 内 `request.method()` 分支。
2. POST body 不能用 `collectList`(会 OOM),要 `request.bodyToFlux(DataBuffer.class).collectList()` 上游侧也收集。
3. `ai/changelog/decisions.md` 留一笔:此插件从"只读代理"变成了"写代理"。

### 3.3 加缓存

**不推荐缓存 memo/API JSON**:文档明确 memo 数据实时。图片压缩派生缓存已经由 `ImageCacheService` 处理,不要在这个代理里再做二次缓存。

### 3.4 改 token 处理

```java
if (StringUtils.hasText(config.accessToken())) {
    spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + config.accessToken());
}
```

- **不要**把 token 放进 query(会落日志)。
- **不要**支持非 Bearer 的认证方式(目前 memos 0.29.x 只用 Bearer)。

## 4. 调试技巧

- 在 IDE 里断点 `suffixAfterPrefix`,确认路径切分正确。
- 用 `curl -v http://halo:8092/apis/api.memos.plugin.halo.run/v1alpha1/proxy/api/v1/memos?pageSize=1` 直接打,排查是 Halo 路由问题还是 memos 端问题。
- 在 `loadConfig()` 加一行 `log.info("proxy baseUrl={}", config.baseUrl())`,可临时打开看设置是否注入。
