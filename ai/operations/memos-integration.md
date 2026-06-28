# 与本地 memos 容器对接

> 这一节回答"我已经有个 memos 容器,怎么让 plugin-memos 连上"。

## 1. memos 容器(已知现状)

来自 `CLAUDE.md`:

| 项 | 值 |
| --- | --- |
| 容器名 | `memos` |
| 宿主机端口 | 5230 |
| Docker compose 路径 | `/www/server/panel/data/compose/memos/docker-compose.yaml` |
| 数据目录 | `/www/server/panel/data/compose/memos/data` |
| 当前版本 | 0.29.1 |

**不要从本插件项目修改/升级 memos**(用户在 CLAUDE.md 明确说了)。

## 2. 网络可达性检查

在 Halo 容器**内部**用 curl 验证 memos 通:

```bash
sg docker -c 'docker exec halo-for-plugin-development \
  curl -sS -o /dev/null -w "%{http_code}\n" http://172.17.0.1:5230/api/v1/memos?pageSize=1'
```

期望:`200`。

如果返回 `000` 或 `Failed to connect`,说明:

- bridge 网关不是 `172.17.0.1`(自定义网络):`docker network inspect bridge` 查。
- memos 没在跑:`docker ps | grep memos`。
- memos 启在别的端口:`ss -lntp | grep 5230` 确认。

## 3. 鉴权

匿名访问默认可以拿到 PUBLIC memos。**只有** memos 关闭访客访问时才需要 token。

验证匿名:

```bash
sg docker -c 'docker exec halo-for-plugin-development \
  curl -sS "http://172.17.0.1:5230/api/v1/memos?pageSize=1" | head -c 500'
```

如果返回 JSON `{"memos":[...],"nextPageToken":"..."}` 即可。

如果返回 `401`:

1. 进 memos Web UI -> 头像 -> System Settings -> General。
2. 勾选 "Allow user sign-ups" 和 "Allow public access" 之一即可匿名。
3. 实在不行,memos -> Settings -> My Account -> Access Tokens -> Create。
4. 复制到 plugin-memos 设置的 `accessToken`。

## 4. 与 Halo 容器的网络拓扑

```text
host: Mini-RuibinNingh
  |-- docker bridge 172.17.0.0/16
  |     |-- halo-for-plugin-development  (Halo app + plugin-memos.jar)
  |     \-- (memos 不在这个 bridge 上,直接在 host 网络)
  |
  \-- memos 容器 (host 网络 or 桥接 5230)
```

**关键**:memos 容器在 host 上,需要从 Halo 容器能 `172.17.0.1:5230` 访问到。

## 5. 验证插件与 memos 真正连通

启 Halo 之后,开 Console 的 `/memos` 页面:

- 看到列表 -> 通。
- 看到 "抓取失败:..." -> 看错误文案:
  - "connection refused" -> baseUrl 写错或 memos 没起。
  - "401 Unauthorized" -> memos 关闭了匿名访问,需要 token。
  - "timeout" -> memos 慢或网络丢包,`memosClient.timeout` 默认 30s。
  - "Host name resolution failed" -> DNS 问题,baseUrl 不要写域名直接写 IP。

## 6. 容器内 curl 的小技巧

```bash
# 进入 Halo 容器交互 shell
sg docker -c 'docker exec -it halo-for-plugin-development /bin/sh'

# 在容器内
apk add --no-cache curl   # Halo 镜像基于 alpine
curl -sS http://172.17.0.1:5230/api/v1/memos?pageSize=1
```

## 7. memos 版本与字段差异

memos 0.29.1 是本插件**目标**版本(`CLAUDE.md` 已固定)。`MemosClient.MemoDto` 字段集是按 0.29.1 反序列化写的。

**若 memos 升级**到 0.30+:

1. 优先查 `docs/vendor/memos-api/README.md` 和 `https://usememos.com/docs/api/latest`。
2. 在 `MemosClient.MemoDto` 加新字段(它有 `@JsonIgnoreProperties(ignoreUnknown=true)`,新字段缺失不会炸)。
3. `MemosMapper.buildSpec` 看是否要消费新字段。
4. 同步更新 `MemosView.vue` 的 `Memo` interface。
5. 在 `ai/changelog/decisions.md` 记一笔。

## 8. 已知限制

- 本插件**不**支持 memos 的"快捷指令"(`/tag` 之类),只读 `content + tags + attachments`。
- memos 私有 memos 不会被本插件拉(必须 PUBLIC 才会出现在列表)。
- memos 的"快捷键表情"由 memos 自家 Web 渲染,本插件不解析。