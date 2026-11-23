<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import type {PDFDocumentProxy,RenderTask} from 'pdfjs-dist'
const props=defineProps<{document:PDFDocumentProxy;number:number;width:number;zoom:number;root?:HTMLElement}>()
const sheet=ref<HTMLElement>(),canvas=ref<HTMLCanvasElement>(),visible=ref(false),error=ref('')
const ratio=ref(1.414),naturalWidth=ref(600)
const size=computed(()=>Math.max(1,Math.min(props.width,naturalWidth.value*1.6)*props.zoom/100))
let observer:IntersectionObserver|undefined,render:RenderTask|undefined
onMounted(()=>{
 observer=new IntersectionObserver(([entry])=>visible.value=entry.isIntersecting,{root:props.root,rootMargin:'500px 0px'})
 if(sheet.value) observer.observe(sheet.value)
})
onBeforeUnmount(()=>observer?.disconnect())
watch([visible,()=>props.width,()=>props.zoom],async(_,__,cleanup)=>{
 let cancelled=false
 const previous=render
 cleanup(()=>{cancelled=true;render?.cancel()})
 await previous?.promise.catch(()=>{})
 const target=canvas.value
 if(cancelled || !target) return
 if(!visible.value){target.width=1;target.height=1;return}
 try{
  const page=await props.document.getPage(props.number)
  if(cancelled)return
  const natural=page.getViewport({scale:1})
  naturalWidth.value=natural.width;ratio.value=natural.height/natural.width
  const view=page.getViewport({scale:size.value/natural.width})
  const density=Math.min(devicePixelRatio||1,2,4096/Math.max(view.width,view.height))
  target.width=Math.ceil(view.width*density);target.height=Math.ceil(view.height*density)
  render=page.render({canvas:target,viewport:view,transform:[density,0,0,density,0,0]})
  await render.promise
 }catch(e){if(!cancelled)error.value=(e as Error).message}
},{flush:'post'})
</script>
<template><div ref="sheet" class="sheet" :data-page="number" :style="{width:size+'px',height:size*ratio+'px'}"><canvas ref="canvas" role="img" :aria-label="'第 '+number+' 页'" /><span v-if="error">{{error}}</span></div></template>
<style scoped>.sheet{position:relative;margin:0 auto 6px;background:white;box-shadow:0 1px 5px #29426025}.sheet canvas{width:100%;height:100%;display:block}.sheet span{position:absolute;inset:10px}</style>
