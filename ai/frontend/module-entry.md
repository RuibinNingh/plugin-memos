# Console 入口 · `ui/src/index.ts`

**位置**:`ui/src/index.ts`

**职责**:用 Halo Console SDK 的 `definePlugin` 注册一个 Console 路由,把 `MemosView` 挂到 `/memos` 菜单。

## 1. 关键代码

```ts
import { definePlugin } from '@halo-dev/console-shared'
import MemosView from './views/MemosView.vue'
import { markRaw } from 'vue'
import RiStickyNoteLine from '~icons/ri/sticky-note-line'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/memos',
        name: 'Memos',
        component: MemosView,
        meta: {
          title: 'Memos',
          searchable: true,
          menu: {
            name: 'Memos',
            group: 'tool',
            icon: markRaw(RiStickyNoteLine),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
```

## 2. 关键事实

| 项 | 值 | 改它的影响 |
| --- | --- | --- |
| `path` | `/memos` | Console 内地址;**改了要同步后端 `MomentRouter` 的 `/moments` 路由**(两者本来就不同,主题是 `/moments`,Console 是 `/memos`)。 |
| `parentName: 'Root'` | 挂在 Halo 根菜单 | 改成其他 `parentName` 可移到子菜单。 |
| `group: 'tool'` | 归类到"工具"组 | 改 `appearance / content / tool` 等可换位置。 |
| `icon: markRaw(RiStickyNoteLine)` | 用 `@iconify/json` 里的 ri 图标 | 改图标名即可;**必须** `markRaw` 包一层(Vue 性能)。 |
| `extensionPoints` | `{}` | 当前未用;若之后要做"在其他页面加按钮",可填 `<key>: { component: ... }`。 |

## 3. 怎么改

### 3.1 加一个 Console 页面

在 `routes` 数组追加一项,`component` 指向新的 `.vue` 文件。**保持 `parentName: 'Root'` 或换成现有 parent**,否则可能不显示。

### 3.2 改图标

`~icons/ri/sticky-note-line` 是 `@iconify/json` 提供的 `ri` 集合。换图标直接改名字:

```ts
import RiListCheck from '~icons/ri/list-check'
icon: markRaw(RiListCheck)
```

### 3.3 国际化

`title: 'Memos'` 当前硬编码。若 Halo Console 已启用 i18n,换成:

```ts
title: 'core.memos.title',
```

并把 key 写到对应的 `locales/*.json`(本项目暂无 locales 目录,**新增要先建**)。

## 4. 不要做的事

- **不要**在 `components: {}` 里加组件再让其他页面引用 —— Halo Console 路由加载机制不会自动注册;改走 `extensionPoints`。
- **不要**把 `MemosView` 直接 `export` 给 `index.ts` 之外的入口用(避免重复加载)。
- **不要**绕过 `definePlugin` 直接 `app.use(...)`,否则 Console 升级到新版 SDK 会断。