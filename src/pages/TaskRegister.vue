<script setup lang="ts">
import {computed, ref} from 'vue'
import {Search, Plus} from 'lucide-vue-next'
import {useStore} from '../store'

import {taskModeName} from '../taskMode'
import {latestTerm, semesters} from '../academic'
import {teachingClasses, taskPath, classPath, intentPath} from '../teacher'
import PageHead from '../components/PageHead.vue'
import Status from '../components/Status.vue'
import Deadline from '../components/Deadline.vue'

const store = useStore()
const s = computed(() => store.state!)
const classes = computed(() => teachingClasses(s.value))

const term = latestTerm(classes.value)
const year = ref(term.academicYear)
const semester = ref(term.semester)
const query = ref('')
const classId = ref('')
const courseId = ref('')
const status = ref('')

/** 班级与课程下拉按当前所选学年学期过滤，避免选到空结果。 */
const termClasses = computed(() =>
  classes.value.filter(c => c.academicYear === year.value && c.semester === semester.value))
const courses = computed(() =>
  s.value.courses.filter(c => termClasses.value.some(tc => tc.courseId === c.id)))

const rows = computed(() => s.value.tasks.filter(t =>
  termClasses.value.some(c => c.id === t.classId)
  && (!classId.value || classId.value === t.classId)
  && (!courseId.value || courseId.value === t.courseId)
  && (!status.value || status.value === t.status)
  && t.name.includes(query.value))
  .sort((a, b) => a.due.localeCompare(b.due)))

const projects = (taskId: string) => s.value.projects.filter(p => p.taskId === taskId)

function clear() {
  query.value = ''
  classId.value = ''
  courseId.value = ''
  status.value = ''
  const t = latestTerm(classes.value)
  year.value = t.academicYear
  semester.value = t.semester
}
</script>
<template>
  <PageHead title="实训任务总表">
    <el-button type="primary" @click="$router.push(intentPath('publish'))"><Plus :size="16" />发布教学任务</el-button>
  </PageHead>
  <div class="toolbar task-register-filters">
    <el-select v-model="year" aria-label="学年">
      <el-option v-for="y in [...new Set(classes.map(c => c.academicYear))].sort().reverse()" :key="y" :value="y" />
    </el-select>
    <el-select v-model="semester" aria-label="学期">
      <el-option v-for="term in semesters" :key="term" :value="term" />

    </el-select>
    <el-input v-model="query" clearable placeholder="搜索设计任务">
      <template #prefix><Search :size="16" /></template>
    </el-input>
    <el-select v-model="classId" clearable placeholder="全部教学班级" aria-label="筛选教学班级">
      <el-option v-for="c in termClasses" :key="c.id" :value="c.id" :label="c.name" />
    </el-select>
    <el-select v-model="courseId" clearable placeholder="全部关联课程" aria-label="筛选关联课程">
      <el-option v-for="c in courses" :key="c.id" :value="c.id" :label="c.name" />
    </el-select>
    <el-select v-model="status" clearable placeholder="全部状态">
      <el-option v-for="v in ['草稿', '已发布', '已关闭']" :key="v" :value="v" />
    </el-select>
    <span class="muted">共 {{rows.length}} 项</span>
  </div>
  <el-table :data="rows" class="task-register-table" size="small">
    <el-table-column label="发布内容" min-width="250">
      <template #default="{row: t}">
        <RouterLink :to="taskPath(t.classId, t.id)" class="strong">{{t.name}}</RouterLink>
        <div class="table-sub">{{taskModeName(t)}}<template v-if="t.taskKind!=='exercise'"> · {{t.milestones.length}} 个阶段</template></div>
      </template>
    </el-table-column>
    <el-table-column label="教学班级 / 关联课程" min-width="240">
      <template #default="{row: t}">
        <RouterLink :to="classPath(t.classId)">{{classes.find(c => c.id === t.classId)?.name}}</RouterLink>
        <div class="table-sub">{{s.courses.find(c => c.id === t.courseId)?.name}}</div>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="110">
      <template #default="{row: t}"><Status :value="t.status" /></template>
    </el-table-column>
    <el-table-column label="截止日期" min-width="155"><template #default="{row}"><Deadline :value="row.due" /></template></el-table-column>
    <el-table-column label="作答 / 申请" min-width="150">
      <template #default="{row: t}">
        <span v-if="t.taskKind==='exercise'">{{projects(t.id).filter(p=>p.submissions.length).length}} 人已作答</span>
        <RouterLink v-else :to="classPath(t.classId) + '/projects?taskId=' + t.id">{{projects(t.id).length}} 个项目</RouterLink>
        <div v-if="t.taskKind!=='exercise'" class="table-sub">{{projects(t.id).filter(p => p.status === '待审核').length}} 个申请待审</div>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="160" fixed="right" align="right">
      <template #default="{row: t}"><el-button @click="$router.push(taskPath(t.classId, t.id))">进入课程任务</el-button></template>
    </el-table-column>
    <template #empty>
      <el-empty description="没有符合条件的设计任务"><el-button @click="clear">清除筛选</el-button></el-empty>
    </template>
  </el-table>
</template>

<style scoped>
.task-register-filters{gap:8px;margin-bottom:10px}
.task-register-table :deep(.el-table__cell){padding-block:5px}
.task-register-table :deep(.el-button){height:28px}
</style>
