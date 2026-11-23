<script setup lang="ts">
import {shallowRef, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ChevronLeft, ChevronRight, Minus, Plus} from 'lucide-vue-next'
import {getDocument, GlobalWorkerOptions, type PDFDocumentProxy} from 'pdfjs-dist'
import PdfSheet from './PdfSheet.vue'
import worker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import {request} from '../api'

GlobalWorkerOptions.workerSrc = worker
const resources = import.meta.env.DEV ? '/node_modules/pdfjs-dist/' : '/pdf-assets/'
const props = defineProps<{url:string}>()
const viewport = ref<HTMLDivElement>()
const page = ref(1), pages = ref(0), zoom = ref(100), width = ref(0)
const busy = ref(true), error = ref('')
const pdf = shallowRef<PDFDocumentProxy>()
let observer: ResizeObserver | undefined
let drag:{x:number;y:number;left:number;top:number}|undefined
function grab(e:PointerEvent){
  if(e.pointerType!=='mouse' || e.button!==0 || !viewport.value || !(e.target instanceof HTMLCanvasElement))return
  drag={x:e.clientX,y:e.clientY,left:viewport.value.scrollLeft,top:viewport.value.scrollTop}
  viewport.value.setPointerCapture(e.pointerId)
}
function release(){ drag=undefined }
function pan(e:PointerEvent){
  if(!drag || !viewport.value)return
  viewport.value.scrollTo(drag.left+drag.x-e.clientX,drag.top+drag.y-e.clientY)
}
function wheel(event:WheelEvent){
  if(!event.ctrlKey || !event.deltaY) return
  event.preventDefault()
  zoom.value=Math.min(250,Math.max(50,zoom.value+(event.deltaY<0?10:-10)))
}
function scrollToPage(n:number){
  viewport.value?.querySelector<HTMLElement>('[data-page="'+n+'"]')?.scrollIntoView({block:'start'})
}
function track(){
  if(!viewport.value) return
  const top=viewport.value.getBoundingClientRect().top
  for(const el of viewport.value.querySelectorAll<HTMLElement>('[data-page]')){
    if(el.getBoundingClientRect().bottom>top+20){page.value=Number(el.dataset.page);break}
  }
}
watch(() => props.url, async (url, _, onCleanup) => {
  const controller = new AbortController()
  let loading: ReturnType<typeof getDocument> | undefined
  onCleanup(() => {
    controller.abort()
    pdf.value = undefined
    if (loading) void loading.destroy().catch(() => {})
  })
  busy.value = true
  error.value = ''
  pages.value = 0
  page.value = 1
  zoom.value = 100
  try {
    const response = await request(url, {signal:AbortSignal.any([controller.signal, AbortSignal.timeout(70000)])})
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw Error(body.message || '文件读取失败')
    }
    const data = await response.arrayBuffer()
    if (controller.signal.aborted) return
    const base = new URL(resources, location.origin)
    loading = getDocument({
      data, useSystemFonts:true,
      cMapUrl:new URL('cmaps/',base).href,
      iccUrl:new URL('iccs/',base).href,
      standardFontDataUrl:new URL('standard_fonts/',base).href,
      wasmUrl:new URL('wasm/',base).href,
    })
    const document = await loading.promise
    if (controller.signal.aborted) return
    pdf.value = document
    pages.value = document.numPages

  } catch (e) {
    if (!controller.signal.aborted) error.value = e instanceof Error ? e.message : '文件预览失败'
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}, {immediate:true})
onMounted(() => {
  observer = new ResizeObserver(([entry]) => {width.value = Math.floor(entry.contentRect.width)})
  if (viewport.value) observer.observe(viewport.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div v-loading="busy" element-loading-text="正在加载文档预览…" class="pdf-preview">
    <div v-if="pages && !error" class="pdf-toolbar">
      <button class="icon-button" aria-label="上一页" :disabled="page <= 1" @click="scrollToPage(page-1)"><ChevronLeft :size="17" /></button>
      <span aria-live="polite">{{page}} / {{pages}}</span>
      <button class="icon-button" aria-label="下一页" :disabled="page >= pages" @click="scrollToPage(page+1)"><ChevronRight :size="17" /></button>
      <span class="pdf-toolbar-divider"></span>
      <button class="icon-button" aria-label="缩小" :disabled="zoom <= 50" @click="zoom=Math.max(50,zoom-25)"><Minus :size="16" /></button>
      <button class="text-button pdf-zoom" title="适合宽度" @click="zoom = 100">{{zoom}}%</button>
      <button class="icon-button" aria-label="放大" :disabled="zoom >= 250" @click="zoom=Math.min(250,zoom+25)"><Plus :size="16" /></button>
    </div>
    <div ref="viewport" class="pdf-viewport" @wheel="wheel" @pointerdown="grab" @pointermove="pan" @pointerup="release" @lostpointercapture="release" @scroll.passive="track">
      <el-empty v-if="error" :description="error" :image-size="50" />
      <template v-if="pdf && !error"><PdfSheet v-for="n in pages" :key="url+':'+n" :document="pdf" :number="n" :width="width" :zoom="zoom" :root="viewport" /></template>
    </div>
  </div>
</template>

<style scoped>
.pdf-preview{background:#edf2f8;min-height:220px;border-radius:8px;overflow:hidden}
.pdf-toolbar{display:flex;justify-content:center;align-items:center;gap:5px;background:#f9fbff;border-bottom:1px solid var(--line);font-variant-numeric:tabular-nums}
.pdf-toolbar .icon-button{border:0;background:transparent}
.pdf-toolbar .icon-button:disabled{opacity:.35;cursor:default}
.pdf-toolbar-divider{height:17px;border-left:1px solid var(--line);margin:0 6px}
.pdf-zoom{width:48px;justify-content:center}
.pdf-viewport{padding:12px;max-height:64vh;overflow:auto;cursor:grab;overscroll-behavior:contain}
.pdf-viewport canvas{display:block;margin:0 auto;max-width:none;box-shadow:0 1px 9px #29426025;background:white}
.pdf-viewport:active{cursor:grabbing}</style>
