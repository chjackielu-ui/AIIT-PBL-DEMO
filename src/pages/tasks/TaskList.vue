<script setup lang="ts">
import {computed, ref,watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {Plus, Search} from 'lucide-vue-next'
import {stamp} from '../../lib'
import {isIndividual,taskModeName,canStartTask,taskStarted} from '../../taskMode'
import type {CourseTask} from '../../types'
import {useStore} from '../../store'
import {latestTerm, relevantClasses} from '../../academic'
import {taskPath, teachingClasses} from '../../teacher'
import PageHead from '../../components/PageHead.vue'
import TermFilter from '../../components/TermFilter.vue'
import Status from '../../components/Status.vue'
import TeacherContext from '../../components/TeacherContext.vue'
import CourseDetails from '../CourseDetails.vue'
import ClassScores from '../../components/ClassScores.vue'
import ClassRoster from '../../components/ClassRoster.vue'

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)
const teacher = computed(() => store.user?.role === 'teacher')
const tc = computed(() => teachingClasses(s.value).find(c => c.id === route.params.classId))

const selectedClass=computed(()=>relevantClasses(s.value).find(c=>c.id===route.query.classId))
const term = selectedClass.value || latestTerm(teacher.value ? teachingClasses(s.value) : relevantClasses(s.value))
const year = ref(term.academicYear)
const semester = ref(term.semester)
const query = ref('')
const status = ref('')
const page = ref(1)
const module=ref(['resources','grading','roster'].includes(String(route.query.module))?String(route.query.module):'tasks')
watch(()=>route.query.module,value=>{module.value=['resources','grading','roster'].includes(String(value))?String(value):'tasks'})
watch(module,value=>{if(teacher.value && route.query.module!==value)router.replace({query:{...route.query,module:value}})})
const sort = ref('due')
const createdFilter=ref(''), modeFilter=ref(''), kindFilter=ref('')
const joined=(id:string)=>s.value.projects.find(p=>p.taskId===id && p.memberIds.includes(store.user!.id))
const canCreate=canStartTask
const createdCount=(t:CourseTask)=>s.value.projects.filter(p=>p.taskId===t.id).length
const rosterCount=(t:CourseTask)=>s.value.enrollments.filter(e=>e.classId===t.classId).length

const taskLink = (id: string) => teacher.value ? taskPath(String(route.params.classId), id) : '/student/tasks/' + id

const filtered = computed(() => s.value.tasks.filter(t =>
  (teacher.value
    ? t.classId === tc.value?.id
    : t.status === '已发布' && relevantClasses(s.value).some(c => c.id === t.classId && c.academicYear === year.value && c.semester === semester.value))
  && (!route.query.classId || t.classId === route.query.classId)
  && (!kindFilter.value || (t.taskKind || 'training')===kindFilter.value)
  && (!modeFilter.value || (isIndividual(t)?'individual':'group')===modeFilter.value)
  && (teacher.value || !createdFilter.value || (createdFilter.value==='created')===!!joined(t.id))
  && t.name.includes(query.value)
  && (!status.value || t.status === status.value))
  .sort((a, b) => sort.value === 'due' ? a.due.localeCompare(b.due) : a.name.localeCompare(b.name, 'zh-CN')))

function resetPage() { page.value = 1 }
</script>
<template>
  <TeacherContext v-if="teacher && tc" />
  <el-result v-if="teacher && !tc" title="教学班级不存在或无任课权限">
    <template #extra><el-button @click="router.push('/teacher/classes')">选择教学班级</el-button></template>
  </el-result>
  <template v-else>
    <PageHead :title="teacher ? '任务列表' : '可参与的任务'">
      <template v-if="teacher">
        <el-button type="primary" @click="router.push(taskLink('create'))"><Plus :size="15" />发布教学任务</el-button>
        <el-button @click="module='roster'">管理班级学生</el-button>
      </template>
    </PageHead>
    <div v-if="!teacher && selectedClass" class="section-head"><h2>{{s.courses.find(c=>c.id===selectedClass!.courseId)?.name}} · {{selectedClass.name}}</h2><el-button @click="router.push('/student/classes/'+selectedClass.id+'/details')">课程说明与规则</el-button></div>
    <el-tabs v-if="teacher" v-model="module" class="class-module-tabs"><el-tab-pane name="tasks" label="任务中心" /><el-tab-pane name="roster" label="班级学生" /><el-tab-pane name="resources" label="资料库" /><el-tab-pane name="grading" label="班级评分与评分细则" /></el-tabs>
    <div v-if="!teacher || module==='tasks'" class="toolbar" :class="{'class-task-toolbar':teacher}">
      <template v-if="!teacher || module==='tasks'">
      <TermFilter v-if="!teacher" :classes="relevantClasses(s)" v-model:year="year" v-model:semester="semester" />
      <el-input v-model="query" placeholder="搜索任务" clearable @input="resetPage">
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="kindFilter" clearable placeholder="发布类型" @change="resetPage"><el-option value="exercise" label="班级任务" /><el-option value="training" label="课程项目" /></el-select>
      <el-select v-if="teacher" v-model="status" clearable placeholder="全部状态" @change="resetPage">
        <el-option v-for="v in ['草稿', '已发布', '已关闭']" :key="v" :value="v" />
      </el-select>
      <el-select v-if="!teacher" v-model="createdFilter" clearable placeholder="项目创建状态" @change="resetPage"><el-option value="created" label="已创建" /><el-option value="uncreated" label="未创建" /></el-select>
      <el-select v-model="modeFilter" clearable placeholder="项目类型" @change="resetPage"><el-option value="individual" label="个人项目" /><el-option value="group" label="小组集体项目" /></el-select>
      <el-select v-model="sort" aria-label="任务排序" @change="resetPage">
        <el-option value="due" label="截止时间排序" />
        <el-option value="name" label="任务名称排序" />
      </el-select>
      </template>
    </div>
    <template v-if="!teacher || module==='tasks'"><p class="muted">共 {{filtered.length}} 项任务</p>
    <div class="training-task-list">
      <article v-for="t in filtered.slice((page - 1) * 8, page * 8)" :key="t.id" class="training-task-row">
        <div class="task-main">
          <RouterLink :to="taskLink(t.id)" class="card-title">{{t.name}}</RouterLink>
          <p v-if="!teacher">{{s.teachingClasses.find(c => c.id === t.classId)?.name}}</p>
          <div class="task-row-facts">
            <span>{{taskModeName(t)}} · {{isIndividual(t)?'每人独立提交':t.min+'–'+t.max+' 人，按阶段要求提交'}}</span>
            <span>{{t.taskKind==='exercise'?'班级任务':'课程项目 · '+t.milestones.length+' 个共用阶段'}}</span>
            <span>截止 {{stamp(t.due)}}</span>
          </div>
        </div>
        <span v-if="teacher" class="muted">已创建 {{createdCount(t)}} {{isIndividual(t)?'/ '+rosterCount(t)+' 人':'组'}}</span>
        <el-tag v-else :type="joined(t.id)?'success':'info'">{{joined(t.id)?'已创建':'未创建'}}</el-tag>
        <Status :value="t.status" />
        <el-button v-if="!teacher" type="primary" :disabled="!joined(t.id) && !canCreate(t)" @click="router.push(t.taskKind==='exercise'?taskLink(t.id):joined(t.id)?'/student/projects/'+joined(t.id)!.id:'/student/projects/create/'+t.id)">{{t.taskKind==='exercise'?(joined(t.id)?'查看作答':canCreate(t)?'开始作答':!taskStarted(t)?'尚未开始':'作答已截止'):joined(t.id)?'进入项目':canCreate(t)?'申请项目':!taskStarted(t)?'尚未开始':'申请已截止'}}</el-button><el-button plain @click="router.push(taskLink(t.id))">查看任务</el-button>
      </article>
    </div>
    <el-empty v-if="!filtered.length" :description="!teacher && !relevantClasses(s).length?'尚未加入教学班，请任课教师添加选课关系后发布任务':!teacher && !s.tasks.length?'教师尚未发布班级任务或课程项目':'没有符合条件的任务'">
      <el-button @click="query = ''; status = '';createdFilter='';modeFilter=''">清除筛选</el-button>
    </el-empty>
    <el-pagination v-if="filtered.length > 8" v-model:current-page="page" :page-size="8" :total="filtered.length" layout="prev,pager,next" />

    </template>
    <CourseDetails v-if="teacher && module==='resources'" :key="String(route.params.classId)+'resources'" embedded panel="resources" />
    <template v-if="teacher && tc && module==='grading'"><CourseDetails :key="tc.id+'rules'" embedded panel="rules" /><ClassScores :class-id="tc.id" /></template>
    <ClassRoster v-if="teacher && tc && module==='roster'" :class-id="tc.id" />
  </template>
</template>
