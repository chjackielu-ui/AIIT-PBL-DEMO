<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, CalendarDays} from 'lucide-vue-next'
import {useStore} from '../../store'
import {run,stamp} from '../../lib'
import {isIndividual,taskModeName,canStartTask} from '../../taskMode'
import {classPath, taskPath, teachingClasses} from '../../teacher'
import {phaseStatus, projectPhases} from '../../projectPresentation'
import PageHead from '../../components/PageHead.vue'
import ProjectScores from '../../components/ProjectScores.vue'
import TeacherProjectDialog from '../../components/TeacherProjectDialog.vue'
import Status from '../../components/Status.vue'
import StagePublisher from '../../components/StagePublisher.vue'
import ProjectGrid from '../../components/ProjectGrid.vue'
import TeacherContext from '../../components/TeacherContext.vue'

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)
const teacher = computed(() => store.user?.role === 'teacher')
const tc = computed(() => teachingClasses(s.value).find(c => c.id === route.params.classId))

/** 教师任务必须属于当前班级；学生直接按服务端可见任务查找。 */
const task = computed(() => s.value.tasks.find(t =>
  t.id === route.params.id && (!teacher.value || t.classId === tc.value?.id)))
const classTask = computed(() => task.value?.taskKind === 'exercise')

const listLink = computed(() => teacher.value ? classPath(String(route.params.classId)) : '/student/tasks')
const taskLink = (id: string) => teacher.value ? taskPath(String(route.params.classId), id) : '/student/tasks/' + id

const alreadyJoined = computed(() =>
  s.value.projects.find(p => p.taskId === task.value?.id && p.memberIds.includes(store.user!.id)))
const phases = computed(() => projectPhases(task.value, teacher.value ? undefined : alreadyJoined.value))

const courseName = computed(() => s.value.courses.find(c => c.id === task.value?.courseId)?.name)
const className = computed(() => s.value.teachingClasses.find(c => c.id === task.value?.classId)?.name)
const teacherNames = computed(() =>
  (s.value.teachingClasses.find(c => c.id === task.value?.classId)?.teacherIds || [])
    .map(id => s.value.users.find(u => u.id === id)?.name).join('、'))

const participation=computed(()=>s.value.enrollments.filter(e=>e.classId===task.value?.classId).map(e=>({user:s.value.users.find(u=>u.id===e.studentId),project:stageProjects.value.find(p=>p.memberIds.includes(e.studentId))})))
const activeTab=ref(['projects','publish','grading'].includes(String(route.query.view))?String(route.query.view):'projects')
watch(()=>route.query.view,value=>{activeTab.value=['projects','publish','grading'].includes(String(value))?String(value):'projects'})
watch(activeTab,value=>{if(teacher.value && route.query.view!==value)router.replace({query:{...route.query,view:value}})})
const stageProjects = computed(() => s.value.projects.filter(p => p.taskId === task.value?.id))
const previewOpen=ref(false),previewId=ref('')
const preview=computed(()=>stageProjects.value.find(p=>p.id===previewId.value))
function showProject(id:string){previewId.value=id;previewOpen.value=true}
watch(()=>route.query.project,id=>{if(teacher.value && typeof id==='string')showProject(id)},{immediate:true})
watch(previewOpen,open=>{if(!open && route.query.project){const query={...route.query};delete query.project;router.replace({query})}})
const entering=ref(false)
async function enterExercise(){
 entering.value=true
 await run(async()=>{
  let project=alreadyJoined.value
  if(!project){const r=await store.act('startClassTask',{taskId:task.value!.id});project=s.value.projects.find(p=>p.id===r.id)}
  if(project && task.value?.milestones[0])await router.push(`/student/projects/${project.id}/submit/${task.value.milestones[0].id}`)
 },'')
 entering.value=false
}
</script>
<template>
  <TeacherContext v-if="teacher && tc" label="课程任务" />
  <template v-if="task">
    <PageHead :title="task.name">
      <el-button @click="router.push(listLink)"><ArrowLeft :size="16" />返回列表</el-button>
      <el-button v-if="teacher && task.status !== '已关闭'" type="primary" @click="router.push(taskLink(task.id) + '/edit')">修改任务</el-button>
      <el-button v-if="!teacher && classTask && (alreadyJoined || task.status==='已发布')" type="primary" :loading="entering" :disabled="!alreadyJoined && !canStartTask(task)" @click="enterExercise">{{alreadyJoined?'查看作答':'开始作答'}}</el-button>
      <el-button v-else-if="!teacher && task.status === '已发布' && !alreadyJoined" type="primary" :disabled="!canStartTask(task)" @click="router.push(`/student/projects/create/${task.id}`)">创建项目</el-button>
      <el-button v-else-if="alreadyJoined && !teacher" type="primary" @click="router.push(`/student/projects/${alreadyJoined.id}`)">进入我的项目</el-button>
    </PageHead>
    <section class="section task-persistent-description">
      <div class="section-head"><h2>{{classTask?'班级任务说明':'课程项目说明'}}</h2><Status :value="task.status" /></div>
      <p class="pre-wrap">{{task.description}}</p>
      <div class="details-grid">
        <div v-if="!teacher"><small>所属课程</small><span>{{courseName}}</span></div>
        <div v-if="!teacher"><small>教学班</small><span>{{className}}</span></div>
        <div v-if="!teacher"><small>任课教师</small><span>{{teacherNames}}</span></div>
        <div v-if="classTask"><small>作答方式</small><span>全班学生独立作答</span></div>
        <div v-else><small>项目类型</small><span>{{taskModeName(task)}} · {{isIndividual(task)?'独立完成':task.min+'–'+task.max+' 人'}}</span></div>
        <div v-if="task.startAt"><small>开始时间</small><span>{{stamp(task.startAt)}}</span></div>
        <div><small>{{classTask?'作答截止':'最终成果截止'}}</small><span>{{stamp(classTask ? task.milestones[0]?.due : task.startAt ? task.due : phases.find(m => m.isFinal)?.due || task.due)}}</span></div>
      </div>
      <el-collapse v-if="!classTask"><el-collapse-item title="项目规则与评分说明"><ProjectScores :task="task" :project="alreadyJoined" /></el-collapse-item></el-collapse>
    </section>
    <section v-if="teacher && classTask && phases[0]" class="section">
      <div class="section-head"><h2>全班作答情况</h2><el-button type="primary" @click="router.push(taskLink(task.id)+'/milestones/'+phases[0].id)">查看作答并批改</el-button></div>
      <el-table :data="participation">
        <el-table-column label="学生" min-width="130"><template #default="{row}">{{row.user?.name}}</template></el-table-column>
        <el-table-column label="作答状态" min-width="120"><template #default="{row}"><Status :value="row.project ? phaseStatus(row.project,phases[0]) : '未提交'" /></template></el-table-column>
      </el-table>
    </section>
    <el-tabs v-if="teacher && !classTask" v-model="activeTab" class="task-function-tabs">
      <el-tab-pane label="查看所有项目" name="projects" />
      <el-tab-pane label="共用阶段与最终验收" name="publish" />
      <el-tab-pane label="提交批改" name="grading" />
    </el-tabs>
    <StagePublisher v-if="teacher && activeTab==='publish' && task.taskKind!=='exercise'" :task="task" />
    <section v-if="!teacher || (!classTask && activeTab==='grading')" class="section compact-milestones">
      <div class="section-head">
        <h2>{{classTask?'作答要求':'阶段与成果验收'}}</h2>
        <div class="actions">
          <span v-if="!classTask">{{phases.length}} 个阶段</span>
          <el-button v-if="teacher && task.status !== '已关闭'" @click="activeTab='publish'">发布 / 修改任务</el-button>
        </div>
      </div>
      <div v-for="(m, i) in phases" :key="m.id" class="milestone-row">
        <span class="step-number">{{i + 1}}</span>
        <div class="milestone-title">
          <h3>{{m.name}}</h3>
          <p v-if="!teacher">{{m.description}}</p>
          <p v-if="!teacher" class="muted">{{m.isFinal ? '最终验收 · ' : ''}}{{m.requireMaterials !== false ? '附件要求：' + m.materials : '仅需提交说明，附件可选'}}</p>
          <p v-if="!teacher && !task.startAt" class="muted">验收：{{m.criteria}}</p>
        </div>
        <div class="milestone-row-actions"><span class="date"><CalendarDays :size="16" />{{stamp(m.due)}}</span><el-button v-if="teacher" type="primary" plain @click="router.push(taskLink(task.id)+'/milestones/'+m.id)">查看全组提交 / 批改</el-button></div>
      </div>
    </section>

    <section class="section" v-if="teacher && !classTask && activeTab==='projects'">
      <div class="section-head"><h2>当前实训任务的项目</h2><span class="muted">{{stageProjects.length}} 个项目</span></div>
      <ProjectGrid :projects="stageProjects" />
      <h3>学生项目创建情况</h3><el-table :data="participation"><el-table-column label="学生" min-width="120"><template #default="{row}">{{row.user?.name}}</template></el-table-column><el-table-column label="创建状态" width="110"><template #default="{row}"><el-tag :type="row.project?'success':'info'">{{row.project?'已创建':'未创建'}}</el-tag></template></el-table-column><el-table-column label="所属项目" min-width="180"><template #default="{row}">{{row.project?.name || '—'}}</template></el-table-column><el-table-column label="操作" width="140" fixed="right"><template #default="{row}"><el-button v-if="row.project" @click="showProject(row.project.id)">查看项目</el-button></template></el-table-column></el-table>
    </section>
    <TeacherProjectDialog v-if="teacher" v-model="previewOpen" :project="preview" />
  </template>
  <el-result v-else title="任务不存在或无权访问">
    <template #extra><el-button @click="router.push(listLink)">返回任务列表</el-button></template>
  </el-result>
</template>
