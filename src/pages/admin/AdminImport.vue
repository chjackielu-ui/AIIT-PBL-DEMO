<script setup lang="ts">
import {computed, ref} from 'vue'
import * as XLSX from 'xlsx'
import {Download, Upload} from 'lucide-vue-next'
import {useStore} from '../../store'
import {run} from '../../lib'
import {colleges, currentTerm, latestTerm} from '../../academic'
import PageHead from '../../components/PageHead.vue'
import TermFilter from '../../components/TermFilter.vue'

const store = useStore()
const s = computed(() => store.state!)

type ImportKind = 'users' | 'courses' | 'classes' | 'teachingClasses' | 'enrollments'
type DataRow = Record<string, unknown>

interface Spec {
  title: string
  columns: Record<string, string> // 中文列名 → 字段名
  /** 空白单元格使用快捷字段填充的列 */
  quickFill: string[]
}

const specs: Record<ImportKind, Spec> = {
  users: {
    title: '学生 / 教师账号',
    columns: {'用户标识': 'id', '姓名': 'name', '角色': 'role', '学号': 'number', '行政班标识': 'classId'},
    quickFill: [],
  },
  courses: {
    title: '课程',
    columns: {'课程标识': 'id', '课程名称': 'name'},
    quickFill: [],
  },
  classes: {
    title: '行政班',
    columns: {'行政班标识': 'id', '行政班名称': 'name', '学院': 'college', '专业': 'major'},
    quickFill: ['college', 'major'],
  },
  teachingClasses: {
    title: '教学班',
    columns: {'教学班标识': 'id', '教学班名称': 'name', '课程标识': 'courseId', '教师标识': 'teacherIds', '学年': 'academicYear', '学期': 'semester'},
    quickFill: ['academicYear', 'semester'],
  },
  enrollments: {
    title: '学生选课关系',
    columns: {'学生标识': 'studentId', '教学班标识': 'classId'},
    quickFill: [],
  },
}

const kind = ref<ImportKind>('users')
const input = ref<HTMLInputElement>()
const busy = ref(false)
const filename = ref('')
const checked = ref<{row: number; data: DataRow; error: string}[]>([])
const importResult = ref<number>()
const selectedTab = ref<ImportKind>('users')

const term = latestTerm(s.value.teachingClasses)
const year = ref(term.academicYear)
const semester = ref(term.semester)

const currentRows = computed<DataRow[]>(() => {
  if (selectedTab.value === 'teachingClasses') {
    return (s.value.teachingClasses
      .filter(c => c.academicYear === year.value && c.semester === semester.value)) as unknown as DataRow[]
  }
  return s.value[selectedTab.value] as unknown as DataRow[]
})

// —— 快捷字段（模板下载与空白单元格默认使用） ——
const college = ref('计算机与软件工程学院')
const major = ref('软件')
const grade = ref('24')
const sequence = ref('03')
const classExample = computed(() => major.value + grade.value + sequence.value)
/** 学号示例：全站唯一来源（文案与模板示例一致）。 */
const studentNumberExample = computed(() => '3' + grade.value + '3099001')

const quickFields = computed<Record<string, string>>(() => ({
  college: college.value,
  major: major.value,
  name: classExample.value,
  role: 'student',
  number: studentNumberExample.value,
  classId: s.value.classes.find(c => c.name === classExample.value)?.id || 'b_new',
  academicYear: currentTerm().academicYear,
  semester: currentTerm().semester,
}))

const templateExample = computed<Record<string, string>>(() => {
  const example: Record<string, string> = (() => {
    switch (kind.value) {
      case 'users': return {用户标识: 's11', 姓名: '新同学', 角色: 'student', 学号: studentNumberExample.value, 行政班标识: quickFields.value.classId}
      case 'courses': return {课程标识: 'c4', 课程名称: '移动应用实践'}
      case 'classes': return {行政班标识: 'b_new', 行政班名称: classExample.value, 学院: college.value, 专业: major.value}
      case 'teachingClasses': return {教学班标识: 'tc3', 教学班名称: '移动应用实践 C 班', 课程标识: 'c1', 教师标识: 't1', 学年: currentTerm().academicYear, 学期: currentTerm().semester}
      case 'enrollments': return {学生标识: 's11', 教学班标识: 'tc1'}
    }
  })() as unknown as Record<string, string>
  return example
})

const errors = computed(() => checked.value.filter(r => r.error).length)

function resetImport() {
  checked.value = []
  filename.value = ''
  importResult.value = undefined
}

function template() {
  const spec = specs[kind.value]
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet([templateExample.value]), '导入数据')
  XLSX.writeFile(workbook, `安信工_${spec.title}_模板.xlsx`)
}

function applyQuickFill(row: DataRow) {
  const spec = specs[kind.value]
  for (const field of spec.quickFill) {
    if (!row[field]) row[field] = quickFields.value[field]
  }
}

async function parse(event: Event) {
  resetImport()
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  filename.value = file.name
  busy.value = true
  await run(async () => {
    if (file.size > 5 * 1024 * 1024) throw Error('导入文件上限 5 MB')
    if (!/\.xlsx?$/i.test(file.name)) throw Error('请选择 .xlsx 或 .xls 文件')
    const book = XLSX.read(await file.arrayBuffer(), {type: 'array'})
    const sheet = book.Sheets[book.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, {defval: '', raw: false})
    const spec = specs[kind.value]
    const header = (XLSX.utils.sheet_to_json(sheet, {header: 1})[0] || []) as string[]
    // 可快捷填充的列（学院/专业/学年/学期）允许旧版模板缺列，空白单元格自动补默认值
    const missing = Object.keys(spec.columns).filter(cn =>
      !header.includes(cn) && !spec.quickFill.includes(spec.columns[cn]))
    if (missing.length) throw Error('缺少必需列：' + missing.join('、'))
    const data: DataRow[] = rows.map(row => {
      const obj: DataRow = {}
      for (const [cn, key] of Object.entries(spec.columns)) {
        obj[key] = String(row[cn] || '').trim()
      }
      if (kind.value === 'teachingClasses' && obj.teacherIds) {
        obj.teacherIds = String(obj.teacherIds).split(/[,，;；]+/).map(x => x.trim()).filter(Boolean)
      }
      applyQuickFill(obj)
      return obj
    })
    const result = await store.act('importPreview', {kind: kind.value, rows: data})
    checked.value = result.rows
  }, '')
  busy.value = false
  if (input.value) input.value.value = ''
}

async function confirmImport() {
  busy.value = true
  await run(async () => {
    const result = await store.act('importConfirm', {kind: kind.value, rows: checked.value.map(r => r.data)})
    importResult.value = result.count
    checked.value = []
    selectedTab.value = kind.value
    // 导入新学年学期的教学班后，重置筛选避免新行不可见
    if (kind.value === 'teachingClasses') {
      const t = latestTerm(s.value.teachingClasses)
      year.value = t.academicYear
      semester.value = t.semester
    }
  }, '基础数据已导入')
  busy.value = false
}
</script>
<template>
  <PageHead title="基础数据导入">
    <el-button @click="template"><Download :size="16" />下载 Excel 模板</el-button>
  </PageHead>
  <el-steps :active="importResult != null ? 3 : checked.length ? 2 : 0" finish-status="success" simple>
    <el-step title="选择文件" />
    <el-step title="预览与校验" />
    <el-step title="确认导入" />
    <el-step title="导入结果" />
  </el-steps>
  <section class="section import-section">
    <div class="section-head">
      <h2>选择导入内容</h2>
      <el-select v-model="kind" aria-label="导入类型" @change="resetImport">
        <el-option v-for="(spec, key) in specs" :key="key" :value="key" :label="spec.title" />
      </el-select>
    </div>
    <div v-if="['classes', 'users', 'teachingClasses'].includes(kind)" class="import-presets">
      <div class="section-head">
        <strong>快捷字段 · 下载模板和空白单元格默认使用</strong>
        <span class="muted">班级示例 {{classExample}} · 学号示例 {{studentNumberExample}}（10 位）</span>
      </div>
      <div class="form-grid">
        <el-select v-model="college" aria-label="学院" @change="major = colleges[college][0]">
          <el-option v-for="(_, c) in colleges" :key="c" :value="c" />
        </el-select>
        <el-select v-model="major" aria-label="专业">
          <el-option v-for="m in colleges[college]" :key="m" :value="m" />
        </el-select>
        <el-input v-model="grade" maxlength="2" aria-label="年级缩写" placeholder="年级，如 24" />
        <el-input v-model="sequence" maxlength="2" aria-label="班级序列号" placeholder="班级序列，如 03" />
      </div>
      <div class="tags">
        <span v-for="(v, k) in quickFields" :key="k">{{k}}：{{v}}</span>
      </div>
      <p class="muted">名称按“专业简称 + 年级 + 班序”填写，如软件2403。学号采用类似 {{studentNumberExample}} 的 10 位示例；账号导入时学号与行政班标识需逐行填写，模板示例仅作格式参考。</p>
    </div>
    <div v-file-drop class="toolbar">
      <el-button @click="template"><Download :size="16" />模板与示例</el-button>
      <el-button type="primary" :loading="busy" @click="input?.click()"><Upload :size="16" />选择或拖入 Excel 文件</el-button>
      <input ref="input" type="file" :disabled="busy" accept=".xlsx,.xls" hidden @change="parse" />
      <span class="muted">{{filename || '.xlsx / .xls，最大 5 MB，最多 1000 行'}}</span>
    </div>
    <p class="muted">按“行政班 → 账号 → 课程 → 教学班 → 选课关系”准备依赖数据。角色值为 student 或 teacher；教师标识用逗号分隔。</p>
    <p class="muted">基础方案仅新增数据；重复关键标识不覆盖。学生导入后还需导入选课关系，才能加入对应教学班。</p>
    <div v-if="checked.length">
      <div class="section-head">
        <h2>数据预览</h2>
        <span :class="errors ? 'error-text' : 'success-text'">{{checked.length}} 行 · {{errors}} 行错误</span>
      </div>
      <el-table :data="checked" max-height="420">
        <el-table-column prop="row" label="Excel 行号" width="110" />
        <el-table-column v-for="(key, cn) in specs[kind].columns" :key="key" :label="String(cn)" min-width="140">
          <template #default="{row}">{{Array.isArray(row.data[key]) ? row.data[key].join(', ') : row.data[key]}}</template>
        </el-table-column>
        <el-table-column label="校验结果" min-width="230">
          <template #default="{row}"><span :class="row.error ? 'error-text' : 'success-text'">{{row.error || '可导入'}}</span></template>
        </el-table-column>
      </el-table>
      <div class="form-footer">
        <el-button @click="resetImport">取消</el-button>
        <el-button type="primary" :disabled="!!errors" :loading="busy" @click="confirmImport">确认导入 {{checked.length}} 行</el-button>
      </div>
    </div>
    <el-result v-if="importResult != null" icon="success" title="导入完成" :sub-title="`${importResult} 行数据已保存到 Java 服务。下方列表与业务选择器已更新。`" />
  </section>
  <section class="section">
    <h2>当前基础数据</h2>
    <el-tabs v-model="selectedTab">
      <el-tab-pane v-for="(spec, key) in specs" :key="key" :name="key" :label="`${spec.title} (${s[key]?.length || 0})`" />
    </el-tabs>
    <TermFilter v-if="selectedTab === 'teachingClasses'" :classes="s.teachingClasses" v-model:year="year" v-model:semester="semester" />
    <el-table :data="currentRows">
      <el-table-column v-for="(key, cn) in specs[selectedTab].columns" :key="key" :prop="key" :label="String(cn)" min-width="140">
        <template #default="{row}">{{Array.isArray(row[key]) ? row[key].join(', ') : row[key] || '—'}}</template>
      </el-table-column>
    </el-table>
  </section>
</template>
