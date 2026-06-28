# 排障手册

> 出现问题先来这里对号入座。每篇按"现象 -> 排查路径 -> 修复"三段式。

## 索引

| 文件 | 场景 |
| --- | --- |
| [proxy-404-502.md](./proxy-404-502.md) | Console `/memos` 页拉不到 / 502 / 网关错误 / 附件图裂 |
| [theme-render-fail.md](./theme-render-fail.md) | 主题端 `/moments` 渲染失败(白屏/500/翻页丢 tag) |
| [memos-api-changes.md](./memos-api-changes.md) | memos 升级后字段漂移导致的反序列化失败 |

## 通用的 5 步排查

无论哪种症状,先跑这 5 步能消掉 60% 的问题:

1. **看设置**:Halo Console -> 插件 -> Memos -> 设置,`baseUrl` 是不是 `http://172.17.0.1:5230` 这种容器内可达地址(若是 Docker 跑 Halo)。
2. **看日志**:`sg docker -c 'docker logs -f halo-for-plugin-development'`,搜 `ERROR` / `memos` / `proxy`。
3. **直接 curl**:从 Halo 容器内 `curl http://172.17.0.1:5230/api/v1/memos?pageSize=1`,看是不是 memos 端就有问题。
4. **看插件状态**:Console -> 插件 -> Memos,确认状态 `Running`、版本对得上。
5. **看前端控制台**:浏览器 DevTools -> Console / Network,看 axios 响应体的 `error` 字段。

## 提交新排障条目

当遇到一个**尚未收录**的问题并解决后,请在对应文件追加一节(用 `## YYYY-MM-DD · 现象简述` 作为标题),并在 `changelog/decisions.md` 写一笔根因。