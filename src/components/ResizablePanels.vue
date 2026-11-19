<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {ChevronLeft, ChevronRight, GripVertical} from 'lucide-vue-next'
const props=withDefaults(defineProps<{storageKey:string;rightLabel?:string;initial?:number}>(),{rightLabel:'右侧面板',initial:52})
const host=ref<HTMLElement>(), ratio=ref(props.initial), folded=ref(false), dragging=ref(false)
try{const saved=JSON.parse(localStorage.getItem(props.storageKey)||'null');if(saved){ratio.value=Math.max(20,Math.min(80,Number(saved.ratio)||props.initial));folded.value=!!saved.folded}}catch{}
watch([ratio,folded],()=>{try{localStorage.setItem(props.storageKey,JSON.stringify({ratio:ratio.value,folded:folded.value}))}catch{}})
const columns=computed(()=>folded.value?'minmax(0,1fr) 12px 0px':`minmax(0,${ratio.value}fr) 12px minmax(0,${100-ratio.value}fr)`)
function move(event:PointerEvent){if(!host.value || !dragging.value)return;const box=host.value.getBoundingClientRect();ratio.value=Math.max(20,Math.min(80,(event.clientX-box.left)/(box.width-12)*100))}
function stop(){dragging.value=false;window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',stop);window.removeEventListener('pointercancel',stop)}
function start(event:PointerEvent){if(event.button!==0)return;event.preventDefault();folded.value=false;dragging.value=true;window.addEventListener('pointermove',move);window.addEventListener('pointerup',stop);window.addEventListener('pointercancel',stop)}
function keyboard(event:KeyboardEvent){if(event.key==='ArrowLeft'||event.key==='ArrowRight'){event.preventDefault();folded.value=false;ratio.value=Math.max(20,Math.min(80,ratio.value+(event.key==='ArrowLeft'?-3:3)))}else if(event.key==='Home'){ratio.value=props.initial;folded.value=false}}
onBeforeUnmount(stop)
</script>
<template>
 <div ref="host" class="resizable-panels" :class="{'is-dragging':dragging,'right-folded':folded}" :style="{gridTemplateColumns:columns}">
  <div class="split-primary"><slot /></div>
  <div class="split-rail" @pointerdown="start"><div class="split-resize-target" role="separator" tabindex="0" aria-label="拖动调整左右面板宽度，方向键微调" aria-orientation="vertical" :aria-valuenow="Math.round(ratio)" :aria-valuemin="20" :aria-valuemax="80" @keydown="keyboard"><GripVertical class="split-grip" :size="14" /></div><button class="split-fold" :aria-label="(folded?'展开':'收起')+rightLabel" :title="(folded?'展开':'收起')+rightLabel" :aria-expanded="!folded" @pointerdown.stop @click="folded=!folded"><ChevronLeft v-if="folded" :size="15" /><ChevronRight v-else :size="15" /></button></div>
  <div v-show="!folded" class="split-secondary"><slot name="right" /></div>
 </div>
</template>
<style>
.resizable-panels{display:grid;min-width:0;min-height:0;width:100%;height:100%;position:relative}
.split-primary,.split-secondary{min-width:0;min-height:0;overflow:hidden;height:100%}
.split-rail{position:relative;cursor:col-resize;touch-action:none;background:#f7f9fc;border-inline:1px solid #e7edf5;outline-offset:-2px;z-index:3}
.split-rail:hover,.split-rail:focus-within,.is-dragging>.split-rail{background:#e7efff}
.split-resize-target{position:absolute;inset:0;outline-offset:-2px}
.split-grip{position:absolute;top:calc(50% - 42px);left:-2px;color:#93a4bb;pointer-events:none}
.split-fold{position:absolute;top:50%;left:-5px;transform:translateY(-50%);width:22px;height:48px;display:grid;place-items:center;padding:0;border:1px solid #dce4f0;border-radius:7px;background:#fff;color:#60738f;cursor:pointer;box-shadow:0 2px 6px #23416c14}
.split-fold:hover{color:var(--primary);border-color:#9fbbf5}
.is-dragging{user-select:none}.is-dragging iframe{pointer-events:none}
</style>
