<script setup lang="ts">
import {computed} from 'vue'
import {learningIndicators, studentLearningRows, studentTranscript} from '../studentReports'
import {localDate, run} from '../lib'
import type {State} from '../types'
import AccountAvatar from './AccountAvatar.vue'

const {state, kind, classId} = defineProps<{state:State;kind:'transcript'|'learning';classId?:string}>()
const transcript = computed(() => studentTranscript(state, kind === 'transcript' ? classId : undefined))
const learning = computed(() => studentLearningRows(state, classId))
const title = computed(() => kind === 'transcript' ? '个人课程成绩单' : '个人学情报告')
const creditNote = '已获学分按本平台总评达到 60 分且学分已知的课程估算，同一课程只计一次。教务系统登记结果为准。'
const courseColumns = ['学年', '学期', '课程编号', '课程名称', '教学班', '课程学分', '总评', '已获学分（估算）']
const learningColumns = ['学年学期', '课程', '教学班', '指标', '我的情况', '班级平均', '单位', '有效样本']
const basics = computed(() => {
  const r = transcript.value
  return [['姓名', r.name], ['学号', r.number], ['班级', r.className], ['专业', r.major], ['学院', r.college], ['导出日期', localDate()]]
})
const totals = computed(() => {
  const r = transcript.value
  return [['毕业最低学分', r.graduation], ['本表已知总学分', r.total], ['本表已获学分（估算）', r.earned], ['学分未收录课程数', r.unknownCredits]]
})

function earned(row:typeof transcript.value.rows[number]) {
  return row.score == null || row.credits == null ? undefined : row.score >= 60 ? row.credits : 0
}

async function download() {
  await run(async () => {
    const xlsx = await import('xlsx')
    const book = xlsx.utils.book_new()
    const rows = kind === 'transcript' ? [
      ...totals.value, [], courseColumns,
      ...transcript.value.rows.map(row => [row.academicYear, row.semester, row.courseCode, row.courseName, row.teachingClass, row.credits, row.score ?? '待评定', earned(row)]),
      [], [creditNote],
    ] : [
      learningColumns,
      ...learning.value.flatMap(row => learningIndicators.map(indicator =>
        [row.term, row.courseName, row.teachingClass, indicator.title, row.mine[indicator.key], row.classAverage[indicator.key], indicator.unit, row.samples[indicator.key]])),
      [], ['指标', '计算口径'],
      ...learningIndicators.map(indicator => [indicator.title, indicator.formula]),
    ]
    const sheet = xlsx.utils.aoa_to_sheet([[title.value], [], ...basics.value, [], ...rows])
    sheet['!cols'] = [23, 24, 19, 30, 26, 15, 17, 22].map(wch => ({wch}))
    xlsx.utils.book_append_sheet(book, sheet, title.value)
    xlsx.writeFile(book, title.value + '_' + localDate() + '.xlsx')
  }, '')
}
</script>

<template>
  <article class="print-document student-report" :class="{'transcript-report':kind === 'transcript'}">
    <header class="report-heading">
      <p>安徽信息工程学院 · 项目管理平台</p>
      <h1>{{ title }}</h1>
    </header>
    <div class="report-identity">
      <dl class="report-basics">
        <div v-for="[label, value] in basics" :key="label"><dt>{{ label }}</dt><dd>{{ value || '—' }}</dd></div>
      </dl>
      <div v-if="kind === 'transcript'" class="transcript-photo" role="img" :aria-label="state.user.name + '的证件照位置'">
        <AccountAvatar v-if="state.user.avatarVersion" :user="state.user" portrait />
        <span v-else>照片</span>
      </div>
    </div>
    <template v-if="kind === 'transcript'">
      <dl class="report-basics report-totals">
        <div v-for="[label, value] in totals" :key="label"><dt>{{ label }}</dt><dd>{{ value ?? '—' }}</dd></div>
      </dl>
      <div class="table-scroll">
        <table>
          <thead><tr><th v-for="column in courseColumns" :key="column" scope="col">{{ column }}</th></tr></thead>
          <tbody>
            <tr v-for="row in transcript.rows" :key="row.classId">
              <td>{{ row.academicYear }}</td><td>{{ row.semester }}</td><td>{{ row.courseCode || '—' }}</td>
              <th scope="row">{{ row.courseName }}</th><td>{{ row.teachingClass }}</td><td>{{ row.credits ?? '—' }}</td>
              <td>{{ row.score ?? '待评定' }}</td><td>{{ earned(row) ?? '—' }}</td>
            </tr>
            <tr v-if="!transcript.rows.length"><td colspan="8">暂无可导出的课程成绩。</td></tr>
          </tbody>
        </table>
      </div>
      <p class="report-note">{{ creditNote }}</p>
    </template>
    <template v-else>
      <p v-if="!learning.length" class="report-note">暂无可导出的个人学情。</p>
      <section v-for="row in learning" :key="row.classId" class="report-learning">
        <h2>{{ row.courseName }} · {{ row.teachingClass }}</h2>
        <p class="report-note">{{ row.term }} · 需提交 {{ row.mine.required }} 项，已有提交 {{ row.mine.submitted }} 项，已通过 {{ row.mine.passed }} 项</p>
        <table>
          <thead><tr><th scope="col">指标</th><th scope="col">我的情况</th><th scope="col">班级平均</th><th scope="col">有效样本</th></tr></thead>
          <tbody>
            <tr v-for="indicator in learningIndicators" :key="indicator.key">
              <th scope="row">{{ indicator.title }}</th>
              <td>{{ row.mine[indicator.key] == null ? '—' : row.mine[indicator.key] + indicator.unit }}</td>
              <td>{{ row.classAverage[indicator.key] == null ? '—' : row.classAverage[indicator.key] + indicator.unit }}</td>
              <td>{{ row.samples[indicator.key] }} 人</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
    <div class="actions no-print">
      <el-button :disabled="!(kind === 'transcript' ? transcript.rows.length : learning.length)" @click="download">下载 Excel</el-button>
    </div>
  </article>
</template>

<style scoped>
.student-report{max-width:1000px}
.report-heading{text-align:center;margin-bottom:20px}
.report-heading p{font-size:13px;color:var(--muted);margin:0}
.report-heading h1{font-size:25px;margin:3px 0;color:#203c68}
.report-basics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));border-top:1px solid #bfcbdc;border-left:1px solid #bfcbdc;margin:0 0 12px}
.report-basics div{display:flex;flex-wrap:wrap;gap:4px 12px;border-right:1px solid #bfcbdc;border-bottom:1px solid #bfcbdc;padding:7px 9px}
.report-basics dt{font-weight:600}.report-basics dd{margin:0;overflow-wrap:anywhere}
.table-scroll{overflow:auto}
.student-report table{width:100%;border-collapse:collapse;text-align:left;font-size:13px}
.student-report :is(td,th){border:1px solid #bfcbdc;padding:6px 8px}
.student-report thead{background:#edf2f8}
.report-note{font-size:12px;margin:9px 0;color:#53677d}
.report-learning{margin-bottom:14px}.report-learning h2{margin-bottom:0}
.transcript-report{color:#000;padding:20px 24px}
.transcript-report :is(h1,p){color:inherit}
.transcript-report :deep(.el-button){--el-button-text-color:#000;--el-button-hover-text-color:#000;--el-button-active-text-color:#000;--el-button-disabled-text-color:#000}
.transcript-report .report-heading{margin-bottom:12px}
.transcript-report .report-heading h1{font-size:23px}
.transcript-report .report-identity{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:stretch;gap:10px;margin-bottom:9px}
.transcript-report .report-identity .report-basics{margin:0}
.transcript-photo{width:76px;aspect-ratio:3/4;align-self:start;display:grid;place-items:center;border:1px solid #999;background:white;font-size:12px}
.transcript-report .report-totals{grid-template-columns:repeat(4,minmax(0,1fr));margin-bottom:9px}
.transcript-report .report-basics div{align-content:center;padding:4px 7px;line-height:1.35}
.transcript-report :is(td,th){padding:4px 6px}
.transcript-report thead{background:#f1f1f1}
.transcript-report .report-note{margin:7px 0}
@media screen and (max-width:600px){.report-basics{grid-template-columns:repeat(2,minmax(0,1fr))}.table-scroll table{min-width:640px}}
@media screen and (max-width:520px){
  .transcript-report{padding:13px}
  .transcript-report .report-totals{grid-template-columns:repeat(2,minmax(0,1fr))}
  .transcript-photo{width:60px}
}
@media print{
  .student-report{max-width:none}
  .student-report :is(td,th){padding:4px 5px;border-color:#777;font-size:10pt}
  .report-heading :is(h1,p),.report-note{color:#000}
  .table-scroll{overflow:visible}
  .student-report tr{break-inside:avoid}
  .student-report thead{display:table-header-group}
  .transcript-report{padding:0}
  .transcript-report .report-identity{break-inside:avoid}
  .transcript-report .report-basics,.transcript-report .report-basics div{border-color:#999}
  .transcript-photo{width:25mm}
  .transcript-report thead{background:#fff}
}
</style>
