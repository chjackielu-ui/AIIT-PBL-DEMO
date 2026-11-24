<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, useId, useTemplateRef} from 'vue'
import {ArrowUpRight} from 'lucide-vue-next'
import type {RouteLocationRaw} from 'vue-router'
import type {User} from '../types'
import type {PortfolioRecord} from '../projectPresentation'
import Status from './Status.vue'
import ProjectTeam from './ProjectTeam.vue'

const props = defineProps<{
  record:PortfolioRecord
  info?:{members:User[]; progress:number; introduction:string}
  to:RouteLocationRaw
}>()
const intro = computed(() => (props.info ? props.info.introduction : props.record.summary) || '暂无项目简介')
const expanded = ref(false)
const overflows = ref(false)
const paragraph = useTemplateRef('paragraph')
const descriptionId = useId()
let observer:ResizeObserver | undefined
onMounted(() => {
  const text = paragraph.value!
  observer = new ResizeObserver(() => {
    overflows.value = text.offsetHeight > 40
    if (!overflows.value) expanded.value = false
  })
  observer.observe(text)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <article class="archive-card">
    <div class="dateline"><small>创建于 <time :datetime="record.date">{{ record.date.slice(0,10) }}</time></small><Status :value="record.status" /></div>
    <h3 :title="record.name">{{ record.name }}</h3>
    <div class="intro">
      <div class="intro-heading"><span>项目简介</span><button v-if="overflows" :aria-controls="descriptionId" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</button></div>
      <div :id="descriptionId" class="intro-text" :class="{expanded}" :tabindex="expanded ? 0 : undefined"><p ref="paragraph">{{ intro }}</p></div>
    </div>
    <div class="people">
      <span class="role" :title="record.role"><small>职务</small> {{ record.role }}</span>
      <ProjectTeam v-if="info" :members="info.members" />
    </div>
    <footer>
      <div v-if="info" class="progress"><small>进度 <b>{{ info.progress }}%</b></small><progress :value="info.progress" max="100" aria-label="项目进度" /></div>
      <RouterLink class="enter" :to="to">{{ record.history ? '查看补录' : '进入项目' }} <ArrowUpRight :size="15" /></RouterLink>
    </footer>
  </article>
</template>

<style scoped>
.archive-card {display:flex;flex-direction:column;min-width:0;padding:16px 18px;border:1px solid var(--line);border-top:3px solid #405c90;border-radius:8px;background:white;color:var(--text);box-shadow:0 4px 12px #29426009}
.dateline,.people,footer,.intro-heading,.progress small {display:flex;align-items:center;justify-content:space-between;gap:8px}
small,.intro-heading {color:var(--muted);font-size:12px}
h3 {display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;overflow-wrap:anywhere;margin:8px 0 6px;font-size:20px;line-height:1.25}
.intro {flex:1;min-height:0;display:flex;flex-direction:column}
.intro-heading {min-height:20px}
.intro-heading button {padding:0;background:none;border:0;font:inherit;color:var(--primary);cursor:pointer}
.intro-text {min-height:0;max-height:40px;overflow:hidden}
.intro-text.expanded {max-height:none;overflow:auto}
.intro-text p {margin:0;color:#45556c;font-size:14px;line-height:20px;white-space:pre-wrap;overflow-wrap:anywhere}
.people {min-height:32px;margin:4px 0;font-size:13px}
.role {min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
footer {border-top:1px solid var(--line);padding-top:9px}
.progress {width:min(46%,140px)}
.progress b {font-weight:600;color:#30476b}
progress {display:block;width:100%;height:4px;border:0;border-radius:3px;overflow:hidden;background:#e7edf6;margin-top:4px}
progress::-webkit-progress-bar {background:#e7edf6}progress::-webkit-progress-value {background:#4969a2}progress::-moz-progress-bar {background:#4969a2}
.enter {margin-left:auto;display:inline-flex;align-items:center;gap:5px;background:var(--primary);color:white;text-decoration:none;border-radius:5px;padding:7px 12px;font-weight:600;font-size:13px}
.enter:hover {color:white;text-decoration:none}
</style>
