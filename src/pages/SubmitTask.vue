<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useStore} from '../store'

import {run, fileDownloadUrl, stamp} from '../lib'
import {phaseSubmission, phaseStatus} from '../projectPresentation'
import {deliverableFiles,validateDeliverables,submissionAttachmentIssue} from '../deliverables'
import PageHead from '../components/PageHead.vue'
import DirectUpload from '../components/DirectUpload.vue'
import DeliverablePicker from '../components/DeliverablePicker.vue'
import FinalPanel from '../components/FinalPanel.vue'
const store=useStore(),route=useRoute(),router=useRouter()
const p=computed(()=>store.state?.projects.find(p=>p.id===route.params.id))
const task=computed(()=>store.state?.tasks.find(t=>t.id===p.value?.taskId))
const m=computed(()=>task.value?.milestones.find(m=>m.id===route.params.phaseId))
const previous=computed(()=>p.value && m.value ? phaseSubmission(p.value,m.value,store.user?.id):undefined)
const history=computed(()=>p.value?.submissions.filter(sub=>sub.milestoneId===m.value?.id && (m.value?.submissionMode!=='individual' || sub.submitterId===store.user?.id)).slice().reverse() || [])
const canSubmit=computed(()=>p.value?.memberIds.includes(store.user?.id || '') && (m.value?.submissionMode==='individual' || p.value?.leaderId===store.user?.id) && ['进行中','结项待修改'].includes(p.value?.status || '') && previous.value?.status!=='已通过' && m.value?.requiresSubmission!==false && (previous.value?.status==='退回修改' || (m.value?.due || '')>=new Date().toLocaleDateString('sv-SE')))
const form=reactive({description:'',fileIds:[] as string[],deliverables:{} as Record<string,string[]>})
const baseSubmissionId=ref('')
const attachmentIssue=computed(()=>submissionAttachmentIssue(m.value?.deliverables || [],form.deliverables,[...form.fileIds,...deliverableFiles(form.deliverables)],m.value?.requireMaterials!==false))
watch(()=>m.value?.id,()=>{
 const prev=previous.value
 Object.assign(form,{description:prev?.description || '',fileIds:[...(prev?.fileIds || [])],deliverables:JSON.parse(JSON.stringify(prev?.deliverables || {}))})
 baseSubmissionId.value=prev?.id || ''
},{immediate:true})
const busy=ref(false)
const back=computed(()=>({path:`/student/projects/${p.value?.id}`,query:{tab:'board',origin:route.query.origin}}))
async function submit(){
 busy.value=true
 await run(async()=>{
  if(!form.description.trim())throw Error('请填写成果说明')
  if(attachmentIssue.value)throw Error(attachmentIssue.value)
  validateDeliverables(m.value?.deliverables || [],form.deliverables)
  const fileIds=[...new Set([...form.fileIds,...deliverableFiles(form.deliverables)])]
  await store.act('submitPhase',{projectId:p.value!.id,milestoneId:m.value!.id,...form,fileIds,baseSubmissionId:baseSubmissionId.value})
  await router.push(back.value)
 },'已提交，等待教师批阅')
 busy.value=false
}
</script>
<template>
 <template v-if="p && m">
  <PageHead :title="m.name"><el-button @click="router.push(back)">返回项目控制台</el-button></PageHead>
  <section class="section"><h2>{{p.name}}</h2><p class="pre-wrap">{{p.description}}</p><p class="muted">项目目标：{{p.goal}}</p></section>
  <section class="section"><div class="section-head"><h2>教师布置的任务</h2><el-tag>{{phaseStatus(p,m,store.user?.id)==='待验收'?'已提交':phaseStatus(p,m,store.user?.id)}}</el-tag></div><p class="pre-wrap">{{m.description}}</p><el-tag>{{m.submissionMode==='individual'?'成员分别提交 · 各自保存材料和评分':'整组提交 · 由组长统一提交'}}</el-tag><div class="details-grid"><div><small>截止日期</small>{{m.due}}</div><div><small>提交要求</small>{{m.materials || '填写成果说明'}}{{m.requireMaterials===false?'（附件可选）':''}}</div></div><div class="info-strip">评分标准：{{m.criteria}}</div></section>
  <FinalPanel v-if="m.isFinal" :project="p" :readonly="!['进行中','结项待修改','结项待审核'].includes(p.status)" />
  <section v-else class="section">
   <div class="section-head"><h2>提交内容</h2><strong v-if="previous?.score!=null">教师评分：{{previous.score}} / 100</strong></div><div v-if="previous?.comment" class="feedback">教师反馈：{{previous.comment}}</div>
   <el-alert v-if="previous?.status==='待验收'" title="截止前、教师批改前可以修改提交内容；批改后锁定，教师退回后可重新提交。旧版本保留在历史记录中。" type="info" :closable="false" />
   <el-form label-position="top" @submit.prevent="submit">
    <el-form-item label="成果说明" required><el-input v-model="form.description" type="textarea" :rows="5" :disabled="!canSubmit" /></el-form-item>
    <el-form-item label="上传成果附件"><DirectUpload v-if="canSubmit" :project-id="p.id" @uploaded="f=>form.fileIds.push(f.id)" /></el-form-item>
    <DeliverablePicker v-model="form.deliverables" :requirements="m.deliverables || []" :files="p.files" :disabled="!canSubmit" />
    <el-form-item label="附加材料 / 资料库文件"><el-select v-model="form.fileIds" multiple filterable :disabled="!canSubmit" placeholder="选择已有文件"><el-option v-for="f in p.files" :key="f.id" :value="f.id" :label="f.name" /></el-select></el-form-item>
    <p class="muted">文件上传至项目资料库。逐项选择清单材料后，系统会将它们与附加材料一起提交；取消选择不会删除原文件。</p>
    <el-alert v-if="canSubmit && attachmentIssue" :title="attachmentIssue" type="warning" :closable="false" /><el-button v-if="canSubmit" type="primary" :disabled="!!attachmentIssue" :loading="busy" @click="submit">{{previous?'更新并提交':'提交给教师'}}</el-button>
    <el-alert v-else :title="previous?.status==='已通过'?'已批改通过，提交内容已锁定。教师退回后才可修改。':m.submissionMode!=='individual' && p.leaderId!==store.user?.id?'本任务由组长统一提交；你可以查看材料、分数与反馈。':'当前为查看状态；截止后无法修改未批改材料，教师退回后可重新提交。'" type="info" :closable="false" />
   </el-form>
   <div v-if="previous"><h3>最近提交</h3><p>{{previous.description}}</p><small class="muted">{{store.state?.users.find(u=>u.id===previous?.submitterId)?.name || '历史提交'}} · {{stamp(previous.time)}}</small><p v-for="fid in previous.fileIds" :key="fid"><a :href="fileDownloadUrl(p.id,fid)">{{p.files.find(f=>f.id===fid)?.name}}</a></p></div>
   <el-collapse v-if="history.length>1"><el-collapse-item title="历史提交与反馈"><article v-for="record in history" :key="record.id" class="phase-block"><div class="section-head"><strong>{{stamp(record.time)}} · {{record.status==='待验收'?'已提交':record.status}}</strong><span v-if="record.score!=null">{{record.score}} / 100</span></div><p class="pre-wrap">{{record.description}}</p><p v-if="record.comment" class="feedback">教师反馈：{{record.comment}}</p><p v-for="fid in record.fileIds" :key="fid"><a :href="fileDownloadUrl(p.id,fid)">{{p.files.find(f=>f.id===fid)?.name || '历史附件'}}</a></p></article></el-collapse-item></el-collapse>
  </section>
 </template><el-empty v-else description="任务不存在或无访问权限" />
</template>
