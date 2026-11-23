<script setup lang="ts">
import {computed, defineAsyncComponent, ref, watch} from 'vue'
import MarkdownText from './MarkdownText.vue'
import {request} from '../api'
import {fileExtension, isDocument} from '../fileTypes'
import type {ProjectFile} from '../types'

const PdfPreview = defineAsyncComponent(() => import('./PdfPreview.vue'))
const props = defineProps<{file:ProjectFile; url:string}>()
const kind = computed(() => {
  const file = props.file
  const extension = fileExtension(file)
  if (isDocument(file)) return 'document'
  if (file.type.startsWith('image/') || /^(png|jpe?g|gif|webp|svg|bmp|ico)$/.test(extension)) return 'image'
  if (file.type.startsWith('text/') || /^(txt|md|markdown|json|js|ts|vue|java|py|css|html|xml|sql|csv|ya?ml|c|cpp|h|go|rs)$/.test(extension)) return 'text'
  return 'other'
})
const text = ref(''), error = ref(''), busy = ref(false)
watch([() => props.url, kind], async ([url, format], _, onCleanup) => {
  text.value = ''
  error.value = ''
  busy.value = false
  if (format !== 'text') return
  if (props.file.size > 2 * 1024 * 1024) {error.value = '文件超过 2 MB，请下载查看'; return}
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  busy.value = true
  try {
    const response = await request(url, {signal:AbortSignal.any([controller.signal, AbortSignal.timeout(30000)])})
    if (!response.ok) throw Error('无法读取附件，请刷新后重试')
    const content = await response.text()
    if (!controller.signal.aborted) text.value = content || '（空文件）'
  } catch (e) {
    if (!controller.signal.aborted) error.value = (e as Error).message
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}, {immediate:true})
</script>

<template>
  <PdfPreview v-if="kind==='document'" :url="url + '/preview'" />
  <div v-else v-loading="busy" class="attachment-preview">
    <img v-if="kind==='image' && !error" :key="url" :src="url" :alt="file.name" @error="error='无法读取图片'" />
    <MarkdownText v-else-if="text && ['md','markdown'].includes(fileExtension(file))" :text="text" /><pre v-else-if="text">{{text}}</pre>
    <el-empty v-else-if="!busy" :description="error || (file.downloadable===false ? '此格式暂不支持在线预览' : '此文件格式可下载查看')" :image-size="52" />
  </div>
</template>

<style scoped>
.attachment-preview{min-height:130px;background:#f7f9fc;border-radius:8px;overflow:auto;max-height:64vh}
.attachment-preview img{display:block;max-width:100%;max-height:62vh;margin:auto;object-fit:contain}
.attachment-preview pre{padding:16px;white-space:pre-wrap;overflow-wrap:anywhere;margin:0}
</style>
