<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef} from 'vue'
import {ArrowRight, Check, ChevronDown, ClipboardList} from 'lucide-vue-next'
import {beforeDeadline} from '../taskMode'
import type {TodoRow} from '../studentTodo'
import {useStore} from '../store'
import {run} from '../lib'
import Deadline from './Deadline.vue'

const {rows, available} = defineProps<{rows: TodoRow[]; available: number}>()
const store = useStore()
const drafting = ref(false)
const title = ref('')
const busy = ref(false)
async function create() {
  if (busy.value || !title.value.trim()) return
  busy.value = true
  await run(async () => {
    await store.act('createPersonalTodo', {title:title.value})
    title.value = ''
    drafting.value = false
    reveal()
  }, '待办已创建')
  busy.value = false
}
const savingCompletion = ref(false)
async function toggle(row:TodoRow) {
  if (savingCompletion.value) return
  savingCompletion.value = true
  try {
    await run(() => store.act('setPersonalTodoDone', {id:row.id, done:!row.done}), '')
  } finally {
    savingCompletion.value = false
  }
}
const showCompleted = ref(false)
const showAll = ref(false)
const expanded = computed(() => showAll.value || showCompleted.value)
const pending = computed(() => rows.filter(row => !row.done))
const visibleRows = computed(() => {
  if (showCompleted.value) return [...pending.value, ...rows.filter(row => row.done)]
  return showAll.value ? pending.value : pending.value.slice(0, 4)
})
const header = useTemplateRef<HTMLElement>('header')
const list = useTemplateRef<HTMLDivElement>('list')
const maxHeight = ref<number>()
let viewport: VisualViewport | null
let frame = 0

function viewportBottom() {
  return viewport ? viewport.height + viewport.offsetTop : window.innerHeight
}

function capHeight() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(() => {
    if (expanded.value && list.value) {
      maxHeight.value = Math.max(0, Math.floor(viewportBottom() - list.value.getBoundingClientRect().top - 12))
    }
  })
}

async function expand(fromShortcut = false) {
  maxHeight.value = undefined
  await nextTick()
  if (expanded.value && list.value && viewportBottom() - list.value.getBoundingClientRect().top < 150) {
    header.value?.scrollIntoView({block: 'start'})
  }
  if (fromShortcut) {
    header.value?.scrollIntoView({block: 'nearest'})
    header.value?.focus({preventScroll: true})
  }
  capHeight()
}

function reveal() {
  showAll.value = true
  void expand(true)
}

onMounted(() => {
  viewport = window.visualViewport
  window.addEventListener('scroll', capHeight, {passive:true})
  window.addEventListener('resize', capHeight)
  viewport?.addEventListener('resize', capHeight)
  viewport?.addEventListener('scroll', capHeight)
})
onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  window.removeEventListener('scroll', capHeight)
  window.removeEventListener('resize', capHeight)
  viewport?.removeEventListener('resize', capHeight)
  viewport?.removeEventListener('scroll', capHeight)
})
defineExpose({reveal})
</script>

<template>
  <section class="section todo-panel">
    <header ref="header" class="section-head" tabindex="-1">
      <h2><ClipboardList :size="18" />待办事项 <span class="todo-count">{{ pending.length }}</span></h2>
      <button v-if="pending.length > 4 && !showCompleted" class="text-button" type="button"
        :aria-expanded="expanded" aria-controls="student-todo-list" @click="showAll = !showAll; expand()">
        {{ showAll ? '收起列表' : '查看全部' }} <ChevronDown :size="14" />
      </button>
      <button type="button" class="text-button" @click="drafting = !drafting">＋ 新建</button>
    </header>
    <form v-if="drafting" class="todo-create" @submit.prevent="create"><el-input v-model="title" autofocus aria-label="待办内容" placeholder="写下待办事项" maxlength="4000" /><el-button native-type="submit" :loading="busy" :disabled="!title.trim()">创建</el-button></form>
    <div class="todo-controls"><small>已完成 {{ rows.length - pending.length }}</small><label class="todo-toggle">
      <input v-model="showCompleted" type="checkbox" aria-controls="student-todo-list" @change="expand()">
      显示已完成事项
    </label></div>
    <div id="student-todo-list" ref="list" class="todo-list" :class="{expanded}"
      :style="expanded && maxHeight !== undefined ? {maxHeight: 'min(18rem, ' + maxHeight + 'px)'} : undefined"
      :tabindex="expanded && visibleRows.length ? 0 : undefined" role="region" aria-label="事项列表">
      <component :is="row.personal ? 'label' : 'RouterLink'" v-for="(row, index) in visibleRows" :key="row.id"
        :to="row.to" class="todo-line enter" :class="{done:row.done, 'personal-todo':row.personal}"
        :style="{animationDelay:(index*70)+'ms'}">
        <input v-if="row.personal" class="todo-checkbox" type="checkbox" :checked="row.done"
          :aria-label="row.title" :aria-disabled="savingCompletion" @click.prevent="toggle(row)">
        <Check v-else-if="row.done" :size="15" aria-hidden="true" />
        <i v-else :class="{overdue:row.due && !beforeDeadline(row.due)}" aria-hidden="true" />
        <span class="todo-name"><strong>{{ row.title }}</strong><small>{{ row.project }}</small></span>
        <span class="todo-due">
          <template v-if="row.done">{{ row.status }}</template>
          <template v-else>
            <small v-if="row.due && !beforeDeadline(row.due)" class="overdue-label">已逾期</small>
            <Deadline :value="row.due" />
          </template>
        </span>
      </component>
      <p v-if="!visibleRows.length" class="todo-note">{{ showCompleted ? '这个学期暂时还没有事项。' : '暂无待完成事项。' }}</p>
    </div>
    <RouterLink v-if="available" to="/student/tasks" class="todo-available">{{ available }} 项可参与的任务 <ArrowRight :size="14" /></RouterLink>
  </section>
</template>

<style scoped>
.todo-panel{margin:0;padding:15px 16px;min-width:0}
.section-head{margin-bottom:6px;scroll-margin-top:68px}
.section-head h2,.section-head button,.todo-available,.todo-toggle{display:flex;align-items:center;gap:6px}
.section-head button,.todo-available{color:#345c8c;font-size:13px}
.todo-count{padding:1px 7px;background:#edf3fb;border-radius:5px;font-size:12px;font-variant-numeric:tabular-nums}
.todo-toggle{width:fit-content;padding-block:5px;color:#425572;cursor:pointer;font-size:13px}
.todo-toggle input{width:15px;height:15px;margin:0;accent-color:#365d92}
.todo-list{min-width:0}
.todo-list.expanded{max-height:min(20rem,55dvh);overflow:auto;overscroll-behavior:contain}
.todo-line{display:grid;grid-template-columns:18px minmax(0,1fr) auto;align-items:center;gap:8px;padding:7px 3px;color:var(--text);border-bottom:1px solid var(--line)}
.todo-line:last-child{border-bottom:0}
.todo-line i{width:7px;height:7px;margin-left:4px;border-radius:50%;background:#6486b2}
.todo-line i.overdue{background:#ac3434}
.todo-line.done svg{color:var(--primary)}
.todo-name{display:grid;min-width:0}
.todo-name :is(strong,small){overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.todo-name small,.todo-due{font-size:12px;color:#52647c}
.todo-due{display:flex;align-items:center;justify-content:flex-end;gap:5px;flex-wrap:wrap}
.overdue-label{color:#a62626}
.todo-note{font-size:13px;color:var(--muted);margin:6px 0}
.todo-available{margin-top:9px}
@media(max-width:480px){.todo-due{max-width:93px}}
.todo-controls,.todo-create{display:flex;align-items:center;gap:10px}.todo-controls{justify-content:flex-end;color:#52647c}.todo-create{margin:9px 0}
.personal-todo {cursor:pointer}
.personal-todo:hover .todo-name strong {text-decoration:underline}
.todo-line.done:hover .todo-name strong {text-decoration:line-through}
.todo-checkbox {
  appearance:none;
  display:grid;
  place-items:center;
  width:16px;
  height:16px;
  margin:0;
  border:1px solid var(--muted);
  border-radius:3px;
  background:transparent;
  cursor:inherit;
}
.todo-checkbox:checked::after {
  content:'';
  width:5px;
  height:8px;
  border:solid currentColor;
  border-width:0 2px 2px 0;
  color:var(--primary);
  transform:translateY(-1px) rotate(45deg);
}
.todo-checkbox:focus-visible {outline:2px solid var(--primary);outline-offset:2px}
.todo-checkbox[aria-disabled="true"] {cursor:wait}
@media (forced-colors:active) {
  .todo-checkbox {border-color:CanvasText}
  .todo-checkbox:checked::after {color:CanvasText}
}
.todo-line.done .todo-name{color:#64748b;}.todo-line.done .todo-name strong,.todo-line.done .todo-name small{text-decoration:line-through}

/* 列表逐行入场：待办按 45ms 递增错开 */
@keyframes todo-enter { from { opacity: 0; transform: translateY(6px) } to { opacity: 1; transform: none } }
.todo-line.enter { animation: todo-enter .45s cubic-bezier(.22,.68,.32,1) both }
@media (prefers-reduced-motion: reduce) { .todo-line.enter { animation: none } }
</style>
