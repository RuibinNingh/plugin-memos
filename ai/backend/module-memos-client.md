# MemosClient(memos API 客户端)

**位置**:`src/main/java/run/halo/memos/client/MemosClient.java`

**职责**:把 memos 0.29.1 的公共 API 包成 reactive 客户端。所有方法**实时直连**,不缓存。

## 1. 公共方法

| 方法 | 路径 | 返回 | 用途 |
| --- | --- | --- | --- |
| `listPage(baseUrl, token, pageSize, pageToken)` | `GET /api/v1/memos?pageSize=&pageToken=` | `Mono<MemosPage>` | 单页拉取 |
| `getMemo(baseUrl, token, uid)` | `GET /api/v1/memos/{uid}` | `Mono<MemoDto>` | 拉单条 |
| `listAll(baseUrl, token, pageSize, maxPages)` | (内部递归翻页) | `Flux<MemoDto>` | 流式拉全部(带 maxPages 上限) |

## 2. 内部 DTO

```java
@Data @JsonIgnoreProperties(ignoreUnknown = true)
public static class MemosPage {
    private List<MemoDto> memos;
    private String nextPageToken;
}

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public static class MemoDto {
    private String name;          // "memos/{uid}"
    private String content;       // 原始 markdown
    private String createTime;
    private String updateTime;
    private String visibility;    // PUBLIC / PRIVATE
    private List<String> tags;
    private Boolean pinned;
    private List<AttachmentDto> attachments;
}

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public static class AttachmentDto {
    private String name;          // "attachments/{uid}"
    private String filename;
    private String externalLink;
    private String type;          // MIME
    private String size;
}
```

**为什么 `@JsonIgnoreProperties(ignoreUnknown=true)`?** memos 0.29.x 偶尔会加字段;不忽略会让反序列化因未知字段而失败。

## 3. 关键行为

- **size 边界**:`listPage` 中 `int size = pageSize <= 0 ? 20 : Math.min(pageSize, 500)`。
- **重试**:`retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))` —— 两次指数退避。
- **超时**:`timeout(Duration.ofSeconds(30))`。
- **翻页**:`listAll` 用 `expand()` 跟着 `nextPageToken` 走,`take(maxPages)` 兜底(防止 memos 死循环)。
- **匿名 + Token 兼容**:`client()` 在 `accessToken` 非空时加 `Authorization: Bearer …`;空则匿名(public memos 默认可访问)。

## 4. 怎么改

### 4.1 加新端点

例如要支持 `GET /api/v1/memos/{uid}/comments`:

```java
public Mono<MemosCommentsPage> listComments(String baseUrl, String accessToken, String uid) {
    return client(baseUrl, accessToken)
        .get()
        .uri(builder -> builder.path(MEMOS_PATH + "/" + uid + "/comments").build())
        .retrieve()
        .bodyToMono(MemosCommentsPage.class)
        .timeout(Duration.ofSeconds(30))
        .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)));
}
```

**必须先** 翻 `docs/vendor/memos-api/README.md` 和官方 `https://usememos.com/docs/api/latest`,**不要凭记忆**写路径/字段。

### 4.2 升级 memos 后字段漂移

如果 memos 升级到 0.30+ 改了字段:

1. 在 `MemoDto` 同步加字段(`ignoreUnknown=true` 救一时)。
2. 在 `MemosMapper` 看是否需要新映射(如 `MemosMapper.buildSpec`)。
3. 在 `ai/changelog/decisions.md` 写一笔。

### 4.3 调整超时/重试

- 全局超时 30s 在三处都设了(`listPage` / `getMemo`);改时记得三处同步。
- 改 `Retry.backoff` 参数会改变"瞬时故障恢复"行为,**先确认 memos 实际响应延迟分布**。

## 5. 不要做的事

- **不要** 在这里加 Spring `@Service` 之外的事务/缓存;这个类只做 HTTP。
- **不要** 把 `MemosPage` / `MemoDto` / `AttachmentDto` 暴露到包外(放在 `MemosClient` 内部 `static class` 是为了"DTO 限定在本类内")。
- **不要** 把 `WebClient` 直接由 `MemosClient` 自己 new,统一从 `WebClientConfig` 注入。