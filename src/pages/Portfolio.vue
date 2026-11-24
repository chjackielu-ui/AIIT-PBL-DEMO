<script setup lang="ts">
import MarkdownText from '../components/MarkdownText.vue'
import MarkdownInput from '../components/MarkdownInput.vue'
import {computed, reactive, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {Download, Plus} from 'lucide-vue-next'
import {useStore} from '../store'
import {currentTerm, termLabel, termOrder} from '../academic'
import {run, confirmAction, printPage, localDate} from '../lib'
import {portfolioRecords, projectMembers, projectProgress, type PortfolioRecord} from '../projectPresentation'
import {curriculumCourse} from '../curriculum'
import type {Course, HistoryRecord} from '../types'
import PageHead from '../components/PageHead.vue'
import Status from '../components/Status.vue'
import CourseNotebook from '../components/CourseNotebook.vue'
import PortfolioProjectCard from '../components/PortfolioProjectCard.vue'
import PortfolioTimeline from '../components/PortfolioTimeline.vue'

defineProps<{embedded?:boolean}>()

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)
const dialog = ref(false)

const blank = (): HistoryRecord => ({
  id: '', userId: store.user!.id, name: '', year: currentTerm().academicYear.replace('-', '–'),
  date: localDate(), role: '', description: '', contribution: '', summary: '',
})
const form = reactive(blank())

/** 项目经历与历史补录合并（字段语义与打印页共用 portfolioRecords）。 */
const records = computed(() => portfolioRecords(s.value, store.user!.id))
const detail = computed(() => records.value.find(r => r.id === route.params.id))
const courses = computed(() => new Map(s.value.courses.map(course => [course.id, course])))
const projectInfo = computed(() => new Map(s.value.projects.filter(project => project.memberIds.includes(s.value.user.id))
  .map(project => [project.id, {members:projectMembers(s.value, project), progress:projectProgress(s.value, project), introduction:project.description}])))
const groups = computed(() => {
  const byCourse = new Map<string, {
    id:string; course?:Course; name:string; year:string; semester?:string; level?:string; records:PortfolioRecord[]
  }>()
  for (const record of records.value) {
    const plan = curriculumCourse(record.course)
    const id = [record.courseId || (record.history ? 'history' : 'uncategorized'), record.year, record.semester].join('|')
    const group = byCourse.get(id)
    if (group) {
      group.records.push(record)
      continue
    }
    const imported = record.course.match(/(?:^|_)软件_([^_]+)(?:_|$)/)?.[1]
    byCourse.set(id, {
      id, course:record.courseId ? courses.value.get(record.courseId) : undefined,
      name:imported || record.course || '未关联课程',
      year:record.year, semester:record.semester, level:plan?.level, records:[record],
    })
  }
  return [...byCourse.values()].sort((a,b) => b.year.localeCompare(a.year) || termOrder(b.semester || '') - termOrder(a.semester || ''))
})
const group = computed(() => {
  const id = route.query.course
  if (typeof id !== 'string') return undefined
  return groups.value.find(group => group.id === id) ||
    groups.value.find(group => group.course && (group.course.id === id || curriculumCourse(group.course.name)?.code === id)) ||
    groups.value.find(group => group.id.startsWith(id + '|'))
})
const month = computed(() => typeof route.query.month === 'string' ? route.query.month : '')
const archiveHome = computed(() => ({path:'/student/personal',query:{...route.query,tab:'portfolio'}}))
const groupPath = (course?:string) => ({path:'/student/personal',query:{...route.query,tab:'portfolio',course,month:undefined}})
const recordPath = (id:string) => ({path:'/student/personal/'+id,query:archiveHome.value.query})
const workspacePath = (id:string) => ({path:'/student/projects/' + id, query:{origin:'/student/personal'}})
watch(detail, record => {
  if (record && !record.history) router.replace(workspacePath(record.id))
}, {immediate:true})

function goToMonth(month:string) {
  router.push({path:'/student/personal',query:{...archiveHome.value.query,month}})
}

function edit(id = '') {
  Object.assign(form, blank(), s.value.history.find(h => h.id === id) || {})
  dialog.value = true
}

async function save() {
  if (!form.name.trim() || !form.role.trim() || !form.description.trim() || !form.contribution.trim() || !form.summary.trim()) {
    const {ElMessage} = await import('element-plus')
    ElMessage.warning('请完整填写补录信息')
    return
  }
  await run(async () => {
    const res = await store.act('historySave', {record: form})
    dialog.value = false
    if (!route.params.id) await router.push(recordPath(res.id))
  }, '历史补录已保存')
}

async function remove(id: string) {
  await confirmAction('删除这条未认证的历史补录记录？', async () => {
    await store.act('historyDelete', {id})
    await router.push(archiveHome.value)
  }, '补录已删除')
}
</script>
<template>
  <PageHead :title="detail?.name || group?.name || (embedded ? '项目档案' : '个人情况')">
    <el-button v-if="detail" @click="router.push(archiveHome)">{{ group ? '返回课程目录' : '返回档案' }}</el-button>
    <el-button v-if="detail?.history" @click="edit(detail.id)">编辑补录</el-button>
    <el-button v-if="detail?.history" @click="remove(detail.id)">删除补录</el-button>
  </PageHead>
  <template v-if="detail?.history">
    <section class="section">
      <div class="section-head"><h2>项目经历</h2><Status :value="detail.status" /></div>
      <p>{{ [detail.date, termLabel(detail.year, detail.semester), detail.role].filter(Boolean).join(' · ') }}</p>
      <h3>个人职责</h3>
      <MarkdownText :text="detail.description" />
      <h3>个人贡献</h3>
      <p class="pre-wrap">{{detail.contribution}}</p>
      <h3>成果摘要</h3>
      <MarkdownText :text="detail.summary" />
      <p class="muted">历史补录：本人填写，未经平台教师认证。</p>
    </section>
  </template>
  <template v-else-if="!detail">
    <div class="archive-caption">
      <div class="caption-title"><h2>课程项目</h2><small>{{ group ? termLabel(group.year,group.semester) + ' · ' + group.records.length + ' 个项目' : groups.length + ' 本 · ' + records.length + ' 个项目' }}</small></div>
      <div class="actions wrap">
        <RouterLink v-if="group" :to="groupPath()">返回全部课程</RouterLink>
        <template v-else>
          <el-button @click="printPage('portfolio')"><Download :size="16" />导出个人履历</el-button>
          <el-button type="primary" @click="edit()"><Plus :size="16" />历史项目补录</el-button>
        </template>
      </div>
    </div>
    <div v-if="group" class="archive-grid projects">
      <PortfolioProjectCard v-for="record in group.records" :key="record.id" :record="record"
        :info="projectInfo.get(record.id)" :to="record.history ? recordPath(record.id) : workspacePath(record.id)" />
    </div>
    <template v-else>
      <div v-if="groups.length" class="archive-grid courses">
        <CourseNotebook v-for="course in groups" :key="course.id" :name="course.name"
          :course="course.course" :level="course.level" :term="termLabel(course.year, course.semester)"
          :count="course.records.length" :to="groupPath(course.id)" />
      </div>
      <el-empty v-else description="暂无项目经历" />
    </template>
    <PortfolioTimeline :records="group?.records || records" :projects="s.projects" :tasks="s.tasks" :month="month" @select="goToMonth" />
  </template>
  <el-dialog v-model="dialog" :title="form.id ? '编辑历史补录' : '历史项目补录'" width="min(680px, 94vw)">
    <el-alert title="本记录将标注为历史补录，未经教师认证。" :closable="false" />
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="项目名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="学年" required><el-input v-model="form.year" /></el-form-item>
        <el-form-item label="项目日期" required><el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="个人角色" required><el-input v-model="form.role" /></el-form-item>
      </div>
      <el-form-item label="职责说明" required><MarkdownInput v-model="form.description" /></el-form-item>
      <el-form-item label="个人贡献" required><MarkdownInput v-model="form.contribution" /></el-form-item>
      <el-form-item label="成果摘要" required><MarkdownInput v-model="form.summary" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" @click="save">保存补录</el-button>
    </template>
  </el-dialog>
</template>
<style scoped>
.archive-caption,.caption-title {display:flex;align-items:center;gap:8px}
.archive-caption {justify-content:space-between;margin-bottom:16px}
.archive-caption h2 {margin:0;white-space:nowrap}
.archive-caption .actions {min-height:40px;align-items:center}
.archive-grid {display:grid;grid-auto-rows:272px;gap:18px;margin-bottom:26px}
.archive-grid.courses {grid-template-columns:repeat(auto-fill,minmax(min(195px,100%),216px))}
.archive-grid.projects {grid-template-columns:repeat(auto-fill,minmax(min(270px,100%),350px))}
@media(max-width:700px){
  .archive-caption {align-items:stretch;flex-direction:column}
  .archive-caption .actions {justify-content:flex-start}
}
</style>
