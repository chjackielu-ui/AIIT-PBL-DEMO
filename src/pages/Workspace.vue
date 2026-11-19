<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, Download, Share2} from 'lucide-vue-next'

import {useStore} from '../store'

import {run, confirmAction, printPage} from '../lib'
import {taskPath, classPath, classSection, isReviewKind} from '../teacher'
import {phaseStatus,pendingTaskCount} from '../projectPresentation'
import ProjectScores from '../components/ProjectScores.vue'
import {ElMessage} from 'element-plus'
import ProjectDecisionBar from '../components/ProjectDecisionBar.vue'
import PageHead from '../components/PageHead.vue'
import Status from '../components/Status.vue'
import FileLibrary from '../components/FileLibrary.vue'
import Members from '../components/Members.vue'
import SharePanel from '../components/SharePanel.vue'
import TeacherContext from '../components/TeacherContext.vue'
import OverviewPanel from '../components/workspace/OverviewPanel.vue'
import BoardPanel from '../components/workspace/BoardPanel.vue'
import ActivityPanel from '../components/workspace/ActivityPanel.vue'

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)

/** 教师要求任务属于当前班级路径；学生必须是成员。 */
const p = computed(() => s.value.projects.find(p =>
  p.id === route.params.id
  && (store.user?.role !== 'teacher' || (route.path.startsWith('/teacher/reviews/projects/') || s.value.tasks.some(t => t.id === p.taskId && t.classId === route.params.classId)))))

const inApplicationReview=computed(()=>route.path.startsWith('/teacher/reviews/projects/'))
const teacher = computed(() => store.user?.role === 'teacher')
const leader = computed(() => p.value?.leaderId === store.user?.id)
const viewer = computed(() => store.user?.role === 'student' ? store.user.id : undefined)
const readonly = computed(() => !p.value || store.user?.role === 'admin' || ['待审核', '待修改', '已归档', '已关闭'].includes(p.value.status))

const tabs = [
  {id: 'overview', name: '概览'},
  {id: 'board', name: '任务看板'},
  {id: 'files', name: '资料库'},
  {id: 'members', name: '团队与记录'},
] as const
type TabId = typeof tabs[number]['id']

function validTab(value: unknown): TabId {
  if (value === 'final' || value === 'milestones') return 'board'
  if (value === 'activity' || value === 'share') return 'members'
  return tabs.some(t => t.id === value) ? value as TabId : 'overview'
}

/** tab 与 URL 单向同步：URL 是唯一事实来源，点击 tab 走 setTab（由 watch 统一写入 URL）。 */
const tab = ref<TabId>(validTab(route.query.tab))
watch(() => route.query.tab, v => { tab.value = validTab(v) })
watch(tab, value => {
  if (value !== validTab(route.query.tab)) router.replace({query: {...route.query, tab: value}})
})

function setTab(value: string) {
  if(value === 'share'){shareOpen.value=true;return}
  if(value === 'discussion'){router.push(`/${store.user?.role}/discussions/${p.value?.id}`);return}
  tab.value = validTab(value)
}

/** 教师从审核列表进入时返回原审核列表；否则回到班级项目列表。 */
const backLink = computed(() => {
  if (!teacher.value) return typeof route.query.origin === 'string' ? route.query.origin : `/${store.user?.role}/projects`
  if (route.query.from === 'achievements') return '/teacher/reviews/achievements'
  if (inApplicationReview.value) return '/teacher/reviews/projects'
  if (route.query.from === 'applications') return '/teacher/reviews/projects'
  const classId = String(route.params.classId)
  const from = route.query.from
  return isReviewKind(from) ? classSection(classId, `reviews/${from}`) : classSection(classId, 'projects')
})


const pending=computed(()=>p.value?pendingTaskCount(s.value,p.value,teacher.value?undefined:store.user?.id):0)
const shareOpen = ref(route.query.tab === 'share')
// —— 项目编辑弹窗 ——
const editing = ref(false)
const edit = reactive({name: '', description: '', goal: '', tags: '', memberIds: [] as string[]})
const task = computed(() => s.value.tasks.find(t => t.id === p.value?.taskId))
const boardFilter=ref('待完成')
const boardMilestones=computed(()=>task.value?.milestones.filter(m=>{
  if(!p.value || m.requiresSubmission===false && !m.isFinal)return false
  const status=phaseStatus(p.value,m,viewer.value)
  if(boardFilter.value==='全部')return true
  if(boardFilter.value==='已完成')return status==='已通过'
  if(boardFilter.value==='已过期')return ['未提交','退回修改'].includes(status) && m.due<new Date().toLocaleDateString('sv-SE')
  return status!=='已通过'
}) || [])
const eligibleMembers = computed(() =>
  s.value.users.filter(u =>
    u.role === 'student' && u.enabled
    && (task.value?.crossClass || s.value.enrollments.some(e => e.studentId === u.id && e.classId === task.value?.classId))))

function openEdit() {
  if (p.value) Object.assign(edit, {
    name: p.value.name, description: p.value.description, goal: p.value.goal,
    tags: p.value.tags, memberIds: [...p.value.memberIds],
  })
  editing.value = true
}
async function saveEdit() {
  if (!edit.name.trim() || !edit.description.trim() || !edit.goal.trim()) {
    ElMessage.warning('项目名称、简介与目标为必填项')
    return
  }
  await run(async () => {
    await store.act('editProject', {projectId: p.value!.id, ...edit})
    editing.value = false
  }, teacher.value ? '项目信息已更新' : '申请已重新提交')
}
</script>
<template>
  <div v-if="p" :class="{'review-detail':inApplicationReview}">
    <div v-if="inApplicationReview" class="review-context"><RouterLink to="/teacher/reviews/projects">项目申请与审核</RouterLink> / {{p.name}}</div>
    <TeacherContext v-if="teacher && !inApplicationReview" label="项目工作区" />
    <PageHead :title="p.name">
      <el-button @click="router.push(backLink)"><ArrowLeft :size="16" />返回列表</el-button>

      <el-button v-if="(teacher && !['已归档', '已关闭'].includes(p.status)) || (leader && p.status === '待修改')" @click="openEdit">{{teacher ? '编辑项目' : '修改申请'}}</el-button>
      <el-button v-if="teacher && p.status === '已归档'" type="primary" @click="confirmAction('项目将移出归档并恢复编辑。', () => store.act('reopen', {projectId: p!.id}))">移出归档</el-button>
      <el-button @click="printPage('project', p.id)"><Download :size="16" />项目介绍 PDF</el-button>
      <el-button @click="setTab('share')"><Share2 :size="16" />项目分享</el-button>
    </PageHead>
    <div class="project-meta">
      <Status :value="p.status" />
      <span>{{task?.name}}</span>
      <span>{{p.memberIds.length}} 名成员</span>
      <span>{{p.created}}</span>
    </div>
    <div v-if="['待审核', '待修改', '已归档', '已关闭'].includes(p.status)" class="info-strip">
      {{p.status === '已归档' ? '项目已归档，协作内容只读。教师可移出归档后继续修改。'
        : p.status === '待审核' ? '申请已提交，成员名单已锁定。审核通过后开放项目协作。'
        : p.status === '待修改' ? '请负责人修改申请文字后重新提交，成员名单由教师调整。'
        : '项目已关闭。'}}
      <span v-if="p.reviewComment"> 教师意见：{{p.reviewComment}}</span>
    </div>
    <section class="section project-persistent-description"><h2>项目说明</h2><p class="pre-wrap">{{p.description}}</p><p class="pre-wrap"><strong>项目目标：</strong>{{p.goal}}</p><el-collapse v-if="task"><el-collapse-item title="课程任务说明"><p class="pre-wrap">{{task.description}}</p><ProjectScores :project="p" :task="task" /></el-collapse-item></el-collapse></section>
    <el-tabs :model-value="tab" class="workspace-tabs" @tab-change="(name: unknown) => setTab(String(name))">
      <el-tab-pane v-for="t in tabs" :key="t.id" :name="t.id"><template #label>{{t.name}}<span v-if="t.id==='board' && pending" class="pending-count">{{pending}}</span></template></el-tab-pane>
    </el-tabs>
    <div>
      <OverviewPanel v-if="tab === 'overview'" :project="p" @goto="setTab" />
      <div v-else-if="tab === 'board'" class="project-board-layout">
        <BoardPanel v-model:filter="boardFilter" :project="p" :readonly="readonly" />
        <section class="section board-milestones"><h2>课程任务</h2><div v-for="m in boardMilestones" :key="m.id" class="board-milestone"><strong>{{m.name}}</strong><small class="muted">截止 {{m.due}}</small><div class="actions"><Status :value="phaseStatus(p,m,viewer)" /><el-button size="small" :type="phaseStatus(p,m,viewer)==='未提交'?'primary':'default'" @click="router.push(teacher ? taskPath(task!.classId,task!.id)+'/milestones/'+m.id : `/student/projects/${p.id}/submit/${m.id}`)">{{teacher?'提交批改':'查看 / 提交'}}</el-button></div></div><el-empty v-if="!boardMilestones.length" description="当前分类暂无课程任务" :image-size="48" /></section>
      </div>
      <FileLibrary v-else-if="tab === 'files'" :project="p" :readonly="readonly" />
      <section v-else-if="tab === 'members'"><Members :project="p" :readonly="readonly" /><ActivityPanel :project="p" /></section>
    </div>


    <el-dialog v-model="shareOpen" title="项目分享" width="min(800px, 95vw)"><SharePanel :project="p" /></el-dialog>
    <ProjectDecisionBar v-if="teacher && (inApplicationReview || p.status==='待审核')" :project="p" />
    <el-dialog v-model="editing" :title="teacher ? '编辑项目' : '修改项目申请'" width="640px">
      <el-form label-position="top">
        <el-form-item label="项目名称" required><el-input v-model="edit.name" /></el-form-item>
        <el-form-item label="项目简介" required><el-input v-model="edit.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="项目目标" required><el-input v-model="edit.goal" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="技术标签"><el-input v-model="edit.tags" /></el-form-item>
        <el-form-item label="正式成员名单">
          <el-select v-model="edit.memberIds" multiple filterable :disabled="!teacher">
            <el-option v-for="u in eligibleMembers" :key="u.id" :value="u.id" :label="u.name" :disabled="u.id === p.leaderId" />
          </el-select>
        </el-form-item>
        <p v-if="teacher" class="muted">调整成员会使结项材料失效，团队需重新确认贡献并提交。</p>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">{{teacher ? '保存项目' : '重新提交申请'}}</el-button>
      </template>
    </el-dialog>
  </div>
  <el-result v-else title="项目不存在或无权访问">
    <template #extra>
      <el-button @click="router.push(teacher ? classPath(String(route.params.classId)) + '/projects' : `/${store.user?.role}/projects`)">返回项目列表</el-button>
    </template>
  </el-result>
</template>
