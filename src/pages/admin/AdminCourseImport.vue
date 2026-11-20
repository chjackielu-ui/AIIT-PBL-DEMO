<script setup lang="ts">
import {computed, ref} from 'vue'
import * as XLSX from 'xlsx'
import {api} from '../../api'
import {run} from '../../lib'
import {useStore} from '../../store'
import PageHead from '../../components/PageHead.vue'

const store = useStore()
const names = ['丛林班','坚冰班','银河班','青松班','山峰班','奋进班']
const columns = {
  '课程标识':'id', '教学班名称':'className', '教师账号':'teacherId', '学年':'academicYear', '学期':'semester',
  '课程状态':'status', '计划开始时间':'plannedStart', '计划结束时间':'plannedEnd',
  '实际开始时间':'actualStart', '实际结束时间':'actualEnd', '评分规则JSON':'gradingRule',
} as const
type Row = Record<string, unknown>
type Preview = {row: number; error: string; data: Row}
const mode = ref<'course' | 'roster'>('course')
const fileInput = ref<HTMLInputElement>()
const busy = ref(false)
const rows = ref<Row[]>([])
const preview = ref<Preview[]>([])
const count = ref<number>()
const endpoint = computed(() => `/admin/${mode.value}-import`)
const errors = computed(() => preview.value.filter(row => row.error).length)
const teachers = computed(() => store.state?.users.filter(user => user.role === 'teacher' && user.enabled) || [])
const courses = computed(() => {
  const state = store.state
  if (!state) return []
  const teacherNames = new Map(state.users.filter(user => user.role === 'teacher').map(user => [user.id, user.name]))
  const enrollments = new Map<string, number>()
  for (const e of state.enrollments) enrollments.set(e.classId, (enrollments.get(e.classId) ?? 0) + 1)
  return state.teachingClasses.filter(c => c.semester === '第三学期').flatMap(c => {
    const course = state.courses.find(item => item.id === c.courseId)
    return course ? [{
      ...course,
      className: c.name,
      teachers: c.teacherIds.map(id => teacherNames.get(id) ?? id).join('、'),
      studentCount: enrollments.get(c.id) ?? 0,
    }] : []
  })
})

function reset() {
  rows.value = []
  preview.value = []
  count.value = undefined
}

function template() {
  const book = XLSX.utils.book_new()
  const defaultTeachers = [1, 1, 2, 2, 2, 3]
  const sample = names.map((name, index) => ({
    '课程标识': `summer26-${index + 1}`, '教学班名称': name,
    '教师账号': store.state?.teachingClasses.find(c => c.name === name && c.semester === '第三学期'
      && c.academicYear === '2026')?.teacherIds[0] ?? teachers.value.find(t => t.id === 'demo-teacher-' + defaultTeachers[index])?.id ?? '',
    '学年': '2026', '学期': '第三学期', '课程状态': '未开始',
    '计划开始时间': '2026-07-08 09:00', '计划结束时间': '2026-07-19 17:00',
    '实际开始时间': '', '实际结束时间': '',
    '评分规则JSON': '{"dimensions":[{"id":"project","name":"项目成果","weight":100,"source":"project"}]}',
  }))
  const sheet = XLSX.utils.json_to_sheet(sample)
  sheet['!cols'] = [18,16,20,10,12,12,23,23,23,23,60].map(wch => ({wch}))
  XLSX.utils.book_append_sheet(book, sheet, '小学期课程导入')
  XLSX.writeFile(book, '2026小学期课程与教学班_样例.xlsx')
}

function readRows(book: XLSX.WorkBook): Row[] {
  if (mode.value === 'roster') {
    const matching = names.filter(name => book.SheetNames.includes('软件-' + name))
    if (!matching.length) throw Error('找不到 24 级软件专业教学班工作表')
    return matching.flatMap(className => XLSX.utils.sheet_to_json<Record<string,string>>(
      book.Sheets['软件-' + className], {raw: false}
    ).map(row => ({number: row['学号']?.trim() || '', name: row['姓名']?.trim() || '', className})))
  }
  const sheet = book.Sheets[book.SheetNames[0]]
  return XLSX.utils.sheet_to_json<Record<string,string>>(sheet, {raw: false, defval: '', dateNF: 'yyyy-mm-dd hh:mm'})
    .map((row, index) => {
      const data: Row = {}
      for (const [column, key] of Object.entries(columns)) {
        const value = String(row[column] ?? '').trim()
        if (key === 'gradingRule' && value) {
          try { data[key] = JSON.parse(value) } catch { throw Error(`第 ${index + 2} 行评分规则不是有效 JSON`) }
        } else data[key] = value
      }
      return data
    })
}

async function choose(event: Event) {
  reset()
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  busy.value = true
  try {
    await run(async () => {
      if (file.size > 5 * 1024 * 1024) throw Error('导入文件上限 5 MB')
      const incoming = readRows(XLSX.read(await file.arrayBuffer(), {type:'array', cellDates:true}))
      const response = await api<{rows: Preview[]}>(endpoint.value + '/preview', {rows: incoming})
      rows.value = incoming
      preview.value = response.rows
    }, '')
  } finally { busy.value = false; input.value = '' }
}

async function commit() {
  busy.value = true
  try {
    await run(async () => {
      const result = await api<{count: number}>(endpoint.value, {rows: rows.value})
      count.value = result.count
      preview.value = []
      rows.value = []
      await store.load()
    }, '导入完成')
  } finally { busy.value = false }
}
</script>

<template>
  <PageHead title="小学期课程导入">
    <el-button @click="template">下载课程与教学班样例</el-button>
  </PageHead>
  <section class="section">
    <el-radio-group v-model="mode" :disabled="busy" @change="reset">
      <el-radio-button value="course">课程与教学班</el-radio-button>
      <el-radio-button value="roster">24 级软件学生名单</el-radio-button>
    </el-radio-group>
    <p v-if="mode === 'course'" class="muted">先在账号管理导入教师。填写教师账号、课程状态、计划时间和评分规则，一行创建一个小学期教学班。</p>
    <p v-else class="muted">先导入课程与教学班，再选择教务名单原表。学生按软件教学班工作表归班。</p>
    <p v-if="mode === 'course' && teachers.length" class="muted">教师账号 {{teachers.map(t => t.id + '（' + t.name + '）').join('、')}}</p>
    <div class="toolbar">
      <el-button type="primary" :loading="busy" @click="fileInput?.click()">选择 Excel 并预览</el-button>
      <input ref="fileInput" type="file" hidden accept=".xlsx,.xls" @change="choose" />
      <el-button v-if="preview.length" :disabled="errors > 0 || busy" @click="commit">确认导入 {{preview.length}} 行</el-button>
    </div>
    <p v-if="count !== undefined" class="success-text">已新增 {{count}} {{mode === 'course' ? '个课程与教学班' : '条选课关系'}}</p>
    <el-table v-if="preview.length" :data="preview" max-height="440">
      <el-table-column prop="row" label="预览行号" width="100" />
      <el-table-column prop="data.className" label="教学班" min-width="115" />
      <el-table-column v-if="mode === 'course'" prop="data.courseName" label="生成的课程名称" min-width="300" />
      <el-table-column v-else prop="data.name" label="学生姓名" min-width="150" />
      <el-table-column prop="data.status" v-if="mode === 'course'" label="课程状态" />
      <el-table-column label="校验结果" min-width="230">
        <template #default="{row}">{{row.error || '可导入'}}</template>
      </el-table-column>
    </el-table>
  </section>
  <section class="section">
    <h2>已导入的小学期课程</h2>
    <el-table :data="courses">
      <el-table-column prop="name" label="课程名称" min-width="310" />
      <el-table-column prop="className" label="教学班" min-width="105" />
      <el-table-column prop="teachers" label="任课教师" min-width="105" />
      <el-table-column prop="studentCount" label="学生人数" width="95" />
      <el-table-column prop="status" label="课程状态" width="110" />
      <el-table-column prop="plannedStart" label="计划开始时间" min-width="150" />
      <el-table-column prop="plannedEnd" label="计划结束时间" min-width="150" />
      <el-table-column prop="actualStart" label="实际开始时间" min-width="150" />
      <el-table-column prop="actualEnd" label="实际结束时间" min-width="150" />
    </el-table>
  </section>
</template>
