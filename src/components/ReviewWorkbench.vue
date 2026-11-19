<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {useStore} from '../store'

import type {GradingDetails,Milestone,Project,ScoreReviewer} from '../types'
import {phaseSubmission} from '../projectPresentation'
import {calculateGrade,newReviewer} from '../grading'
import {run} from '../lib'
import SubmissionPreview from './SubmissionPreview.vue'
import BulkScoreSheet from './BulkScoreSheet.vue'
import ScoreEditor from './ScoreEditor.vue'
import ResizablePanels from './ResizablePanels.vue'
const props=defineProps<{project:Project;milestone:Milestone;memberId?:string}>()
const emit=defineEmits<{done:[]}>()
const store=useStore()
const selectedMember=ref(props.memberId || props.project.memberIds.find(id=>phaseSubmission(props.project,props.milestone,id)?.status==='待验收') || props.project.memberIds[0])
const sub=computed(()=>phaseSubmission(props.project,props.milestone,selectedMember.value))
const pending=computed(()=>props.milestone.isFinal ? props.project.status==='结项待审核' : sub.value?.status==='待验收' && ['进行中','结项待修改'].includes(props.project.status))
const final=reactive(JSON.parse(JSON.stringify(props.project.final || {})))
const form=reactive({comment:''})
const grade=ref<GradingDetails>({reviewers:[]})
function reset(){
 const record=props.milestone.isFinal?props.project.final:sub.value
 form.comment=record?.comment || ''
 const score=props.milestone.isFinal?props.project.final?.projectScore:sub.value?.score
 grade.value=JSON.parse(JSON.stringify(record?.grading || {reviewers:[{...newReviewer(store.user?.name),score}]}))
}
watch(()=>sub.value?.id,reset,{immediate:true})
const busy=ref(false)
const quick=['成果完整，达到本阶段目标。','实现基本可用，请补充测试和说明。','材料不完整，请按任务要求补充后重新提交。','请修正关键问题并提供运行截图。']
function imported(_score:number,comment:string,reviewers:ScoreReviewer[]){grade.value={reviewers:[{...reviewers[0],name:store.user?.name || '任课教师',weight:100}]};form.comment=comment}
async function decide(pass:boolean){
 busy.value=true
 await run(async()=>{
  if(!form.comment.trim())throw Error('请填写评语或选择快捷评语')
  const score=props.milestone.isFinal && !pass?undefined:calculateGrade(grade.value,props.milestone.rubric || [])
  const grading={...grade.value,rubric:props.milestone.rubric || []}
  if(props.milestone.isFinal){
   await store.act('reviewFinal',{projectId:props.project.id,pass,comment:form.comment,grading,final:{...final,projectScore:score,comment:form.comment}})
  }else await store.act('reviewPhase',{projectId:props.project.id,submissionId:sub.value?.id,pass,score,grading,...form})
  emit('done')
 },pass?'评分已发布':'已打回，学生可重新提交')
 busy.value=false
}
</script>
<template>
 <ResizablePanels storage-key="pbl-grading-split" right-label="评分栏" :initial="62" class="review-resize">
  <SubmissionPreview :project="project" :file-ids="milestone.isFinal ? [...(project.final?.fileIds || []),...(project.final?.imageIds || [])] : sub?.fileIds || []" :description="milestone.isFinal ? (project.final?.summary || '')+'\n'+(project.final?.description || '') : sub?.description || ''" />
  <template #right><section class="grading-sidebar">
   <h2>{{milestone.isFinal?'项目验收评分':'阶段任务评分'}}</h2><p>{{project.name}} · {{milestone.name}}</p>
   <el-select v-if="milestone.submissionMode==='individual' && !milestone.isFinal" v-model="selectedMember" aria-label="选择要批改的成员"><el-option v-for="id in project.memberIds" :key="id" :value="id" :label="store.state?.users.find(u=>u.id===id)?.name || id" /></el-select>
   <div class="info-strip">验收标准：{{milestone.criteria}}</div><p v-if="milestone.scoringRule" class="info-strip">特殊评分规则：{{milestone.scoringRule}}</p>
   <el-form label-position="top" @submit.prevent>
    <BulkScoreSheet v-if="pending && !milestone.rubric?.length" :project-id="project.id" :task-name="milestone.name" @apply="imported" />
    <ScoreEditor v-model="grade" :rubric="milestone.rubric" :readonly="!pending" teacher-only />
    <el-form-item label="综合评语 / 打回原因" required><el-input v-model="form.comment" type="textarea" :rows="4" :disabled="!pending" /></el-form-item>
    <div v-if="pending" class="quick-comments"><small>快捷评语 · 点击追加</small><button type="button" v-for="q in quick" :key="q" @click="form.comment += (form.comment?'\n':'')+q">{{q}}</button></div>
    <template v-if="milestone.isFinal && final.contributions"><h3>成员贡献与个人评分</h3><div v-for="c in final.contributions" :key="c.userId" class="phase-form"><strong>{{store.state?.users.find(u=>u.id===c.userId)?.name}}</strong><p>{{c.role}} · {{c.description}}</p><el-form-item label="教师确认的贡献比例（合计 100%）"><el-input-number v-model="c.percent" :min="0" :max="100" :disabled="!pending" /></el-form-item><el-form-item label="个人成绩（教师评分）"><el-input-number v-model="c.score" :min="0" :max="100" :disabled="!pending" /></el-form-item><el-form-item label="个人评语"><el-input v-model="c.comment" :disabled="!pending" /><el-button v-if="pending" text @click="c.comment=form.comment">使用综合评语</el-button></el-form-item></div></template>
   </el-form>
   <div v-if="pending" class="actions wrap"><el-button :loading="busy" @click="decide(false)">打回修改</el-button><el-button type="primary" :loading="busy" @click="decide(true)">{{milestone.isFinal?'通过并归档':'通过并发布评分'}}</el-button></div><el-alert v-else title="当前成果不可批阅，可查看已提交材料与评分。" type="info" :closable="false" />
  </section></template>
 </ResizablePanels>
</template>
