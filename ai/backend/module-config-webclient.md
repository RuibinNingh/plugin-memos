# WebClientConfig(基础设施 Bean)

**位置**:`src/main/java/run/halo/memos/config/WebClientConfig.java`

**职责**:暴露一个**专给 memos 用的** `WebClient` Bean,被 `MemosClient` 和 `MemosProxyEndpoint` 共享。

## 1. 关键参数

```java
@Bean
public WebClient memosWebClient() {
    HttpClient httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(30));
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
        .build();
}
```

| 参数 | 值 | 原因 |
| --- | --- | --- |
| `responseTimeout` | 30s | 与 `MemosClient.timeout` 保持一致 |
| `maxInMemorySize` | 16 MB | 单页 memos 可能带很多附件,默认 256KB 不够 |

## 2. 为什么"专用"

Halo 自己也会有自己的 WebClient(给 console / API client 用),如果共用:

- 缓冲大小会被 Halo 全局策略覆盖,改不动。
- 一旦 memos 慢,**会拖慢 Halo 其它出站请求**。

所以本插件**独立一个** WebClient,所有出向 memos 的请求都从它出。

## 3. 怎么改

### 3.1 调整缓冲上限

如果未来要支持视频附件,可能要更大。但**单 buffer 16MB** 已经是较大值,再大会增加 OOM 风险。

- 改这里同时**检查 `MemosProxyEndpoint` 的 `collectList` 路径**:它会把整页内容收齐再写回,意味着 Halo 端峰值内存 = buffer 大小。

### 3.2 加连接池

```java
HttpClient httpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(30))
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
```

加 `ChannelOption` 或 `ConnectionProvider` 都能优化,**但要先用 JMH 压一遍**确认有收益。

### 3.3 改超时

- `responseTimeout` 不等于 `MemosClient.timeout`:前者是底层 socket,后者是 `bodyToMono` 整体。
- 两处都改了才彻底生效。

## 4. 不要做的事

- **不要**把 `memosWebClient` 标 `@Primary`(避免覆盖 Halo 的默认 WebClient)。
- **不要**给它加 `clientConnector` 之外的全局 filter(日志、tracing 等),真有需求就单独定义一个 filter 然后 `.filter(...)` 加进来。
- **不要**把 `memosWebClient` 改成非单例(`@Bean` 默认就是单例,scope 改 prototype 会拖垮性能)。