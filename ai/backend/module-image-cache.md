# 图片压缩缓存

**位置**:

- `src/main/java/run/halo/memos/cache/ImageCacheService.java`
- `src/main/java/run/halo/memos/cache/ImageCacheWarmupService.java`
- `src/main/java/run/halo/memos/cache/ImageCacheSettings.java`
- `src/main/java/run/halo/memos/cache/ImageUrlSupport.java`
- `src/main/java/run/halo/memos/endpoint/MemosImageEndpoint.java`
- `src/main/java/run/halo/memos/endpoint/MemosCacheEndpoint.java`

**职责**:只缓存 Memos 图片附件的压缩派生文件,不缓存 memo 数据,不修改 Memos 原图。

## 1. 路由

| 路径 | 用途 |
| --- | --- |
| `/memos/proxy/image/attachments/{uid}/{filename}` | 压缩缓存图。命中缓存直接返回;未命中则拉 Memos 原图生成缓存。 |
| `/memos/proxy/file/attachments/{uid}/{filename}` | 原图。保留不变,由 `MemosProxyEndpoint` 处理。 |
| `/apis/console.api.memos.plugin.halo.run/v1alpha1/cache/status` | Console 查询缓存状态。 |
| `/apis/console.api.memos.plugin.halo.run/v1alpha1/cache/refresh` | Console 手动刷新/预热缓存。 |
| `/apis/console.api.memos.plugin.halo.run/v1alpha1/cache/clear` | Console 清空压缩缓存。 |

## 2. 缓存目录

使用 Halo 插件根目录:

```text
<pluginsRoot>/memos/cache/images/attachments/{uid}/w{width}-q{quality}-{hash}.jpg
<pluginsRoot>/memos/cache/images/attachments/{uid}/w{width}-q{quality}-{hash}.png
```

不要写入 Memos 数据目录,也不要写入 `src/main/resources/static`。

## 3. 格式规则

- JPEG/JPG:缩放后输出 JPEG。
- 大 PNG 无透明通道:缩放后输出 JPEG,质量至少 86。
- 大 PNG 有透明通道:缩放后保留 PNG。
- GIF/SVG/APNG/不可解码图片:走原图路由。
- 低于 `imageCacheMinBytes` 的图片由 `ImageCacheService` 回退原图。

## 4. 预热

`ImageCacheWarmupService` 每 30 分钟执行一次:

1. 读取 `ImageCacheSettings`。
2. 若 `imageCacheWarmupEnabled=false`,跳过。
3. 通过 `MemosClient.listAll` 拉最近 `imageCacheWarmupPageSize × imageCacheWarmupMaxPages` 条 memo。
4. 扫描可压缩图片附件,串行生成缓存。
5. 清理超过 `imageCacheMaxAgeDays` 的旧缓存文件。

Console 的"刷新图片缓存"按钮调用同一套 `refresh` 流程。

## 5. 维护注意

- 原图 API `/memos/proxy/file/**` 是保底能力,不要删除。
- `MemosMapper` 与 `MemosView.vue` 都有图片 URL 选择逻辑,改支持格式时要同步;大小阈值由后端 `ImageCacheService` 读取设置后判断。
- 压缩失败必须回退原图,不能让主题图直接裂掉。
