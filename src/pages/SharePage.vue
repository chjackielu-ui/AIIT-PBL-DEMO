<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {Layers} from 'lucide-vue-next'
import {api} from '../api'
import ProjectDocument from '../components/ProjectDocument.vue'

const route = useRoute()
const publicData = ref<any>()
const error = ref('')
const notice = ref('')
/** 轮询 in-flight 标志：请求超过 10 秒时不叠加。 */
let generation = 0
let inFlight = false
let timer: ReturnType<typeof setInterval> | undefined

async function load() {
  if (inFlight) return
  inFlight = true
  const requestGeneration = generation
  try {
    const shareId = route.params.id
    const data = await api(`/public/${shareId}`)
    if (shareId !== route.params.id) return
    // 内容未变化时不整体替换，避免全文档重渲染
    if (JSON.stringify(data) !== JSON.stringify(publicData.value)) {
      publicData.value = data
    }
    error.value = ''
    notice.value = ''
  } catch (e) {
    if (requestGeneration !== generation) return
    const message = e instanceof Error ? e.message : ''
    if (message.includes('暂未公开') || message.includes('记录不存在')) {
      publicData.value = undefined
      error.value = '该项目暂未公开'
      notice.value = ''
    } else {
      // 网络故障 / 服务重启：保留已加载内容，仅提示重试中
      notice.value = '服务暂时不可用，正在重试'
    }
  } finally {
    if (requestGeneration === generation) inFlight = false
  }
}

/** 分享链接切换（同一路由参数变化）时立即清空并重新加载。 */
watch(() => route.params.id, () => {
  generation++
  inFlight = false
  publicData.value = undefined
  error.value = ''
  notice.value = ''
  load()
})

onMounted(() => {
  load()
  timer = setInterval(load, 10000)
})
onBeforeUnmount(() => clearInterval(timer))

const imageUrl = (id: string) => `/api/public/${route.params.id}/images/${id}`
const doc = computed(() => publicData.value)
</script>
<template>
  <div class="public-page">
    <div class="public-toolbar no-print">
      <span class="actions"><Layers :size="20" />安信工项目管理平台 <span class="muted">/ 成果分享</span></span>
    </div>
    <el-result v-if="error" :title="error" sub-title="项目尚未开启分享，或分享已关闭。" />
    <ProjectDocument v-else-if="doc" :data="doc" sharing :image-url="imageUrl" />
    <p v-else-if="notice" class="print-tip no-print">{{notice}}</p>
    <div class="public-footer">安信工项目管理平台 · 公开成果</div>
  </div>
</template>
