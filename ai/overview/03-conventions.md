# 03 · 约定 / Convention

> 这一节是"代码里看不见但必须遵守"的规矩。新增/修改前请过一遍。

## 1. Java 风格

- **JDK 21**:`--release 21`,允许使用 `record`、pattern matching、switch expression。
- **Lombok**:`@Data` / `@Builder` / `@RequiredArgsConstructor` 为主;VO 全用 `@Builder`。
- **响应式优先**:网络/IO 全走 `Mono` / `Flux`,`MemosClient`/`MomentFinderImpl`/`MomentCommentSubject` 全部阻塞路径都已被 reactive 化。
- **WebClient 而非 RestTemplate**:本项目统一走 WebClient。
- **`@Finder` 而非 `@Component`**:`MomentFinderImpl` 用 `run.halo.app.theme.finders.Finder`(meta-`@Service`),不要加 `@Component`。
- **`@GVK` 但不入 SchemeManager**:`Moment` 仅用于评论挂载,不需要在 `MemosPlugin.start()` 里 registerScheme。

## 2. 命名

| 类型 | 命名 |
| --- | --- |
| 包 | `run.halo.memos.{client,finder,finders.impl,sync,vo,endpoint,config}` |
| 类 | `XxxFinder` / `XxxFinderImpl` / `XxxVo` / `XxxMapper` / `XxxEndpoint` |
| 文件 | 与类同名 |
| 设置节点 | 顶层 group=`base`(由 `ReactiveSettingFetcher.get("base")` 读取) |

## 3. 注释密度

- **public 类/方法** 必须有 Javadoc 一句话解释"为什么"。
- **WebClient 缓冲、流式 body 处理、uid 转换** 等"非显然"逻辑,必须有 inline 注释。
- 不要写"重复代码"型注释;要写"为什么不那么写"。

## 4. 提交规范

沿用仓库原有风格(`Adapt Halo 2.23`、`Upgrade to Gradle 9.4.0` 等):

```text
<type>: <summary>

<可选 body: 解释动机/取舍>
```

- `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` 都可以。
- 涉及 `Moment` GVK 或代理路由前缀的"破坏性"改动,必须在 `ai/changelog/decisions.md` 留一笔。

## 5. 测试约定

- 后端单元测试:`src/test/java/...`,`./gradlew test`,使用 `spring-boot-starter-test`。
- 前端单元测试:`ui/` 内 `vitest`,`pnpm test:unit`,目前为空测试套件。

## 6. 安全约定

来自 `CLAUDE.md`,**任何模块都不得违反**:

1. **不要硬编码** accessToken / Cookie / Authorization 到源码、文档、测试、日志。
2. Token 仅从设置(`memos-settings.accessToken`)读取,默认空。
3. 代理路由 `/proxy/**` 只信任 `setting.baseUrl`,不要做"用户自定义 host"功能(SSRF 面会扩散)。

## 7. 设置项命名(不要随意改)

`extensions/settings.yaml` 中:

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `baseUrl` | text | `http://127.0.0.1:5230` | memos 根地址 |
| `accessToken` | password | (空) | 可选 Bearer |
| `owner` | text | `admin` | 用于头像/昵称展示的 Halo 用户 |
| `title` | text | `瞬间` | 主题页标题 |
| `pageSize` | number | `10` | 主题瞬间页每页条数 |

代码里读这些字段的路径统一:`settingFetcher.get("base").path("xxx")`,group 永远叫 `base`。