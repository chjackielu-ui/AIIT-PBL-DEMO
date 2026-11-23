<script setup lang="ts">
import {computed, ref} from 'vue'
import {run} from '../lib'
import type {ScoreReviewer} from '../types'
const props = defineProps<{projectId?:string;taskName?:string}>()
const emit = defineEmits<{apply:[score:number,comment:string,reviewers:ScoreReviewer[]]}>()
const opened=ref(false), busy=ref(false), filename=ref('')
interface ScoreRow {project:string;task:string;reviewer:string;score:number;weight:number;comment:string}
const rows=ref<ScoreRow[]>([])
const groups=computed(()=>{
  const result=new Map<string,{project:string;task:string;weight:number;score:number}>()
  for(const r of rows.value){const key=JSON.stringify([r.project,r.task]);const g=result.get(key)||{project:r.project,task:r.task,weight:0,score:0};g.weight+=r.weight;g.score+=r.score*r.weight/100;result.set(key,g)}
  return [...result.values()]
})
const valid=computed(()=>groups.value.length>0 && groups.value.every(g=>Math.abs(g.weight-100)<0.001))
const current=computed(()=>groups.value.find(g=>g.project===props.projectId && g.task===props.taskName))
async function sample(){await run(async()=>{
  const XLSX=await import('xlsx')
  const sheet=XLSX.utils.aoa_to_sheet([['项目ID','任务名称','评分人员','所评分数','权重比例（%）','备注'],[props.projectId||'项目ID示例',props.taskName||'阶段任务名称','任课教师',90,100,'成果完整']])
  sheet['!cols']=[{wch:38},{wch:24},{wch:16},{wch:14},{wch:20},{wch:40}]
  const book=XLSX.utils.book_new();XLSX.utils.book_append_sheet(book,sheet,'批量评分表');XLSX.writeFile(book,'批量评分样表.xlsx')
},'')}
async function upload(e:Event){
  const input=e.target as HTMLInputElement, file=input.files?.[0];if(!file)return
  rows.value=[];filename.value='';busy.value=true
  await run(async()=>{
    if(file.size>5*1024*1024)throw Error('评分表请控制在 5 MB 以内')
    if(!/\.(xlsx?|csv)$/i.test(file.name))throw Error('请选择 Excel 或 CSV 评分表')
    const XLSX=await import('xlsx'),book=XLSX.read(await file.arrayBuffer(),{type:'array'})
    const data=XLSX.utils.sheet_to_json<unknown[]>(book.Sheets[book.SheetNames[0]],{header:1,defval:''}).filter(r=>r.some(v=>String(v).trim()))
    const header=['项目ID','任务名称','评分人员','所评分数','权重比例（%）','备注']
    if(!data.length || header.some((h,i)=>data[0][i]!==h))throw Error('表头与样表不符，请下载样表后填写')
    const parsed=data.slice(1).map((r,i)=>{
      const [project,task,reviewer,score,weight,comment]=r
      if(!String(project).trim()||!String(task).trim()||!String(reviewer).trim()||String(score).trim()===''||String(weight).trim()===''||![Number(score),Number(weight)].every(v=>Number.isFinite(v)&&v>=0&&v<=100)||Number(weight)===0)throw Error(`第 ${i+2} 行内容无效：分数需为 0–100，权重需大于 0 且不超过 100，项目、任务及人员不能为空`)
      return {project:String(project).trim(),task:String(task).trim(),reviewer:String(reviewer).trim(),score:Number(score),weight:Number(weight),comment:String(comment)}
    })
    if(!parsed.length||parsed.length>2000)throw Error('请上传含 1–2000 条评分记录的表格')
    const people=new Map<string,Set<string>>()
    for(const row of parsed){const key=JSON.stringify([row.project,row.task]),names=people.get(key)||new Set<string>();if(names.has(row.reviewer))throw Error(`${row.project} / ${row.task}：评分人员重复`);names.add(row.reviewer);if(names.size>1)throw Error('同一项目的同一任务只能填写一位任课教师');people.set(key,names)}
    rows.value=parsed;filename.value=file.name
  },'')
  busy.value=false;input.value=''
}
function apply(){if(current.value && valid.value){const selected=rows.value.filter(r=>r.project===props.projectId&&r.task===props.taskName);emit('apply',Math.round(current.value.score*100)/100,selected.map(r=>`${r.reviewer}（${r.weight}%）：${r.comment}`).join('\n'),selected.map(r=>({name:r.reviewer,score:r.score,weight:r.weight,comment:r.comment,points:{}})));opened.value=false}}
</script>
<template>
  <el-button @click="opened=true">导入当前任务评分表</el-button>
  <el-dialog v-model="opened" title="导入当前任务评分表" width="min(1000px,94vw)" append-to-body>
    <p>每个项目的每项任务填写一位任课教师，采用 100 分制，权重填写 100%。</p>
    <div class="actions wrap"><el-button @click="sample">下载评分样表</el-button><label v-file-drop class="upload-label">{{busy?'读取中…':'选择或拖入评分表'}}<input type="file" accept=".xlsx,.xls,.csv" :disabled="busy" @change="upload" /></label><span>{{filename}}</span></div>
    <el-alert title="表格仅填入当前项目、当前任务的评分；点击“通过并发布评分”或“通过并归档”后由服务器校验并保存。" type="info" :closable="false" show-icon />
    <el-table v-if="rows.length" :data="rows" max-height="320"><el-table-column prop="project" label="项目ID" min-width="140" /><el-table-column prop="task" label="任务名称" min-width="130" /><el-table-column prop="reviewer" label="评分人员" width="110" /><el-table-column prop="score" label="所评分数" width="95" /><el-table-column prop="weight" label="权重（%）" width="105" /><el-table-column prop="comment" label="备注" min-width="160" /></el-table>
    <p v-for="g in groups" :key="JSON.stringify([g.project,g.task])" :class="Math.abs(g.weight-100)<0.001?'success-text':'error-text'">{{g.project}} · {{g.task}}：权重合计 {{g.weight}}%，加权分数 {{g.score.toFixed(2)}}</p>
    <template #footer><el-button @click="opened=false">关闭</el-button><el-button v-if="projectId" type="primary" :disabled="!valid || !current" @click="apply">填入当前任务评分</el-button></template>
  </el-dialog>
</template>
