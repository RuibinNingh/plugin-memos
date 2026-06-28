# 插件设置项(`memos-settings`)

**来源**:`src/main/resources/extensions/settings.yaml`

**生效时机**:保存后,所有走 `ReactiveSettingFetcher.get("base")` 的类会实时拿到新值(无缓存)。

## 字段总览

| 字段 | formkit | 默认 | 是否必填 | 读取方 |
| --- | --- | --- | --- | --- |
| `baseUrl` | text | `http://127.0.0.1:5230` | 是 | `MemosProxyEndpoint` / `MemosClient` / `MomentFinderImpl` / `MomentCommentSubject` |
| `accessToken` | password | (空) | 否 | `MemosProxyEndpoint` / `MemosClient` / `MomentCommentSubject` |
| `owner` | text | `admin` | 否 | `MomentFinderImpl` |
| `title` | text | `瞬间` | 否 | `MomentRouter` |
| `pageSize` | number | 10 | 否 | `MomentRouter` |

`group: base` 是统一分组,所有读取都走 `settingFetcher.get("base")`。

## 1. `baseUrl`(必填)

**含义**:memos 服务的根地址(HTTP)。

**踩坑**:**容器内/容器外地址不同**。

| 部署形态 | 填什么 |
| --- | --- |
| Halo 与 memos 都在宿主机 | `http://127.0.0.1:5230`(或宿主机 LAN IP) |
| Halo 在 Docker 容器、memos 在宿主机 | `http://172.17.0.1:5230`(bridge 网关) |
| memos 也在 Docker | `http://memos:5230`(容器名) |

**别在生产把 baseUrl 设成公网地址** —— 本插件的 `MemosProxyEndpoint` 透传上游响应,意味着 Halo 服务器会变成 memos 的代理出口,SSRF 面会扩散到任意 baseUrl。

## 2. `accessToken`(可选)

**含义**:memos API 的 Bearer token。

**何时需要**:memos 实例开启"系统设置 → 启用访客访问" 为 **关闭** 时。匿名调用会 401。

**怎么拿**:

1. memos 网页登录 → 头像 → Settings → My Account → Access Tokens → Create。
2. 复制 token 到本设置项。

**安全**:

- formkit 是 `password`(前端掩码显示)。
- 不会回显到响应中(后端只读 `node.path("accessToken").asText("")`)。
- **不要**写进 `plugin.yaml` 或 `application.yaml`。
- **不要** `log.info(config.accessToken())` —— 任何日志都要脱敏。

## 3. `owner`

**含义**:头像/昵称展示用的 Halo 用户名(单一作者;插件默认把全部 moment 当作同一人发布)。

**何时需要改**:Halo 里给这个作者建了非 `admin` 的账号(如 `rbningh`),想显示真实头像/昵称时。

**生效**:`MomentFinderImpl.fillOwner` 用 `client.fetch(User.class, ownerName)` 查 Halo `User`,转成 `ContributorVo`。

**找不到怎么办**:`fillOwner` 用 `defaultIfEmpty(vo)` 兜空,所以**找不到不报错,只是 owner 字段为 null**。

## 4. `title`

**含义**:主题端 `/moments` 列表页的浏览器标题。

**默认**:`瞬间`。

**注意**:不影响 Console 页面(`MemosView` 写死 `<h2>Memos</h2>`),也不影响文章详情页的标题(那是主题模板自己定的)。

## 5. `pageSize`

**含义**:主题端 `/moments` 列表页每页条数。

**默认**:10。

**上限**:不硬限;但 `MomentFinderImpl.walkToPage` 要走 `pageSize × page` 次 memos 请求,**设太大翻页慢**。建议 5~30。

## 6. 改 settings.yaml 的注意事项

- **加字段**:
  1. 在 `forms[0].formSchema` 加一个 `$formkit` 项。
  2. 在 `MomentFinderImpl` / `MomentRouter` 等读取处增加 `node.path("newField")`。
  3. 给读取处加默认值(`asText("...")` / `asInt(10)`)。
- **改字段名**:**会丢已保存的值**(Halo 持久化的 key 是名字)。需要写兼容层或公告用户重设。
- **删字段**:**会引发 `asText` 取到空,默认生效**。检查代码里所有 `node.path("deletedField")` 是否都接得住 null。