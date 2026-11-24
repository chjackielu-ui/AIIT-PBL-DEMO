<script setup lang="ts">
import {computed, shallowRef} from 'vue'
import {ArrowDown, ArrowUp, Bell, BookOpen, ClipboardCheck, FileSpreadsheet, FolderKanban, GraduationCap, ListTodo, MessagesSquare, Plus, Settings2, UserRound, X, ChartNoAxesColumn, Library} from 'lucide-vue-next'
import {useStore} from '../store'
import {run} from '../lib'

const emit = defineEmits<{pending:[]}>()
const store = useStore()
const choices = [
  {id:'pending',name:'未完成任务',icon:ListTodo,path:''},
  {id:'profile',name:'个人信息',icon:UserRound,path:'/profile'},
  {id:'transcript',name:'成绩单导出',icon:FileSpreadsheet,path:'/print/transcript'},
  {id:'library',name:'资料库',icon:Library,path:'/student/library'},
  {id:'portfolio',name:'项目档案',icon:FolderKanban,path:'/student/portfolio'},
  {id:'classes',name:'我的课程',icon:GraduationCap,path:'/student/classes'},
  {id:'available',name:'可参与任务',icon:BookOpen,path:'/student/tasks'},
  {id:'projects',name:'我的项目',icon:FolderKanban,path:'/student/projects'},
  {id:'learning',name:'学情分析',icon:ChartNoAxesColumn,path:'/student/learning'},
  {id:'notices',name:'通知中心',icon:Bell,path:'/notifications'},
  {id:'discussions',name:'项目讨论',icon:MessagesSquare,path:'/student/discussions'},
  {id:'submissions',name:'提交概览',icon:ClipboardCheck,path:'/student/submissions'},
]
const defaultIds = choices.slice(0,6).map(item => item.id)
const byId = new Map(choices.map(item => [item.id,item]))
const savedIds = computed(() => store.user?.studentShortcuts ?? defaultIds)
const slots = computed(() => Array.from({length:6}, (_,i) => byId.get(savedIds.value[i] || '')))
const draft = shallowRef<string[]>([])
const editing = shallowRef(false)
const saving = shallowRef(false)
function open() {
  draft.value = [...savedIds.value]
  editing.value = true
}
function move(index:number, direction:number) {
  const next = [...draft.value]
  const target = index + direction
  if (target < 0 || target >= next.length) return
  const item = next[index]
  next[index] = next[target]
  next[target] = item
  draft.value = next
}
async function save() {
  if (saving.value) return
  saving.value = true
  await run(async () => {
    await store.act('setStudentShortcuts', {items:draft.value})
    editing.value = false
  }, '快捷导航已保存')
  saving.value = false
}
</script>

<template>
  <section class="section shortcuts shortcuts-panel">
    <div class="section-head"><h2>快捷导航</h2><button type="button" class="text-button shortcut-edit" @click="open"><Settings2 :size="15" />编辑</button></div>
    <div class="shortcut-grid">
      <template v-for="(item,index) in slots" :key="item?.id || index">
        <RouterLink v-if="item?.path" :to="item.path">
          <component :is="item.icon" :size="19" aria-hidden="true" /><span>{{ item.name }}</span>
        </RouterLink>
        <button v-else type="button" @click="item ? emit('pending') : open()">
          <component :is="item?.icon || Plus" :size="19" aria-hidden="true" /><span>{{ item?.name || '添加入口' }}</span>
        </button>
      </template>
    </div>
  </section>
  <el-dialog v-model="editing" title="编辑快捷导航" width="min(520px, 94vw)" :close-on-click-modal="!saving">
    <p class="muted">{{ draft.length }} / 6 个入口，按下列顺序显示。</p>
    <ol class="shortcut-order">
      <li v-for="(id,index) in draft" :key="id">
        <span>{{ byId.get(id)?.name }}</span>
        <button type="button" :aria-label="'上移' + byId.get(id)?.name" :disabled="index === 0" @click="move(index,-1)"><ArrowUp :size="16" /></button>
        <button type="button" :aria-label="'下移' + byId.get(id)?.name" :disabled="index === draft.length - 1" @click="move(index,1)"><ArrowDown :size="16" /></button>
        <button type="button" :aria-label="'移除' + byId.get(id)?.name" @click="draft = draft.filter(item => item !== id)"><X :size="16" /></button>
      </li>
    </ol>
    <div class="actions wrap">
      <el-button v-for="item in choices.filter(item => !draft.includes(item.id))" :key="item.id" :disabled="draft.length === 6" @click="draft = [...draft,item.id]"><Plus :size="14" />{{ item.name }}</el-button>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="draft = [...defaultIds]">恢复默认</el-button>
      <el-button :disabled="saving" @click="editing=false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.shortcuts{grid-area:shortcuts;padding:12px;margin:0}
.shortcuts .section-head{margin-bottom:10px}
.shortcut-edit{display:flex;align-items:center;gap:5px}
.shortcuts{display:flex;flex-direction:column}
.shortcut-grid {
  display:grid;
  grid-template-columns:repeat(3,minmax(0,1fr));
  border:1px solid var(--line);
  border-radius:var(--small-radius);
  overflow:hidden;
}
.shortcut-grid :is(a,button) {
  display:grid;
  justify-items:center;
  align-content:center;
  gap:4px;
  min-width:0;
  min-height:56px;
  padding:6px 4px;
  border:0;
  background:none;
  color:var(--primary);
  text-align:center;
  font-size:13px;
}
.shortcut-grid :is(a,button):not(:nth-child(3n)) {border-inline-end:1px solid var(--line)}
.shortcut-grid :is(a,button):nth-child(n+4) {border-block-start:1px solid var(--line)}
.shortcut-grid :is(a,button):hover span {text-decoration:underline}
.shortcut-grid :is(a,button):focus-visible {outline:2px solid var(--primary);outline-offset:-3px}
@media(max-width:540px) {
  .shortcut-grid {grid-template-columns:repeat(2,minmax(0,1fr))}
  .shortcut-grid :is(a,button):nth-child(n) {border-inline-end:0}
  .shortcut-grid :is(a,button):nth-child(odd) {border-inline-end:1px solid var(--line)}
  .shortcut-grid :is(a,button):nth-child(n+3) {border-block-start:1px solid var(--line)}
}
.shortcut-order{padding-left:22px;margin:0 0 12px}
.shortcut-order li{padding:3px 0;border-bottom:1px solid var(--line)}
.shortcut-order li span{display:inline-block;min-width:calc(100% - 105px)}
.shortcut-order button{width:33px;height:32px;background:none;border:0;color:var(--primary);border-radius:4px}
.shortcut-order button:disabled{opacity:.35}
.shortcut-order button:hover:not(:disabled){background:var(--selected)}
</style>
