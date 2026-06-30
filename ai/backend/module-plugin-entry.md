# Plugin 入口 & 模板常量

## 1. `MemosPlugin`

**位置**:`src/main/java/run/halo/memos/MemosPlugin.java`

**职责**:Halo 2.x 插件主类。**生命周期全空** —— 不注册 SchemeManager、不挂事件;后台图片缓存预热由 Spring `@Scheduled` Bean 负责。

**为什么空?** 因为本插件不需要手写 lifecycle;代理、Finder、图片缓存预热都由 Spring Bean 管理。

```java
public class MemosPlugin extends BasePlugin {
    public MemosPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
```

**怎么改**

- **不要**往这里塞 `start() / stop()` 钩子;图片缓存预热在 `ImageCacheWarmupService`。
- 若需注册 Halo 扩展点(GVK/Setting 等),由 `plugin.yaml` 的 `spec.configMapName` + `extensions/` 下的 YAML 完成,不需要 Java 写代码。

## 2. `ModelConst`

**位置**:`src/main/java/run/halo/memos/ModelConst.java`

**职责**:**只放一个常量** —— `_templateId`(对应 `Map.of(...)` 中的 `_templateId` key),供主题模板识别当前页面。

```java
public static final String TEMPLATE_ID = "_templateId";
```

**怎么改**

- 这个常量来自官方 plugin-moments 的命名习惯,改它意味着主题侧模板的判断也要改,**不推荐动**。
- 如果以后要新增"模板层共用"常量(如 `LAYOUT` 等),都加在这里,保持单一来源。

## 3. 关联文件

- `src/main/resources/plugin.yaml` — 插件清单(由 `MemosPlugin` 隐式引用)。
- `src/main/resources/extensions/settings.yaml` — 插件设置(运行时配置中心)。

**怎么改**:`plugin.yaml` 中:

| 字段 | 影响 |
| --- | --- |
| `metadata.name=memos` | Halo 控制台 / URL 前缀;不要改 |
| `spec.requires=">=2.23.0"` | 改高会拒绝老 Halo,改低可能用到不存在的 API |
| `spec.settingName` | 必须与 `extensions/settings.yaml` 的 `metadata.name` 一致 |
| `spec.configMapName` | Halo 持久化插件配置用,**改后等于丢设置** |

`settings.yaml` 中:

- 顶层 `metadata.name` 必须等于 `plugin.yaml` 的 `spec.settingName`。
- 字段名(`baseUrl` / `accessToken` / `owner` / `title` / `pageSize` / `imageCache*`)在 `MomentFinderImpl`/`MomentRouter`/`ImageCacheSettings` 等处被 `node.path("...")` 读取,**改名会静默回退到默认值**。
