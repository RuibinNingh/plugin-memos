# plugin-memos

把自托管 [Memos](https://usememos.com/) 的公开动态实时接入 [Halo](https://www.halo.run/) 的 Halo 2.x 插件。

`plugin-memos` 是为 **thyuu-xingdu / 星度主题** 的瞬间页面做的适配插件。它不同步、不落库，memo 数据会在访问时实时请求 Memos API；图片附件会按需生成本地压缩缓存，降低博客到浏览器的传输体积。

没有星度主题也可以安装插件，但主题端 `/moments` 页面需要主题提供匹配模板；否则只能使用 Console 端 `/console/memos` 查看 Memos 动态。

## 功能

- 为 thyuu-xingdu / 星度主题提供 `/moments` 列表页和 `/moments/{name}` 详情页数据
- 暴露 `momentFinder`，并按星度主题依赖的官方 `plugin-moments` Finder 数据结构对齐
- Console 端 `/console/memos` 实时查看 Memos 公开动态
- 支持时间线 / 瀑布流视图切换
- 通过 Halo 后端代理 Memos API，避免浏览器直接访问内网 Memos 地址
- 通过 `/memos/proxy/file/**` 保留原图访问，通过 `/memos/proxy/image/**` 提供本地压缩图缓存
- 支持后台定时预热图片缓存，并可在 Console 手动刷新图片缓存
- 可选 Access Token，匿名访问默认只展示 Memos 的 PUBLIC 内容
- 接入 Halo 评论挂载点，moment 详情页可作为评论对象

## 兼容性

| 项目 | 要求 |
| --- | --- |
| Halo | `>= 2.23.0` |
| Java | 21 |
| Node.js | 20 |
| pnpm | 9 |
| Memos | 主要按 `0.29.1` 适配 |
| 主题端瞬间页 | thyuu-xingdu / 星度主题，或自行提供兼容模板 |

当前开发环境使用 Halo 2.25.x 验证。

## 安装

从 Releases 下载 `plugin-memos-<version>.jar`，在 Halo Console 中进入：

```text
插件 -> 上传插件
```

上传 jar 后启用插件。

也可以自行构建：

```bash
./gradlew clean build
```

构建产物位于：

```text
build/libs/plugin-memos-<version>.jar
```

## 配置

启用插件后，在插件设置中填写 Memos 配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| Memos 地址 | `http://127.0.0.1:5230` | 自建 Memos 的根地址 |
| Access Token | 空 | 可选。实例需要鉴权时填写 Memos Personal Access Token |
| 归属 Halo 用户名 | `admin` | 用于主题端头像、昵称展示 |
| 瞬间页标题 | `瞬间` | `/moments` 页面标题 |
| 主题瞬间页每页条数 | `10` | 主题端分页大小 |
| 启用图片压缩缓存 | `true` | 大图优先走本地压缩缓存 |
| 压缩图最长边 | `1600` | 图片不会被放大 |
| JPEG 质量 | `82` | PNG 无透明通道会转 JPEG，有透明通道保留 PNG |
| 定时预热图片缓存 | `true` | 定时扫描最近 Memos 图片并提前生成缓存 |

如果 Halo 运行在 Docker 中，`127.0.0.1` 通常指 Halo 容器自身，不是宿主机。此时 Memos 地址应填写容器可访问的地址，例如：

```text
http://172.17.0.1:5230
```

## 使用

配置完成后访问：

```text
https://your-site.example/moments
```

Console 页面：

```text
https://your-site.example/console/memos
```

主题端 `/moments` 依赖主题模板。当前目标是 thyuu-xingdu / 星度主题，它的 `moments.html` 会消费 `${moments.listResult}` / `${tags}` / `${title}`，`moment.html` 会消费 `${moment}`。

如果你使用其他主题，需要自行提供兼容模板，或在主题里按本插件暴露的数据结构适配 `momentFinder`。

## 工作方式

```text
Theme /moments
  -> MomentRouter
  -> MomentFinderImpl
  -> MemosClient
  -> Memos /api/v1/memos
```

Console 端则通过：

```text
/apis/api.memos.plugin.halo.run/v1alpha1/proxy/**
```

转发到配置的 Memos 服务。

图片附件有两条公开路径：

```text
/memos/proxy/image/attachments/{uid}/{filename}  # 压缩缓存图
/memos/proxy/file/attachments/{uid}/{filename}   # 原图
```

主题列表和 Console 缩略显示默认使用压缩图；点击图片或下载原图仍走原图路径。

## 注意事项

- 插件不会把 Memos 数据写入 Halo 数据库。
- 插件不会缓存 Memos 数据，每次访问都会实时请求 Memos；仅缓存图片压缩后的派生文件。
- 主题端 `/moments` 不是独立前端页面，它依赖主题模板；没有兼容模板时不会自动生成完整页面。
- Memos 的置顶内容可能会出现在较新的普通内容之前，这是上游排序语义。
- 若要求严格按 `createTime` 倒序，需要在插件层另行显式排序。
- 不要把公网不可控地址填入 Memos 地址。该地址会被 Halo 服务端代理访问。
- 不要把 Access Token 写入仓库、截图或日志。

## 本地开发

安装依赖并启动开发 Halo：

```bash
./gradlew pnpmInstall
./gradlew haloServer
```

常用命令：

```bash
# 构建完整插件
./gradlew clean build

# 只验证 Java 编译
./gradlew classes -x :ui:buildFrontend -x processUiResources

# 只构建 UI
./gradlew :ui:assemble
```

项目结构：

```text
src/main/java/run/halo/memos/      # Java 后端、Finder、Router、代理
src/main/resources/plugin.yaml     # Halo 插件清单
src/main/resources/extensions/     # 插件设置表单
ui/src/                            # Console 前端
docs/vendor/memos-api/             # Memos API 本地备注
ai/                                # 项目维护与排障文档
```

## 排障

### `/moments` 404

检查插件是否启用、主题是否已绑定，以及插件日志里 `MomentRouter` 是否正常加载。

### `/moments` 顺序看起来乱

先确认服务端 HTML 顺序：

```bash
curl -sS 'http://<halo-host>:8090/moments' -o /tmp/moments.html
perl -0777 -ne 'while(/data-moment-id="([^"]+)".*?<time class="moment-time">([^<]+)<\/time>/sg){print "$1 $2\n"}' /tmp/moments.html
```

如果 HTML 已乱序，优先检查插件 Finder 和 Memos 上游排序；如果 HTML 正常但页面视觉乱序，检查主题瀑布流布局。

### 图片不显示

检查 `/memos/proxy/image/**` 与 `/memos/proxy/file/**` 是否可访问，以及 Memos 附件是否需要鉴权。压缩失败时会回退到原图路径。

更多维护说明见 [ai/README.md](./ai/README.md)。

## License

[GPL-3.0](./LICENSE)
