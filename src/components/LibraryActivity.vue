<script setup lang="ts">
import {shallowRef, watch} from 'vue'
import {RefreshCw} from 'lucide-vue-next'
import {api} from '../api'
import {stamp} from '../lib'
import {useStore} from '../store'
import type {LibraryEvent} from '../types'

const {projectId} = defineProps<{projectId:string}>()
const emit = defineEmits<{changed:[]}>()
const open = defineModel<boolean>({required:true})
const store = useStore()
const entries = shallowRef<LibraryEvent[]>([])
const offset = shallowRef(0)
const hasMore = shallowRef(false)
const pending = shallowRef(false)
const error = shallowRef('')
const refresh = shallowRef(0)
let latest: {projectId:string;eventId:string} | undefined

watch([open, () => projectId, offset, refresh], ([visible, id, start], previous, onCleanup) => {
  if (!visible) {
    offset.value = 0
    return
  }
  let active = true
  let fetching = false
  if (!previous?.[0] || id !== previous[1] || start !== previous[2]) {
    entries.value = []
    hasMore.value = false
  }
  async function fetchPage() {
    if (fetching || !active) return
    fetching = true
    pending.value = true
    try {
      const params = new URLSearchParams({projectId:id, offset:String(start)})
      const page = await api<{entries:LibraryEvent[];hasMore:boolean}>('/library/activity?' + params)
      if (!active) return
      if (start === 0) {
        const eventId = page.entries[0]?.id || ''
        if (latest?.projectId === id && latest.eventId !== eventId) emit('changed')
        latest = {projectId:id,eventId}
      }
      entries.value = page.entries
      hasMore.value = page.hasMore
      error.value = ''
    } catch {
      if (active) error.value = '日志暂时无法更新，请重试。'
    } finally {
      fetching = false
      if (active) pending.value = false
    }
  }
  void fetchPage()
  const timer = start === 0 ? window.setInterval(() => {
    if (!document.hidden) void fetchPage()
  }, 5000) : undefined
  onCleanup(() => {
    active = false
    window.clearInterval(timer)
  })
}, {immediate:true})

function actor(id:string) {
  return store.state?.users.find(user => user.id === id)?.name || '未知用户'
}
function projectName(id:string) {
  return id ? store.state?.projects.find(project => project.id === id)?.name || '项目资料库' : '个人资料库'
}
</script>

<template>
  <el-drawer v-model="open" title="资料库操作日志" size="min(480px, 100vw)">
    <div class="log-toolbar">
      <span class="muted">{{ offset ? '历史记录' : '最新记录 · 每 5 秒自动刷新' }}</span>
      <el-button :disabled="pending" @click="refresh++"><RefreshCw :size="15" />刷新</el-button>
    </div>
    <p v-if="error" role="status" class="error-text">{{ error }}</p>
    <p v-if="pending && !entries.length" role="status" class="muted">正在读取日志…</p>
    <p v-else-if="!entries.length && !error" class="muted">暂无操作记录。</p>
    <ol v-if="entries.length" class="log-list">
      <li v-for="event in entries" :key="event.id">
        <div><strong>{{ event.action }}</strong><time :datetime="event.time">{{ stamp(event.time) }}</time></div>
        <p>{{ event.name }}</p>
        <small>{{ actor(event.userId) }} · {{ projectName(event.projectId) }}</small>
      </li>
    </ol>
    <div v-if="offset || hasMore" class="actions">
      <el-button :disabled="!offset || pending" @click="offset -= 50">上一页</el-button>
      <el-button :disabled="!hasMore || pending" @click="offset += 50">下一页</el-button>
    </div>
  </el-drawer>
</template>

<style scoped>
.log-toolbar,.log-list li div{display:flex;justify-content:space-between;align-items:center;gap:12px}
.log-list{list-style:none;padding:0;margin:12px 0 16px}
.log-list li{border-bottom:1px solid var(--line);padding:11px 0}
.log-list time,.log-list small{font-size:12px}
.log-list p{margin:3px 0}
</style>
