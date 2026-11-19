<script setup lang="ts">
import {computed} from 'vue'
import {useStore} from '../store'

import type {Project} from '../types'
import {phaseStatus,projectProgress} from '../projectPresentation'
import Status from './Status.vue'
import ProjectDecisionBar from './ProjectDecisionBar.vue'
const props=defineProps<{project?:Project;application?:boolean}>()
const open=defineModel<boolean>({default:false})
const store=useStore()
const task=computed(()=>store.state?.tasks.find(t=>t.id===props.project?.taskId))
const phases=computed(()=>task.value?.milestones.filter(m=>m.isFinal || m.requiresSubmission!==false) || [])
const name=(id:string)=>store.state?.users.find(u=>u.id===id)?.name || id
</script>
<template>
 <el-dialog v-model="open" :title="application?'项目申请详情':'项目简介与完成情况'" width="min(680px,94vw)" destroy-on-close>
  <template v-if="project"><div class="section-head"><h2>{{project.name}}</h2><Status :value="project.status" /></div><p class="muted">{{task?.name}}</p><p class="pre-wrap">{{project.description}}</p><p class="pre-wrap"><strong>项目目标：</strong>{{project.goal}}</p>
   <div class="project-preview-facts"><span>负责人：{{name(project.leaderId)}}</span><span>{{application?'申请时间':'创建时间'}}：{{project.created}}</span><span>组员：{{project.memberIds.map(name).join('、')}}</span></div>
   <h3>完成情况</h3><el-progress :percentage="projectProgress(store.state!,project)" :stroke-width="7" /><div class="project-preview-phases"><div v-for="m in phases" :key="m.id"><span>{{m.name}}</span><Status :value="phaseStatus(project,m)" /></div></div><p v-if="!phases.length" class="muted">教师尚未发布子任务。</p>
   <p v-if="application && project.reviewComment" class="feedback">审核意见：{{project.reviewComment}}</p><ProjectDecisionBar v-if="application" :project="project" inline />
  </template><el-empty v-else description="项目不存在或无查看权限" />
  <template #footer><el-button @click="open=false">关闭</el-button></template>
 </el-dialog>
</template>
<style scoped>.project-preview-facts{display:flex;flex-wrap:wrap;gap:8px 22px;font-size:13px;color:var(--muted);padding:12px 0}.project-preview-phases{margin-top:16px}.project-preview-phases>div{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid var(--line)}.project-preview-phases>div>span:first-child{min-width:0;overflow-wrap:anywhere}</style>
