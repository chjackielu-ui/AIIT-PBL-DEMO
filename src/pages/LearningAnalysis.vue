<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {CircleHelp} from 'lucide-vue-next'
import {useStore} from '../store'

import PageHead from '../components/PageHead.vue'
defineProps<{embedded?:boolean}>()
const store=useStore(),selected=ref('')
const data=computed(()=>store.state?.learningAnalytics || [])
watch(data,rows=>{if(!rows.some(r=>r.classId===selected.value))selected.value=rows[0]?.classId || ''},{immediate:true})
const current=computed(()=>data.value.find(r=>r.classId===selected.value))
const labels=[
 {key:'completionRate',title:'任务通过率',unit:'%',formula:'本人已通过的任务数 ÷ 该教学班已发布的需提交任务总数 × 100。未参与项目的任务也计入分母。',source:'教师验收结果'},
 {key:'submissionRate',title:'任务提交率',unit:'%',formula:'本人已有提交记录的任务数 ÷ 需提交任务总数 × 100。整组提交对每位成员计一次。',source:'最近提交记录'},
 {key:'onTimeRate',title:'按时提交率',unit:'%',formula:'最近版本在截止日及之前提交的任务数 ÷ 有有效提交时间的任务数 × 100。缺少时间的历史材料不参与计算。',source:'提交日期与截止日期'},
 {key:'averageScore',title:'已通过任务均分',unit:'分',formula:'本人已通过且有教师评分的任务分数之和 ÷ 这些任务数。分别提交取本人分数，项目验收取教师确认的个人成绩。这是等权学习诊断均分，项目成绩另按任务权重计算。',source:'教师评分'},
 {key:'uploads',title:'资料上传份数',unit:'份',formula:'本人在该教学班项目中上传并保留在资料库的文件数。上传数量不代表工作量、贡献度或质量。',source:'资料库上传记录'}
] as const
const rows=computed(()=>current.value?labels.map(label=>({...label,mine:current.value!.mine[label.key],average:current.value!.classAverage[label.key],samples:current.value!.samples[label.key]})):[])
const className=(id:string)=>{const c=store.state?.teachingClasses.find(c=>c.id===id);return `${store.state?.courses.find(co=>co.id===c?.courseId)?.name || ''} · ${c?.name || id}`}
const display=(n:number|null|undefined,unit:string)=>n==null?'暂无数据':`${n}${unit}`
function width(n:number|null|undefined,row:typeof rows.value[number]){
 const maximum=row.unit==='份'?Math.max(row.mine ?? 0,row.average ?? 0,1):100
 return `${Math.max(0,Math.min(100,(n ?? 0)/maximum*100))}%`
}
</script>
<template>
 <PageHead :title="embedded?'学情分析':'个人情况'"><el-button @click="store.load()">刷新统计</el-button></PageHead>
 <div class="toolbar"><el-select v-model="selected" aria-label="选择学情分析教学班" style="width:min(100%,480px)"><el-option v-for="d in data" :key="d.classId" :value="d.classId" :label="className(d.classId)" /></el-select></div>
 <template v-if="current">
  <section class="section learning-summary"><div><h2>{{className(current.classId)}}</h2><p class="muted">{{current.studentCount}} 名在读学生 · 班级平均包含本人</p></div><div class="learning-counts"><div><strong>{{current.mine.required}}</strong><small>需提交任务</small></div><div><strong>{{current.mine.submitted}}</strong><small>已有提交</small></div><div><strong>{{current.mine.passed}}</strong><small>已通过</small></div></div></section>
  <div class="learning-legend"><span><i class="mine-dot" />我的情况</span><span><i class="class-dot" />班级平均</span></div>
  <div class="learning-charts">
   <section v-for="row in rows" :key="row.key" class="section learning-card">
    <div class="learning-card-heading"><h2>{{row.title}}</h2><el-popover trigger="click" :width="290" placement="bottom"><template #reference><button class="metric-help" :aria-label="row.title+'的计算方法'"><CircleHelp :size="17" /></button></template><strong>{{row.title}}如何计算</strong><p>{{row.formula}}</p><p class="muted">数据来源：{{row.source}}。班级平均取有该项数据的学生指标的算术平均；当前有效样本 {{row.samples}} 人。</p></el-popover></div>
    <div class="learning-value">{{display(row.mine,row.unit)}}</div>
    <div class="learning-comparison" role="img" :aria-label="`${row.title}：我的情况${display(row.mine,row.unit)}，班级平均${display(row.average,row.unit)}`">
     <div class="learning-bar-label"><span>我的情况</span><strong>{{display(row.mine,row.unit)}}</strong></div><div class="learning-track"><span class="learning-bar mine-bar" :style="{width:width(row.mine,row)}" /></div>
     <div class="learning-bar-label"><span>班级平均</span><strong>{{display(row.average,row.unit)}}</strong></div><div class="learning-track"><span class="learning-bar class-bar" :style="{width:width(row.average,row)}" /></div>
    </div>
    <small class="muted">{{row.unit==='份'?'按当前两组数值同比例展示':'刻度范围 0–100'}} · 有效样本 {{row.samples}} 人</small>
   </section>
  </div>
 </template>
 <el-empty v-else description="尚未加入教学班，暂无可计算的学情数据" />
</template>
<style scoped>
.learning-summary{display:flex;align-items:center;justify-content:space-between;gap:24px;flex-wrap:wrap}.learning-summary h2{margin:0}.learning-counts{display:flex;gap:32px}.learning-counts div{display:flex;flex-direction:column;gap:6px}.learning-counts strong{font-size:26px;color:#1e3a5f}.learning-counts small{color:var(--muted)}.learning-legend{display:flex;gap:24px;margin:20px 0 12px;color:var(--muted);font-size:13px}.learning-legend span{display:flex;align-items:center;gap:7px}.learning-legend i{width:9px;height:9px;border-radius:50%}.mine-dot,.mine-bar{background:var(--primary)}.class-dot,.class-bar{background:#9eabc4}.learning-charts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.learning-card{min-width:0;margin:0}.learning-card-heading{display:flex;align-items:center;gap:7px}.learning-card-heading h2{font-size:16px;margin:0}.metric-help{display:inline-flex;align-items:center;border:0;background:transparent;color:var(--muted);padding:5px;cursor:pointer;border-radius:50%;flex:none}.metric-help:hover,.metric-help:focus-visible{background:#edf4ff;color:#1e3a5f}.learning-value{font-size:32px;font-weight:650;color:#1e3a5f;margin:20px 0}.learning-bar-label{display:flex;justify-content:space-between;gap:8px;font-size:12px;margin:12px 0 7px}.learning-track{height:10px;background:#eff3f8;border-radius:8px;overflow:hidden}.learning-bar{display:block;height:100%;border-radius:8px}.learning-comparison{margin-bottom:18px}
@media(max-width:1150px){.learning-charts{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:650px){.learning-charts{grid-template-columns:1fr}.learning-counts{width:100%;justify-content:space-between;gap:14px}.learning-summary{gap:14px}.learning-value{font-size:28px}}
</style>
