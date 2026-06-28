# memos API 升级导致的字段漂移

> 当 memos 从 0.29.1 升到 0.30+ 时,`MemosClient` 收到的响应结构可能变化,需要同步本插件字段。

## 1. 怎么知道是这个问题

出现以下任一现象:

- `Console /memos` 报 "抓取失败: 500" 但 memos 自身正常。
- 日志里有 `JsonMappingException` / `UnrecognizedPropertyException` / `MismatchedInputException`。
- 某些字段忽然为 null(原来有值)。
- 附件 URL 错乱(`attachments[].name` 形态变了)。

**注意**:`@JsonIgnoreProperties(ignoreUnknown=true)` 会"宽容"新增字段,但**改名 / 改类型 / 删除**的字段会炸。

## 2. 升级前的准备

1. **先翻官方文档**:`https://usememos.com/docs/api/latest` 或 `docs/vendor/memos-api/README.md`。
2. **对比 0.29.1 的 DTO 字段**:`MemosClient.MemoDto` / `AttachmentDto` / `MemosPage`。
3. **标记 breaking change**:字段名变更、类型变更、嵌套结构变化。

## 3. 升级步骤(标准流程)

```text
[1] 在测试环境升级 memos(用 docker tag 切版本)
[2] curl memos API,把响应存到本地 json
[3] 对比 json 字段 vs MemosClient.MemoDto
[4] 改 MemosClient / MemosMapper / MemosView.vue 的类型
[5] 在本仓库 ./gradlew clean build 通过
[6] 重启 dev Halo,跑三件事:
    - Console /memos 列表能加载
    - 详情页能进
    - 附件图能展示
[7] 在 ai/changelog/decisions.md 记一笔
```

## 4. 字段常见变更(经验)

| 变更 | 适配 |
| --- | --- |
| `createTime` 从 `String` 变 `Timestamp` | 改 `MemoDto.createTime` 为 `Instant`,同步 `MemosMapper.parseInstant`。 |
| `visibility` 改成枚举 | `MemoDto.visibility` 改用 `String`(兼容性最好);`MomentVisible` 映射。 |
| `attachments[].name` 去掉 `attachments/` 前缀 | `MemosMapper.resolveUrl` 改用 `if (uid.startsWith("attachments/"))` 兼容两种情况。 |
| `tags` 变成 `Object` 包含 meta | 写自定义 `JsonDeserializer`,扁平化。 |
| `pinned` 字段删除 | `MemosMapper.buildAnnotations` 不再写 `ANNO_PINNED`。 |
| 新增 `reactions` 字段 | 不动;若要展示,加 `MemoDto.reactions` 字段 + 模板消费。 |

## 5. 当你不知道字段长啥样

在 `MemosClient.listPage` 临时改:

```java
.bodyToMono(JsonNode.class)
.doOnNext(node -> log.info("memos response: {}", node.toString()))
.then(Mono.empty())  // 别真往后走,只为打印
```

跑一次,看 raw JSON 啥样。

## 6. "我能不能做个保险"

可以,但要权衡:

- **Jackson 自定义 `JsonDeserializer`** 把任意字段都尝试转 `String` / `long` 等 —— **不推荐**,会把真正的类型错误掩盖。
- **API 入口加 schema 校验**(JsonSchema)—— 复杂度太高,小插件没必要。
- **MemosMapper 做 null-safe** —— 已经在做(`parseInstant` `try/catch Instant.now()` 是兜底)。

## 7. 升级后必跑清单

- [ ] `./gradlew clean build` 通过
- [ ] `./gradlew test` 通过
- [ ] Console `/memos` 列表可加载
- [ ] 点击进详情,内容正确
- [ ] 至少 1 张图、1 个视频能播
- [ ] 主题端 `/moments` 列表、详情、翻页正常
- [ ] 评论系统对 moment 可挂载
- [ ] `ai/changelog/decisions.md` 已记

## 8. 回滚方案

如果升级后有大问题:

```bash
cd /www/server/panel/data/compose/memos
docker compose down
docker compose up -d   # 回到上一个镜像 tag
```

数据目录 `/www/server/panel/data/compose/memos/data` **不会丢**,但 memos 0.30 写的数据 0.29 读不动(可能有 schema 升级),**先备份** `data/` 再回滚。