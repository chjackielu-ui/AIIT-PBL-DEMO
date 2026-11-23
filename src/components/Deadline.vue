<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref} from 'vue'
import {stamp} from '../lib'

const props = defineProps<{value?:string;remind?:boolean}>()
const date = computed(() => stamp(props.value || '未设置'))
const time = computed(() => date.value.slice(11,16) || (/^\d{4}-\d{2}-\d{2}$/.test(date.value) ? '23:59' : ''))
const now=ref(Date.now())
let timer:ReturnType<typeof setInterval>
onMounted(()=>timer=setInterval(()=>now.value=Date.now(),60000))
onBeforeUnmount(()=>clearInterval(timer))
const remaining=computed(()=>Date.parse(date.value.slice(0,10)+'T'+time.value+':00+08:00')-now.value)
const soon=computed(()=>props.remind!==false && remaining.value>=0 && remaining.value<=72*3600000)
</script>

<template>
  <time class="deadline-lines" :datetime="time ? (date.slice(0,10)+'T'+time) : undefined">
    <span>{{date.slice(0,10)}}</span>
    <span v-if="time">{{time}}</span><strong v-if="soon" class="soon">{{remaining<=86400000?'24 小时内截止':'即将截止'}}</strong>
  </time>
</template>

<style scoped>
.deadline-lines{display:inline-flex;align-items:baseline;gap:4px;flex-wrap:wrap;color:var(--primary);font-weight:600;line-height:1.2;white-space:nowrap;font-variant-numeric:tabular-nums}
.deadline-lines small{display:block;color:var(--muted);font-size:.83em}
.soon{color:#914800;background:#fff3d7;border-radius:4px;padding:2px 4px;font-size:11px}</style>
