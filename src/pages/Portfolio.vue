<script setup lang="ts">
import MarkdownText from '../components/MarkdownText.vue'
import MarkdownInput from '../components/MarkdownInput.vue'
import {computed, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ArrowRight, Download, FolderKanban, Plus, Search} from 'lucide-vue-next'
import {useStore} from '../store'
import {currentTerm, termLabel, termOrder} from '../academic'
import {run, confirmAction, printPage, localDate} from '../lib'
import {portfolioRecords, projectMembers, projectProgress, type PortfolioRecord} from '../projectPresentation'
import {curriculumCourse} from '../curriculum'
import type {Course, HistoryRecord} from '../types'
import PageHead from '../components/PageHead.vue'
import Status from '../components/Status.vue'
import BorderGlow from '../components/BorderGlow.vue'
import CourseCover from '../components/CourseCover.vue'
import ProjectTeam from '../components/ProjectTeam.vue'
import PortfolioTimeline from '../components/PortfolioTimeline.vue'

defineProps<{embedded?:boolean}>()

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)
const q = ref('')
const year = ref('')
const status = ref('')
const expandedIds = ref<string[]>([])
const dialog = ref(false)

const blank = (): HistoryRecord => ({
  id: '', userId: store.user!.id, name: '', year: currentTerm().academicYear.replace('-', '–'),
  date: localDate(), role: '', description: '', contribution: '', summary: '',
})
const form = reactive(blank())

/** 项目经历与历史补录合并（字段语义与打印页共用 portfolioRecords）。 */
const records = computed(() => portfolioRecords(s.value, store.user!.id))
const filtered = computed(() => records.value.filter(r =>
  (r.name + ' ' + r.course + ' ' + r.contribution).toLocaleLowerCase().includes(q.value.trim().toLocaleLowerCase())
  && (!year.value || r.year === year.value)
  && (!status.value || r.status === status.value)))
const detail = computed(() => records.value.find(r => r.id === route.params.id))
const years = computed(() => [...new Set(records.value.map(record => record.year).filter(Boolean))].sort().reverse())
const statuses = computed(() => [...new Set(records.value.map(record => record.status))])
const courses = computed(() => new Map(s.value.courses.map(course => [course.id, course])))
const projectInfo = computed(() => new Map(s.value.projects.filter(project => project.memberIds.includes(s.value.user.id))
  .map(project => [project.id, {members:projectMembers(s.value, project), progress:projectProgress(s.value, project)}])))
const groups = computed(() => {
  const byCourse = new Map<string, {
    id:string; course?:Course; name:string; year:string; semester?:string; level?:string; records:PortfolioRecord[]
  }>()
  for (const record of filtered.value) {
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
  <PageHead :title="detail ? detail.name : embedded ? '项目档案' : '个人情况'">
    <el-button v-if="detail" @click="router.push(archiveHome)">返回档案</el-button>
    <el-button v-if="detail?.history" @click="edit(detail.id)">编辑补录</el-button>
    <el-button v-if="detail?.history" @click="remove(detail.id)">删除补录</el-button>
  </PageHead>
  <template v-if="detail">
    <section class="section">
      <div class="section-head"><h2>项目经历</h2><Status :value="detail.status" /></div>
      <p>{{ [detail.date, termLabel(detail.year, detail.semester), detail.role].filter(Boolean).join(' · ') }}</p>
      <template v-if="projectInfo.get(detail.id)?.members.length">
        <h3>团队成员</h3>
        <ProjectTeam :members="projectInfo.get(detail.id)!.members" expanded />
      </template>
      <h3>个人职责</h3>
      <MarkdownText :text="detail.description" />
      <h3>个人贡献</h3>
      <p class="pre-wrap">{{detail.contribution}}</p>
      <h3>成果摘要</h3>
      <MarkdownText :text="detail.summary" />
      <p v-if="detail.score != null">个人成绩：{{detail.score}} 分 · {{detail.comment}}</p>
      <p v-if="detail.history" class="muted">历史补录：本人填写，未经平台教师认证。</p>
      <el-button v-else @click="router.push('/student/projects/' + detail.id)">进入项目工作区</el-button>
    </section>
  </template>
  <template v-else>
    <div class="toolbar archive-tools">
      <el-input v-model="q" placeholder="搜索课程、项目或个人贡献" aria-label="搜索课程、项目或个人贡献" clearable>
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="year" clearable placeholder="全部学年" aria-label="筛选学年">
        <el-option v-for="y in years" :key="y" :value="y" />
      </el-select>
      <el-select v-model="status" clearable placeholder="全部状态" aria-label="筛选状态">
        <el-option v-for="v in statuses" :key="v" :value="v" />
      </el-select>
      <div class="actions wrap">
        <el-button @click="printPage('portfolio')"><Download :size="16" />导出个人履历</el-button>
        <el-button type="primary" @click="edit()"><Plus :size="16" />历史项目补录</el-button>
      </div>
    </div>
    <div v-if="group" class="archive-caption">
      <RouterLink :to="groupPath()">全部课程</RouterLink><span>/</span><h2>{{ group.name }}</h2>
      <small>{{ termLabel(group.year, group.semester) }}</small>
    </div>
    <div v-else-if="filtered.length" class="archive-caption"><h2>课程项目</h2><small>{{ filtered.length }} 个项目</small></div>
    <div v-if="group" class="archive-grid">
      <BorderGlow v-for="record in group.records" :key="record.id">
        <article class="archive-project">
          <header>
            <FolderKanban :size="17" />
            <strong>{{ record.name }}</strong>

            <small>{{ record.teachingClass || record.role }} · {{ record.date.slice(0,10) }}</small>
            <ProjectTeam v-if="projectInfo.has(record.id)" :members="projectInfo.get(record.id)!.members" />
            <Status :value="record.status" />
          </header>
          <div class="archive-project-detail">
            <p>{{ expandedIds.includes(record.id) ? (record.description || record.summary) : (record.description || record.summary || '暂无项目简介').slice(0,64) }}<template v-if="(record.description || record.summary).length > 64"><span v-if="!expandedIds.includes(record.id)">…</span> <button class="text-button" @click="expandedIds = expandedIds.includes(record.id) ? expandedIds.filter(id => id !== record.id) : [...expandedIds,record.id]">{{expandedIds.includes(record.id) ? '收起' : '更多'}}</button></template></p>
            <template v-if="projectInfo.has(record.id)">
              <small>当前进度 {{ projectInfo.get(record.id)!.progress }}%</small>

            </template>
            <RouterLink :to="recordPath(record.id)">查看项目经历 <ArrowRight :size="14" /></RouterLink>
          </div>
        </article>
      </BorderGlow>
    </div>
    <div v-else class="archive-grid">
      <BorderGlow v-for="course in groups" :key="course.id">
        <RouterLink class="archive-course" :to="groupPath(course.id)">
          <CourseCover :course="course.course">
            <span v-if="course.level" class="course-level">{{ course.level }}</span>
          </CourseCover>
          <div class="course-info">
            <small>{{ termLabel(course.year, course.semester) }}</small>
            <strong>{{ course.name }}</strong>
            <ul class="course-projects">
              <li v-for="record in course.records.slice(0, 2)" :key="record.id">
                <span :title="record.name">{{ record.name }}</span>
                <ProjectTeam v-if="projectInfo.has(record.id)" :members="projectInfo.get(record.id)!.members" />
              </li>
            </ul>
            <span class="course-action">{{ course.records.length }} 个项目 <ArrowRight :size="16" /></span>
          </div>
        </RouterLink>
      </BorderGlow>
    </div>
    <el-empty v-if="!filtered.length" description="没有符合条件的项目经历">
      <el-button v-if="q || year || status" @click="q = ''; year = ''; status = ''">清除筛选</el-button>
    </el-empty>
    <PortfolioTimeline :records="group?.records || filtered" :projects="s.projects" :tasks="s.tasks" :month="month" @select="goToMonth" />
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
.archive-tools{gap:8px;margin:0 0 14px}.archive-tools .el-input{flex:1 1 205px;max-width:330px}.archive-tools .el-select{width:126px}.archive-tools .actions{margin-left:auto}
.archive-caption{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:0 0 8px}.archive-caption h2{margin:0}
.archive-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(285px,100%),1fr));gap:14px;align-items:start}
.archive-course{min-width:0;display:grid;color:var(--text);border-radius:inherit;overflow:hidden}
.archive-course:hover{text-decoration:none}.archive-course:hover strong{color:var(--primary)}
.archive-course :deep(.course-cover img){aspect-ratio:16/10;object-position:50% 42%}
.course-level{position:absolute;bottom:10px;right:10px;border:1px solid #c8d9ef;border-radius:5px;padding:2px 6px;font-size:12px;color:#2d5491;background:#f6f9fe;font-weight:650}
.course-info{display:grid;gap:3px;padding:12px 14px;min-width:0}
.course-info>strong{font-size:17px;line-height:1.35;overflow-wrap:anywhere}
.course-info>small,.archive-project small{color:#52647c;font-size:12px}
.course-projects{list-style:none;padding:0;margin:4px 0 0;min-width:0}
.course-projects li{display:flex;justify-content:space-between;align-items:center;gap:5px;border-top:1px solid var(--line);min-height:33px;font-size:13px}
.course-projects li>span:first-child{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.course-action{display:flex;justify-content:space-between;align-items:center;font-size:13px;color:#365b93;margin-top:5px}
.archive-project{min-width:0}
.archive-project header{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:6px 8px;padding:10px 12px}
.archive-project header strong{font-size:16px;overflow-wrap:anywhere}
.archive-project header small{grid-column:2/-1}
.archive-project header :deep(.team-stack){grid-column:2}
.archive-project header :deep(.status){grid-column:3;justify-self:end}
.archive-project-detail{display:grid;gap:9px;padding:12px 14px;border-top:1px solid var(--line);overflow-wrap:anywhere}
.archive-project-detail p{margin:0;white-space:pre-wrap}
.archive-project-detail>a{display:flex;align-items:center;gap:5px;width:fit-content}
@media(max-width:700px){.archive-tools .actions{margin-left:0}.archive-tools .el-input{max-width:none}.archive-tools .el-select{flex:1 1 110px}}
.archive-grid{grid-template-columns:repeat(auto-fill,minmax(min(270px,100%),310px));gap:10px}
.archive-course,.archive-project{height:205px;overflow:auto}.archive-course{grid-template-rows:64px 1fr}.archive-course :deep(.course-cover){aspect-ratio:auto}.course-info{padding:8px 12px;align-content:start}.archive-project-detail{gap:5px;padding:8px 12px}.course-projects li{min-height:25px}
</style>
