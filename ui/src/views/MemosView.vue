<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { marked } from 'marked'
import { axiosInstance } from '@halo-dev/api-client'
import { VButton, VCard, VEmpty, VLoading, VSpace, VTag } from '@halo-dev/components'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiListCheck from '~icons/ri/list-check'
import RiLayoutGridLine from '~icons/ri/layout-grid-line'

type ViewMode = 'timeline' | 'waterfall'

interface Attachment {
  name: string
  filename: string
  externalLink: string
  type: string
  size?: string
}

interface Memo {
  name: string
  content: string
  createTime: string
  updateTime: string
  visibility: string
  tags: string[]
  pinned: boolean
  attachments: Attachment[]
}

interface MemosResponse {
  memos: Memo[]
  nextPageToken?: string
}

const PROXY_BASE = '/apis/api.memos.plugin.halo.run/v1alpha1/proxy'
const PUBLIC_PROXY_BASE = '/memos/proxy'
const CACHE_API = '/apis/console.api.memos.plugin.halo.run/v1alpha1/cache'
const PAGE_SIZE = 20
const VIEW_KEY = 'memos-view-mode'

const memos = ref<Memo[]>([])
const nextPageToken = ref<string | undefined>(undefined)
const loading = ref(false)
const loadingMore = ref(false)
const refreshingImageCache = ref(false)
const error = ref<string>('')
const cacheMessage = ref<string>('')
const lastFetchedAt = ref<Date | null>(null)
const viewMode = ref<ViewMode>(
  (localStorage.getItem(VIEW_KEY) as ViewMode) || 'timeline'
)

const totalCount = computed(() => memos.value.length)
const hasMore = computed(() => Boolean(nextPageToken.value))
const latestTime = computed(() => {
  if (!memos.value.length) return ''
  return memos.value[0].createTime
})
const listClass = computed(() => `memo-list memo-list--${viewMode.value}`)

function setView(mode: ViewMode) {
  viewMode.value = mode
  localStorage.setItem(VIEW_KEY, mode)
}

function renderMarkdown(content: string): string {
  if (!content) return ''
  // breaks: true 让 memos 编辑器里的单换行变成 <br>,与后端
  // MemosMapper.renderMarkdown(预升级为硬换行)行为保持一致。
  return marked.parse(content, { async: false, breaks: true }) as string
}

function attachmentUrl(att: Attachment): string {
  if (att.externalLink) return att.externalLink
  return `${PUBLIC_PROXY_BASE}/file/${attachmentPath(att)}`
}

function displayAttachmentUrl(att: Attachment): string {
  if (att.externalLink) return att.externalLink
  if (shouldUseImageCache(att)) {
    return `${PUBLIC_PROXY_BASE}/image/${attachmentPath(att)}`
  }
  return attachmentUrl(att)
}

function attachmentPath(att: Attachment): string {
  const uid = att.name.startsWith('attachments/')
    ? att.name.slice('attachments/'.length)
    : att.name
  return `attachments/${uid}/${encodeURIComponent(att.filename)}`
}

function isImage(att: Attachment): boolean {
  return (att.type || '').startsWith('image/')
}

function shouldUseImageCache(att: Attachment): boolean {
  const type = (att.type || '').toLowerCase()
  if (type !== 'image/jpeg' && type !== 'image/jpg' && type !== 'image/png') {
    return false
  }
  return true
}

function isVideo(att: Attachment): boolean {
  return (att.type || '').startsWith('video/')
}

function imagesOf(memo: Memo): Attachment[] {
  return (memo.attachments || []).filter(isImage)
}

function videosOf(memo: Memo): Attachment[] {
  return (memo.attachments || []).filter(isVideo)
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d)
}

function dateLabel(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d)
}

async function fetchPage(token?: string): Promise<MemosResponse> {
  const params: Record<string, string> = { pageSize: String(PAGE_SIZE) }
  if (token) params.pageToken = token
  const { data } = await axiosInstance.get<MemosResponse>(
    `${PROXY_BASE}/api/v1/memos`,
    { params }
  )
  return data
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchPage()
    memos.value = data.memos || []
    nextPageToken.value = data.nextPageToken
    lastFetchedAt.value = new Date()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextPageToken.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const data = await fetchPage(nextPageToken.value)
    memos.value.push(...(data.memos || []))
    nextPageToken.value = data.nextPageToken
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingMore.value = false
  }
}

async function refreshImageCache() {
  refreshingImageCache.value = true
  cacheMessage.value = ''
  try {
    const { data } = await axiosInstance.post(`${CACHE_API}/refresh`)
    cacheMessage.value = `图片缓存：新生成 ${data.created || 0}，已存在 ${data.hits || 0}，失败 ${data.failed || 0}`
  } catch (e: unknown) {
    cacheMessage.value = `图片缓存刷新失败：${e instanceof Error ? e.message : String(e)}`
  } finally {
    refreshingImageCache.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="memos-page">
    <VCard class="memos-header" :body-class="['!p-4']">
      <div class="header-row">
        <div class="header-title">
          <h2>Memos</h2>
          <VSpace>
            <VTag>已加载 {{ totalCount }} 条</VTag>
            <VTag v-if="latestTime" type="info">最近 {{ formatTime(latestTime) }}</VTag>
            <VTag v-if="lastFetchedAt" type="success">已刷新 {{ formatTime(lastFetchedAt.toISOString()) }}</VTag>
          </VSpace>
        </div>
        <VSpace class="header-actions">
          <VButton
            size="sm"
            :type="viewMode === 'timeline' ? 'primary' : 'default'"
            @click="setView('timeline')"
          >
            <template #icon><RiListCheck /></template>
            时间线
          </VButton>
          <VButton
            size="sm"
            :type="viewMode === 'waterfall' ? 'primary' : 'default'"
            @click="setView('waterfall')"
          >
            <template #icon><RiLayoutGridLine /></template>
            瀑布流
          </VButton>
          <VButton type="secondary" :loading="loading" @click="refresh">
            <template #icon><RiRefreshLine /></template>
            刷新
          </VButton>
          <VButton type="secondary" :loading="refreshingImageCache" @click="refreshImageCache">
            <template #icon><RiRefreshLine /></template>
            刷新图片缓存
          </VButton>
        </VSpace>
      </div>
      <p v-if="cacheMessage" class="cache-message">{{ cacheMessage }}</p>
    </VCard>

    <VLoading v-if="loading && !memos.length" />

    <div v-else-if="error" class="memos-error">
      <VCard>
        <p>抓取失败：{{ error }}</p>
        <p class="muted">请检查插件设置中的 Memos 地址是否正确（Docker 内用 http://172.17.0.1:5230）。</p>
      </VCard>
    </div>

    <VEmpty v-else-if="!memos.length" title="暂无公开 Memos" message="在 memos 中发布一条公开动态后刷新。" />

    <div v-else :class="listClass">
      <template v-for="memo in memos" :key="memo.name">
        <div v-if="viewMode === 'timeline'" class="timeline-date">{{ dateLabel(memo.createTime) }}</div>
        <VCard class="memo-item" :body-class="['!p-4']">
          <div class="memo-top">
            <VSpace>
              <VTag v-if="memo.pinned" type="warning">置顶</VTag>
              <VTag>{{ memo.visibility }}</VTag>
              <span class="memo-time">{{ formatTime(memo.createTime) }}</span>
            </VSpace>
            <VSpace v-if="memo.tags?.length" class="memo-tags">
              <VTag v-for="tag in memo.tags" :key="tag" type="default">#{{ tag }}</VTag>
            </VSpace>
          </div>

          <div v-if="memo.content" class="memo-content" v-html="renderMarkdown(memo.content)" />

          <div v-if="imagesOf(memo).length" class="memo-grid" :data-count="imagesOf(memo).length">
            <a
              v-for="img in imagesOf(memo)"
              :key="img.name"
              :href="attachmentUrl(img)"
              target="_blank"
            >
              <img
                :src="displayAttachmentUrl(img)"
                :alt="img.filename"
                loading="lazy"
                decoding="async"
              />
            </a>
          </div>

          <div v-for="v in videosOf(memo)" :key="v.name" class="memo-video">
            <video :src="attachmentUrl(v)" controls preload="metadata" />
          </div>
        </VCard>
      </template>
    </div>

    <div v-if="hasMore" class="load-more">
      <VButton type="secondary" :loading="loadingMore" @click="loadMore">加载更多</VButton>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.memos-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.header-title {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;

  h2 {
    margin: 0;
    font-size: 1.125rem;
    font-weight: 600;
  }
}

.cache-message {
  margin: 0.75rem 0 0;
  color: #4b5563;
  font-size: 0.85rem;
}

.memo-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.memo-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 0.5rem;
}

.memo-time {
  color: #6b7280;
  font-size: 0.8rem;
}

.memo-content {
  :deep(p) {
    margin: 0 0 0.5rem;
    line-height: 1.7;
    word-break: break-word;
  }

  :deep(pre) {
    background: #f3f4f6;
    padding: 0.75rem;
    border-radius: 0.375rem;
    overflow-x: auto;
  }

  :deep(code) {
    font-family: ui-monospace, monospace;
    font-size: 0.875rem;
  }
}

.memo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 0.5rem;
  margin-top: 0.5rem;

  a {
    display: block;
    overflow: hidden;
    border-radius: 0.375rem;
    aspect-ratio: 1 / 1;
    background: #f3f4f6;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    cursor: zoom-in;
  }
}

.memo-video {
  margin-top: 0.5rem;

  video {
    width: 100%;
    max-height: 360px;
    border-radius: 0.375rem;
  }
}

/* 时间线布局：竖线 + 圆点 */
.memo-list--timeline {
  position: relative;
  padding-left: 1.5rem;

  &::before {
    content: '';
    position: absolute;
    left: 0.4rem;
    top: 0.5rem;
    bottom: 0.5rem;
    width: 2px;
    background: #e5e7eb;
  }

  .memo-item {
    position: relative;

    &::before {
      content: '';
      position: absolute;
      left: -1.35rem;
      top: 1rem;
      width: 0.6rem;
      height: 0.6rem;
      border-radius: 50%;
      background: var(--primary, #3b82f6);
      border: 2px solid #fff;
      box-shadow: 0 0 0 2px #e5e7eb;
    }
  }

  .timeline-date {
    font-size: 0.8rem;
    color: #6b7280;
    font-weight: 600;
    margin-left: -1.5rem;
    padding-left: 0;
  }
}

/* 瀑布流布局：CSS columns */
.memo-list--waterfall {
  display: block;
  columns: 3;
  column-gap: 1rem;

  .memo-item {
    break-inside: avoid;
    margin-bottom: 1rem;
    display: inline-block;
    width: 100%;
  }

  .timeline-date {
    display: none;
  }

  @media (max-width: 1024px) {
    columns: 2;
  }

  @media (max-width: 640px) {
    columns: 1;
  }
}

.load-more {
  display: flex;
  justify-content: center;
}

.muted {
  color: #6b7280;
  font-size: 0.85rem;
}
</style>
