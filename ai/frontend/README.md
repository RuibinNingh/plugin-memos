# 前端模块地图(Console UI)

> 本目录是 `ui/src/**` 与构建配置(`vite.config.ts` / `build.gradle` / `package.json`)的说明,只关心 **Console 内的 `/memos` 页面**;主题侧 Thymeleaf 模板不在这里。

## 模块清单

| 文件 | 入口 | 职责 |
| --- | --- | --- |
| [module-entry.md](./module-entry.md) | `ui/src/index.ts` | `definePlugin` 注册路由 |
| [module-view.md](./module-view.md) | `ui/src/views/MemosView.vue` | 唯一视图,实时拉/展示 memos |
| [module-build.md](./module-build.md) | `ui/vite.config.ts`、`ui/package.json`、`ui/build.gradle` | 构建与依赖 |

## 技术栈版本

| 依赖 | 版本 | 出处 |
| --- | --- | --- |
| Vue | 3.5.17 | `ui/package.json` |
| Vite | 5.3.2 | `ui/package.json` |
| pnpm | 9.15.9 | `ui/package.json` `packageManager` |
| TypeScript | 5.8.3 | `ui/package.json` |
| `@halo-dev/api-client` / `@halo-dev/components` / `@halo-dev/console-shared` | 2.21.x | Halo Console SDK |
| `marked` | 12.0.0 | 渲染 memo markdown |
| `axios` | 1.7.2 | HTTP(走 `axiosInstance`) |
| Sass | 1.89.2 | `<style lang="scss">` |

## 一句话交互模型

```text
MemosView mounted
   └─ axiosInstance.get(PROXY_BASE + '/api/v1/memos', { params: { pageSize: 20 } })
        └─ Halo 把请求转给 MemosProxyEndpoint
             └─ WebClient 直连 memos
                  └─ 返回 { memos: [...], nextPageToken }
                       └─ Vue 渲染(时间线 / 瀑布流)
```

详见 [module-view.md](./module-view.md) 与 [overview/01-architecture.md](../overview/01-architecture.md)。