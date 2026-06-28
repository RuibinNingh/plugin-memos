# MemosView.vue(唯一视图)

**位置**:`ui/src/views/MemosView.vue`

**职责**:渲染 `/memos` Console 页面。**所有数据来自 memos,经由后端 `MemosProxyEndpoint` 透传**。

## 1. 文件结构

```text
<script setup lang="ts">
  // 1. 类型定义(Memo / Attachment / MemosResponse)
  // 2. 常量(PROXY_BASE / PAGE_SIZE / VIEW_KEY)
  // 3. 响应式状态(memos / loading / error / viewMode)
  // 4. 计算属性(totalCount / hasMore / latestTime / listClass)
  // 5. 工具函数(renderMarkdown / attachmentUrl / formatTime / ...)
  // 6. 异步动作(fetchPage / refresh / loadMore)
  // 7. onMounted(refresh)
</script>

<template>
  <!-- 头部统计 + 视图切换 + 刷新 -->
  <!-- loading / error / empty 三态 -->
  <!-- 时间线 / 瀑布流列表 + 图片网格 + 视频 -->
  <!-- 加载更多 -->
</template>

<style lang="scss" scoped> ... </style>
```

## 2. 数据模型

```ts
interface Memo {
  name: string         // "memos/{uid}"
  content: string      // markdown
  createTime: string
  updateTime: string
  visibility: string
  tags: string[]
  pinned: boolean
  attachments: Attachment[]
}

interface Attachment {
  name: string         // "attachments/{uid}"
  filename: string
  externalLink: string // 外部图床直链(优先用)
  type: string         // MIME
  size?: string
}
```

字段名与后端 `MemosClient.MemoDto` / `AttachmentDto` **一一对应**,改后端 DTO 要同步这里。

## 3. 关键常量

```ts
const PROXY_BASE = '/apis/api.memos.plugin.halo.run/v1alpha1/proxy'
const PAGE_SIZE  = 20
const VIEW_KEY   = 'memos-view-mode'
```

- **`PROXY_BASE`** 必须与后端 `MemosProxyEndpoint.groupVersion()` 一致。改了后端 group,这里也要改。
- **`PAGE_SIZE = 20`** 是"加载更多"每次拉的条数;`loadMore` 时用 `nextPageToken` 续。
- **`VIEW_KEY = 'memos-view-mode'`** 是 localStorage 的 key,持久化用户选的时间线/瀑布流视图。

## 4. 视图模式

```ts
type ViewMode = 'timeline' | 'waterfall'
const viewMode = ref<ViewMode>(
  (localStorage.getItem(VIEW_KEY) as ViewMode) || 'timeline'
)

function setView(mode: ViewMode) {
  viewMode.value = mode
  localStorage.setItem(VIEW_KEY, mode)
}
```

模板里 `:class="listClass"` 切换 `memo-list--timeline` / `memo-list--waterfall`:

- `timeline`:左侧 2px 竖线 + 圆点 + 日期小标题(`timeline-date`)。
- `waterfall`:CSS columns 三栏(响应式 1024→2、640→1)。

## 5. Markdown 渲染

```ts
import { marked } from 'marked'

function renderMarkdown(content: string): string {
  if (!content) return ''
  return marked.parse(content, { async: false }) as string
}
```

后端 `MemosMapper.renderMarkdown` 也用 `commonmark` 渲染过一遍,**为什么前端还要渲染一次?**

- 后端 `MomentContent.html` 只为 Finder/主题端服务。
- 前端直接拿 `Memo.content`(原始 markdown),用 `marked` 渲染成 HTML 显示在 Console 上 —— **两套渲染**,只是恰好都用 markdown → HTML,后续若 memos 内容里有非标准语法可能出现差异,**接受这个差异**。

## 6. 附件 URL

```ts
function attachmentUrl(att: Attachment): string {
  if (att.externalLink) return att.externalLink
  const uid = att.name.startsWith('attachments/')
    ? att.name.slice('attachments/'.length)
    : att.name
  return `${PROXY_BASE}/file/attachments/${uid}/${encodeURIComponent(att.filename)}`
}
```

- 优先用 `externalLink`(memos 里手工填的外链)。
- 否则走 `PROXY_BASE + '/file/...'` —— 后端 `MemosProxyEndpoint.PUBLIC_FILE_ROUTE_PREFIX = '/memos/proxy/file'` 暴露的公开文件路由。
- `encodeURIComponent(att.filename)` 处理文件名里的中文/空格。

**改 `PROXY_BASE` 必看 `MemosProxyEndpoint.groupVersion()`**。

## 7. 时间格式化

```ts
function formatTime(iso: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}
```

中文环境。若需要 i18n,**改用 `useI18n()`**(目前本插件未引入)。

## 8. 错误展示

```ts
catch (e: unknown) {
  error.value = e instanceof Error ? e.message : String(e)
}
```

模板里:

```html
<p>抓取失败：{{ error }}</p>
<p class="muted">请检查插件设置中的 Memos 地址是否正确(Docker 内用 http://172.17.0.1:5230)。</p>
```

- `error` 是从 `MemosProxyEndpoint.onErrorResume` 返回的 `{ error: "..." }` JSON 反序列化出来的 message。
- 文案中的 `172.17.0.1` 是 Halo 容器**宿主** IP(默认 bridge 网关);若 Docker 网络变了,这里也要改。

## 9. 怎么改

### 9.1 加新字段显示

例:显示 memo 的 `updateTime`。改 `Memo` interface + 模板里加 `<span>{{ formatTime(memo.updateTime) }}</span>`。

### 9.2 加筛选(按 tag / pinned)

1. 在顶部 `<VSpace>` 加一组 `<VButton>` 切换 tag。
2. `fetchPage` 增加 `tag` / `pinned` 参数(后端 memos API 支持)。
3. `refresh()` 重置 `memos.value`。

### 9.3 替换 marked 渲染器

```bash
pnpm remove marked
pnpm add markdown-it
```

`renderMarkdown` 函数同步改;**注意 marked v12 → markdown-it 的输出 HTML 细节差异**(escape、换行处理等)。

### 9.4 替换时间线/瀑布流布局

`memo-list--timeline` / `memo-list--waterfall` 是 SCSS class。在 `<style scoped>` 改样式即可,JS 不动。

## 10. 测试

- `pnpm test:unit` —— 当前为空测试套件(`--passWithNoTests` 兜底)。
- 真实联调建议用 `./gradlew haloServer` 起 Halo + memos 容器,在 Console `/memos` 直接交互。