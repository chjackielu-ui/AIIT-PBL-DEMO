<script setup lang="ts">
import {computed, ref} from 'vue'
import {useStore} from '../store'

import {latestTerm, relevantClasses} from '../academic'
import TermFilter from '../components/TermFilter.vue'
import PageHead from '../components/PageHead.vue'
import IdentityCard from '../components/IdentityCard.vue'
import ProjectGrid from '../components/ProjectGrid.vue'
import {taskModeName,canStartTask,beforeDeadline} from '../taskMode'
import {stamp} from '../lib'
import {phaseStatus,projectPhases} from '../projectPresentation'
import NotificationList from '../components/NotificationList.vue'

const store = useStore()
const s = computed(() => store.state!)
const role = computed(() => store.user!.role)

const term = latestTerm(relevantClasses(s.value))
const year = ref(term.academicYear)
const semester = ref(term.semester)

/** 学生工作台按相关班级过滤；管理员查看全部教学班（服务端已按权限下发）。 */
const classes = computed(() =>
  relevantClasses(s.value).filter(c => c.academicYear === year.value && c.semester === semester.value))
const tasks = computed(() => s.value.tasks.filter(t => classes.value.some(c => c.id === t.classId)))
const records = computed(() => s.value.projects.filter(p => tasks.value.some(t => t.id === p.taskId)))
const projects = computed(() => records.value.filter(p => tasks.value.find(t => t.id === p.taskId)?.taskKind !== 'exercise'))

const availableTasks=computed(()=>tasks.value.filter(t=>canStartTask(t) && !records.value.some(p=>p.taskId===t.id && p.memberIds.includes(store.user!.id))))
const pendingTasks=computed(()=>records.value.filter(p=>p.memberIds.includes(store.user!.id) && !['待审核','待修改','已关闭','已归档'].includes(p.status)).flatMap(p=>projectPhases(tasks.value.find(t=>t.id===p.taskId),p).filter(m=>m.requiresSubmission!==false || m.isFinal).map(m=>({p,m,status:phaseStatus(p,m,store.user?.id)}))).filter(r=>['未提交','退回修改'].includes(r.status)).sort((a,b)=>a.m.due.localeCompare(b.m.due)))
</script>
<template>
  <PageHead :title="role === 'admin' ? '管理员工作台' : '学生工作台'">
    <TermFilter :classes="relevantClasses(s)" v-model:year="year" v-model:semester="semester" />
  </PageHead>
  <IdentityCard>
    <template v-if="role === 'student'">
      <el-button type="primary" @click="$router.push('/student/tasks')">参与实训任务</el-button>
      <el-button @click="$router.push('/student/projects')">项目进度与组队</el-button>
      <el-button @click="$router.push('/student/portfolio')">个人档案</el-button>
    </template>
    <template v-else>
      <el-button type="primary" @click="$router.push('/admin/course-import')">导入小学期课程</el-button>
      <el-button @click="$router.push('/admin/users')">账号管理</el-button>
    </template>
  </IdentityCard>
  
  <section v-if="role==='student' && availableTasks.length" class="section cold-start"><div class="section-head"><h2>待开始的班级任务与课程项目</h2><RouterLink to="/student/tasks">可参与的任务 →</RouterLink></div><article v-for="t in availableTasks.slice(0,4)" :key="t.id" class="home-pending-row"><div><strong>{{t.name}}</strong><small class="muted">{{taskModeName(t)}}</small></div><el-button type="primary" @click="$router.push(t.taskKind==='exercise'?'/student/tasks/'+t.id:'/student/projects/create/'+t.id)">{{t.taskKind==='exercise'?'开始作答':'申请项目'}}</el-button></article></section>
  <div :class="{'student-home-columns':role==='student'}">
  <section class="section">
    <div class="section-head">
      <h2>{{role === 'student' ? '我的课程项目' : '最近项目'}}</h2>
      <RouterLink :to="`/${role}/projects`">查看全部</RouterLink>
    </div>
    <ProjectGrid :projects="[...projects].sort((a, b) => b.created.localeCompare(a.created)).slice(0, 8)" />
  </section>
  <section v-if="role==='student'" class="section"><div class="section-head"><h2>待完成的任务</h2><RouterLink to="/student/submissions">查看全部</RouterLink></div><article v-for="r in pendingTasks.slice(0,6)" :key="r.p.id+r.m.id" class="home-pending-row"><span class="task-state-dot" :class="{overdue:!beforeDeadline(r.m.due)}" /><div><strong>{{r.m.name}}</strong><small class="muted">{{r.p.name}} · 截止 {{stamp(r.m.due)}}</small></div><el-button type="primary" plain @click="$router.push(`/student/projects/${r.p.id}/submit/${r.m.id}`)">{{r.status==='退回修改'?'修改并提交':'查看 / 提交'}}</el-button></article><el-empty v-if="!pendingTasks.length" description="暂无待完成的任务" :image-size="48" /></section>
  </div>
  <section class="section">
    <div class="section-head">
      <h2>通知</h2>
      <RouterLink to="/notifications">查看全部</RouterLink>
    </div>
    <NotificationList :notices="s.notifications.slice(0, 3)" />
    <el-empty v-if="!s.notifications.length" description="暂无通知" :image-size="40" />
  </section>
</template>

