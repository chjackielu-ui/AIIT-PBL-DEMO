<script setup lang="ts">
import {computed,nextTick,onMounted,ref,watch} from 'vue'
import {ChevronDown, CircleHelp, FileText} from 'lucide-vue-next'
import {useStore} from '../store'
import {learningIndicators, studentLearningRows, studentTranscript} from '../studentReports'

import Counter from '../components/Counter.vue'
import PageHead from '../components/PageHead.vue'
import CurriculumPlan from '../components/CurriculumPlan.vue'
defineProps<{embedded?:boolean}>()
const store=useStore(),selected=ref('')
const data=computed(()=>store.state?studentLearningRows(store.state):[])
const transcript=computed(()=>store.state?studentTranscript(store.state):undefined)
watch(data,rows=>{if(!rows.some(r=>r.classId===selected.value))selected.value=rows[0]?.classId || ''},{immediate:true})
const current=computed(()=>data.value.find(r=>r.classId===selected.value))
const rows=computed(()=>current.value?learningIndicators.map(label=>({...label,mine:current.value!.mine[label.key],average:current.value!.classAverage[label.key],samples:current.value!.samples[label.key]})):[])
const display=(n:number|null|undefined,unit:string)=>n==null?'暂无数据':`${n}${unit}`
/** 我的情况进度条：从 0 增长到目标宽度；切换教学班时重新播放。 */
const grown=ref(false)
function replayGrow(){grown.value=false;void nextTick(()=>requestAnimationFrame(()=>{grown.value=true}))}
onMounted(replayGrow)
watch(current,replayGrow)
function openReport(scope:'all'|'selected') {
 const query = scope === 'selected' && selected.value ? '?classId=' + encodeURIComponent(selected.value) : ''
 window.open('/print/transcript' + query, '_blank', 'noopener')
}
function width(n:number|null|undefined,row:typeof rows.value[number]){
 const maximum=row.unit==='份'?Math.max(row.mine ?? 0,row.average ?? 0,1):100
 return `${Math.max(0,Math.min(100,(n ?? 0)/maximum*100))}%`
}
</script>
<template>
 <PageHead :title="embedded?'学情分析':'个人情况'" />
 <div class="toolbar report-toolbar">
  <el-select v-model="selected" aria-label="选择学情分析教学班" placeholder="暂无教学班"><el-option v-for="d in data" :key="d.classId" :value="d.classId" :label="d.courseName + ' · ' + d.teachingClass" /></el-select>
  <el-button @click="store.load()">刷新统计</el-button>
  <el-dropdown trigger="click" :disabled="!transcript?.rows.length" @command="(scope:'all'|'selected') => openReport(scope)">
   <el-button type="primary" :disabled="!transcript?.rows.length"><FileText :size="16" />导出成绩单<ChevronDown :size="14" /></el-button>
   <template #dropdown><el-dropdown-menu><el-dropdown-item command="all">全部课程成绩单</el-dropdown-item><el-dropdown-item command="selected" :disabled="!transcript?.rows.some(row=>row.classId===selected)">当前课程成绩单</el-dropdown-item></el-dropdown-menu></template>
  </el-dropdown>

 </div>
 <div v-if="transcript?.rows.length" class="credit-summary">
  <span>毕业最低学分 <strong>{{ transcript.graduation ?? '—' }}</strong></span>
  <span>本平台已知课程总学分 <strong>{{ transcript.total ?? '—' }}</strong></span>
  <span>本平台已获学分（估算） <strong>{{ transcript.earned ?? '—' }}</strong></span>
 </div>
 <template v-if="current">
  <section class="section learning-summary"><div><h2>{{current.courseName}}</h2><p class="muted">{{current.teachingClass}} · {{current.studentCount}} 名在读学生</p></div><div class="learning-counts"><div><strong><Counter :value="current.mine.required" :font-size="26" :font-weight="650" :duration="1" :stagger="0.08" /></strong><small>需提交任务</small></div><div><strong><Counter :value="current.mine.submitted" :font-size="26" :font-weight="650" :duration="1" :stagger="0.08" /></strong><small>已有提交</small></div><div><strong><Counter :value="current.mine.passed" :font-size="26" :font-weight="650" :duration="1" :stagger="0.08" /></strong><small>已通过</small></div></div></section>
  <div class="learning-legend"><span><i class="mine-dot" />我的情况</span><span><i class="class-dot" />班级平均</span></div>
  <div class="learning-charts">
   <section v-for="row in rows" :key="row.key" class="section learning-card">
    <div class="learning-card-heading"><h2>{{row.title}}</h2><el-popover trigger="click" :width="290" placement="bottom"><template #reference><button class="metric-help" :aria-label="row.title+'的计算方法'"><CircleHelp :size="17" /></button></template><strong>{{row.title}}如何计算</strong><p>{{row.formula}}</p><p class="muted">数据来源：{{row.source}}。班级平均取有该项数据的学生指标的算术平均；当前有效样本 {{row.samples}} 人。</p></el-popover></div>
    <div class="learning-value">{{display(row.mine,row.unit)}}</div>
    <div class="learning-comparison" role="img" :aria-label="`${row.title}：我的情况${display(row.mine,row.unit)}，班级平均${display(row.average,row.unit)}`">
     <div class="learning-bar-label"><span>我的情况</span><strong>{{display(row.mine,row.unit)}}</strong></div><div class="learning-track"><span class="learning-bar mine-bar" :style="{width:grown?width(row.mine,row):'0%'}" /></div>
     <div class="learning-bar-label"><span>班级平均</span><strong>{{display(row.average,row.unit)}}</strong></div><div class="learning-track"><span class="learning-bar class-bar" :style="{width:width(row.average,row)}" /></div>
    </div>
    <small class="muted">{{row.unit==='份'?'按当前两组数值同比例展示':'刻度范围 0–100'}} · 有效样本 {{row.samples}} 人</small>
   </section>
  </div>
 </template>
 <el-empty v-else description="尚未加入教学班，暂无可计算的学情数据" />
 <CurriculumPlan :cohort="transcript?.cohort" />
</template>
<style scoped>
.report-toolbar{margin:0 0 10px;gap:8px}.report-toolbar .el-select{flex:1 1 225px;max-width:410px}
.credit-summary{display:flex;gap:10px 22px;flex-wrap:wrap;padding:9px 12px;margin-bottom:10px;background:#eef4fc;border-radius:7px;color:#314968;font-size:13px}
.credit-summary strong{margin-left:5px;font-variant-numeric:tabular-nums}
.learning-summary p{margin:4px 0 0}
.learning-summary{display:flex;align-items:center;justify-content:space-between;gap:24px;flex-wrap:wrap}.learning-summary h2{margin:0}.learning-counts{display:flex;gap:32px}.learning-counts div{display:flex;flex-direction:column;gap:6px}.learning-counts strong{font-size:26px;color:#1e3a5f}.learning-counts small{color:var(--muted)}.learning-legend{display:flex;gap:24px;margin:20px 0 12px;color:var(--muted);font-size:13px}.learning-legend span{display:flex;align-items:center;gap:7px}.learning-legend i{width:9px;height:9px;border-radius:50%}.mine-dot,.mine-bar{background:var(--primary)}.class-dot,.class-bar{background:#9eabc4}.learning-charts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.learning-card{min-width:0;margin:0}.learning-card-heading{display:flex;align-items:center;gap:7px}.learning-card-heading h2{font-size:16px;margin:0}.metric-help{display:inline-flex;align-items:center;border:0;background:transparent;color:var(--muted);padding:5px;cursor:pointer;border-radius:50%;flex:none}.metric-help:hover,.metric-help:focus-visible{background:#edf4ff;color:#1e3a5f}.learning-value{font-size:32px;font-weight:650;color:#1e3a5f;margin:20px 0}.learning-bar-label{display:flex;justify-content:space-between;gap:8px;font-size:12px;margin:12px 0 7px}.learning-track{height:10px;background:#eff3f8;border-radius:8px;overflow:hidden}.learning-bar{display:block;height:100%;border-radius:8px}.learning-comparison{margin-bottom:18px}
@media(max-width:1150px){.learning-charts{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:650px){.learning-charts{grid-template-columns:1fr}.learning-counts{width:100%;justify-content:space-between;gap:14px}.learning-summary{gap:14px}.learning-value{font-size:28px}}

/* 我的情况进度条：从 0 增长到目标宽度 */
.learning-bar.mine-bar { transition: width .55s cubic-bezier(.22, .68, .32, 1); }
@media (prefers-reduced-motion: reduce) { .learning-bar.mine-bar { transition: none; } }
</style>
