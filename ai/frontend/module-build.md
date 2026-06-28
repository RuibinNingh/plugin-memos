# 前端构建(`ui/`)

涉及 3 个文件:

| 文件 | 角色 |
| --- | --- |
| `ui/package.json` | 依赖、脚本、`packageManager` |
| `ui/vite.config.ts` | Vite + Vue 插件 + Vitest 配置 |
| `ui/build.gradle` | 通过 `corepack` 调 `pnpm install / build / test` |

## 1. `package.json` 关键脚本

```json
"scripts": {
  "build": "run-p type-check \"build-only {@}\" --",
  "build-only": "vite build",
  "dev": "vite build --watch --mode=development",
  "lint:oxlint": "oxlint . --fix -D correctness --ignore-path .gitignore",
  "lint:eslint": "eslint . --fix",
  "lint": "run-s lint:*",
  "prettier": "prettier -w src/",
  "test:unit": "vitest --passWithNoTests",
  "type-check": "vue-tsc --build"
}
```

- **`build`**:`run-p` 并行跑 `type-check` 和 `build-only`(`{@}` 是 `run-p` 的语法糖,代表 `npm run build` 时传入的 args)。
- **`dev`**:`vite build --watch` —— 注意是 build 模式,不是 `vite dev`。插件模式没有 dev server,通过 watcher 把 dist 重新生成即可。
- **`lint`**:`run-s` 串行跑 oxlint → eslint。
- **`test:unit`**:`vitest --passWithNoTests` —— 当前没测试也能过。

## 2. `vite.config.ts`

```ts
import { viteConfig } from '@halo-dev/ui-plugin-bundler-kit'
import Icons from 'unplugin-icons/vite'
import { configDefaults } from 'vitest/config'

export default viteConfig({
  vite: {
    plugins: [Icons({ compiler: 'vue3' })],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
    },
  },
})
```

- **`viteConfig` 来自 `@halo-dev/ui-plugin-bundler-kit`** —— Halo 官方对 Vite 的预封装,负责 output dir、library mode、index.html 模板等。**不要**自己写 `defineConfig`。
- **`Icons({ compiler: 'vue3' })`**:`unplugin-icons/vite` 让 `~icons/ri/foo` 形式的 import 可用。
- **`test.environment: 'jsdom'`**:Vitest 需要 DOM 环境跑组件测试。
- **alias `@` → `./src`**:与 Vite/Vue 工具链约定一致。

## 3. `build.gradle`

```groovy
plugins { id 'base' }
group 'run.halo.memos.ui'

def pnpm = ['corepack', 'pnpm@9.15.9']

tasks.register('pnpmInstall', Exec) { ... }
tasks.register('buildFrontend', Exec) { ... })
tasks.register('pnpmCheck', Exec) { ... }

tasks.named('check')   { dependsOn tasks.named('pnpmCheck') }
tasks.named('assemble') { dependsOn tasks.named('buildFrontend') }
```

**几个坑**:

1. **不用 `node-gradle` 的 pnpm**:它带的 pnpm 与 Node 22 绑定,在 Node 20 环境下会报错。本项目**走 corepack**(系统 Node 自带 corepack),固定 `pnpm@9.15.9`。
2. **不写 `id 'com.github.node-gradle.node'`**:本项目故意只 `id 'base'`,避免 node-gradle 抢着执行。
3. **`buildFrontend` 依赖 `pnpmInstall`**:Gradle 会按输入文件(`package.json`/`pnpm-lock.yaml`/`src/**`)做缓存,只跑变更。

## 4. 父项目怎么消费 UI

`build.gradle`(根):

```groovy
tasks.register('processUiResources', Copy) {
    from project(':ui').layout.buildDirectory.dir('dist')
    into layout.buildDirectory.dir('resources/main/console')
    dependsOn project(':ui').tasks.named('assemble')
    shouldRunAfter tasks.named('processResources')
}

tasks.named('classes') { dependsOn tasks.named('processUiResources') }
```

流程:`:ui:assemble` → `ui/dist/` → 拷到 `build/resources/main/console/` → 打进 jar。

## 5. 怎么改

### 5.1 加新依赖

```bash
cd ui
corepack pnpm@9.15.9 add <pkg>     # 运行时
corepack pnpm@9.15.9 add -D <pkg>  # 开发时
```

加完确认 `package.json` 写进去了,提交时连 `pnpm-lock.yaml` 一起。

### 5.2 升级依赖

- **升级前看 `gradle.properties` 之类的兼容声明**;`@halo-dev/*` 必须 ≥ 2.21(对应 Halo 2.21+ Console)。
- 升级 Vue / Vite **要做完整 build + type-check**:`pnpm run build` 不能有 warning。

### 5.3 加新 Vite 插件

在 `viteConfig({ vite: { plugins: [...] } })` 里加。**所有插件必须与 Vue 3 + Vite 5 兼容**。

### 5.4 加单元测试

1. 写 `xxx.test.ts` / `xxx.test.tsx`。
2. `pnpm test:unit` 跑通。
3. 去掉 `package.json` 里的 `--passWithNoTests`(可选,鼓励真测试)。

## 6. 常见错误

| 报错 | 原因 | 修复 |
| --- | --- | --- |
| `pnpm: command not found` | corepack 未启用 | `corepack enable` 然后 `corepack prepare pnpm@9.15.9 --activate` |
| `Cannot find module '...'` | 依赖未装 | `pnpm install` |
| `Icons(...)` 报错 | `unplugin-icons` 版本不兼容 | 升到 ≥ 22.x |
| `vite build` 输出空 | `definePlugin` 没正确导出 | 确认 `ui/src/index.ts` 有 `export default definePlugin(...)` |