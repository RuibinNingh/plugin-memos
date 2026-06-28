# 主题端 `/moments` 渲染失败

> 路径:`/moments` 和 `/moments/{name}`(主题 URL,不是 Console)。
> 涉及的代码:`MomentRouter`、`MomentFinder`/`Impl`、`MemosMapper`、主题端 Thymeleaf 模板。

## 症状速查

| 现象 | 大概率原因 |
| --- | --- |
| `/moments` 直接 404 | `MomentRouter` 没注册 / 主题没绑 |
| 列表白屏 | 模板找不到 `${moments}` |
| 时间线不是按时间顺序 | `MomentFinderImpl` 异步补 owner 时未保序,或 memos 上游返回了置顶/非时间排序 |
| 详情页 500 | `MemosMapper.toMoment` 异常 |
| 翻页"下一页"丢 tag | `appendTagParam` 被绕过 |
| 评论挂载后评论列表 500 | `MomentCommentSubject` 抛异常 |

---

## 1. `/moments` 404

**排查**:

```bash
# 在 Halo 容器内直接打
sg docker -c 'docker exec halo-for-plugin-development \
  curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:8090/moments'
```

- `200`:前端问题,查主题。
- `404`:`MomentRouter` 没被加载。

**修复**:

- 检查插件状态(Halo Console -> 插件 -> Memos -> Running)。
- 看 `MemosPlugin` 是否被 Spring 扫描到。本项目 `MemosPlugin` 类在 `run.halo.memos` 包,根目录,应自动扫描。
- 重新构建:`./gradlew clean build` 后 reload。

## 2. 列表白屏

**现象**:HTTP 200,但页面是空白(theme 模板出错或变量没传)。

**排查**:

1. 打开 DevTools -> Network -> 看 `/moments` 响应体。
2. 服务端日志:`docker logs halo-for-plugin-development | grep -i "template\|moment"`。
3. 在 `MomentRouter.handlerFunction` 加临时 `log.info(momentList(request))` 看返回数据。

**修复**:

- 模板用了 `${moment.listResult}` 但路由给的是 `moments` -> 主题端模板错。
- 模板用了 `${moment.content.raw}` 但 `MomentContent.raw` 为空 -> `MemosMapper.renderMarkdown` 异常(看 commonmark 抛什么)。
- 模板用了 `${moment.owner.avatar}` 但 `ownerName` 对应的 Halo User 不存在 -> **应该是 `null`**,不是白屏;若白屏说明主题模板没做 `null` 检查。

## 3. 时间线乱序

**现象**:`/moments` 能正常渲染,但列表日期顺序不稳定,例如服务端 HTML 中出现:

```text
2026-05-16
2026-04-22
2026-05-04
2026-04-26
```

**先判断乱序发生在哪一层**:

```bash
curl -sS 'http://<halo-host>:8090/moments' -o /tmp/moments.html
perl -0777 -ne 'while(/data-moment-id="([^"]+)".*?<time class="moment-time">([^<]+)<\/time>/sg){print "$1 $2\n"}' /tmp/moments.html
```

- 如果 HTML 里已经乱序:问题在插件 Finder 或 memos 上游,不是浏览器/主题 JS。
- 如果 HTML 顺序正确但页面视觉乱序:检查主题的瀑布流/columns/masonry 布局和本地 `thyuu-moment-layout`。

**插件侧常见根因**:

- `MomentFinderImpl` 里把 `fillOwner` 接在普通 `flatMap` 后面。`fillOwner` 会异步查 Halo `User`,普通 `flatMap` 按完成顺序发出元素,会打乱 memos 原始顺序。
- 修复:列表路径必须使用 `flatMapSequential(vo -> fillOwner(vo, config))`。

**上游侧常见根因**:

- memos 自身返回的第一页包含 `pinned=true` 的旧 memo,旧内容被置顶后会排在较新内容前面。
- 修复策略取决于产品预期:若要完全按 `createTime` 倒序,需要插件显式排序;若要尊重 memos 置顶,保持上游顺序即可。

## 4. 详情页 500

**排查**:`docker logs` 看 stacktrace,大概率 `MomentFinder.get(momentName)` 抛错。

**常见根因**:

- `momentName` 不是 `memos-{uid}` 形式 -> `MemosMapper.uidFromMomentName` 解出空字符串 -> memos `GET /api/v1/memos/{空}` 404 -> 上游 4xx 透传成 500。
  - 修复:在 `MomentFinder.get` 加 `StringUtils.hasText(uid)` 校验,空时返回 `Mono.empty()`。
- memos API 5xx 透传。
  - 修复:`MemosClient.getMemo` 加重试 / 加 `onErrorResume` 转空对象。

## 5. 翻页丢 tag

**现象**:`/moments?tag=foo` 第 1 页正常,点"下一页"变成 `/moments/page/2` 但 tag 没了。

**原因**:`MomentRouter.appendTagParam` 没起作用,或主题模板自己拼的 URL 没带 tag。

**修复**:

- 主题端若**自己写**"上一页/下一页"链接,**必须**保留 query 中的 `tag`。
- 改 `MomentRouter` 的 `appendTagParam` 为更鲁棒的实现(用 `UriComponentsBuilder` 而非字符串拼接)。
- **不要**修改 `UrlContextListResult` 的 `nextUrl` / `prevUrl` 字段(由 Halo 自己生成),只装饰。

## 6. 评论挂载 500

**现象**:在 moment 详情页打开评论框,提交评论时 500。

**排查**:`docker logs | grep "MomentCommentSubject"`。

**常见根因**:

- `settingFetcher.get("base")` 返回空 -> `withConfig().defaultIfEmpty` 兜了默认值,但**默认 baseUrl 是 `http://127.0.0.1:5230`**,若 memos 不在该地址,会 401/404 链式失败。
  - 修复:确保 `memos-settings.baseUrl` 已配。
- `externalLinkProcessor` 抛异常(站点 externalUrl 没配) -> 跳到日志能看到。
  - 修复:在 `application.yaml` 配 `halo.externalUrl: http://192.168.0.145:8092`。

---

## 调试技巧

```java
// MomentRouter.momentList 开头
log.info("list tag={} page={} size={}", tagVal, pageNum, pageSize);
```

```java
// MomentFinderImpl.listByTag 开头
log.info("listByTag config={} page={} size={}", config, page, size);
```

跑一次列表,看是否真走到 memos 拉数据。**注意脱敏**:不要打 `config.accessToken()`,要打 `config.accessToken().isEmpty()`。
