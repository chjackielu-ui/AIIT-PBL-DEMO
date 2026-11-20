<script setup lang="ts">
import {computed,ref} from 'vue'
import {useStore} from '../store'

import type {CourseTask,Milestone,Project} from '../types'
import {phaseStatus,phaseScore,phaseSubmission,projectCourseScore,projectPhases} from '../projectPresentation'
import ScoreEditor from './ScoreEditor.vue'
const props=defineProps<{project?:Project;task?:CourseTask}>(),store=useStore()
const task=computed(()=>props.task || store.state?.tasks.find(t=>t.id===props.project?.taskId))
const viewer=computed(()=>store.user?.role==='student'?store.user.id:undefined)
const phases=computed(()=>projectPhases(task.value,props.project).filter(m=>m.isFinal || m.requiresSubmission!==false))
const weight=computed(()=>phases.value.reduce((sum,m)=>sum+(m.scoreWeight ?? 1),0))
const accumulated=computed(()=>props.project ? projectCourseScore(props.project, phases.value, viewer.value) : undefined)
const showing=ref(false),selected=ref<Milestone>(),selectedMember=ref('')
const record=computed(()=>!props.project?undefined:selected.value?.isFinal?props.project.final:selected.value?phaseSubmission(props.project,selected.value,viewer.value || selectedMember.value):undefined)
const details=computed(()=>JSON.parse(JSON.stringify(record.value?.grading || {reviewers:[]})))
function inspect(m:Milestone){if(!props.project)return;selected.value=m;selectedMember.value=viewer.value || props.project.memberIds.find(id=>phaseSubmission(props.project!,m,id)?.grading) || props.project.memberIds[0] || '';showing.value=true}
const hasDetails=(m:Milestone)=>!props.project?false:m.isFinal?!!props.project.final?.grading:m.submissionMode==='individual' && !viewer.value?props.project.memberIds.some(id=>!!phaseSubmission(props.project!,m,id)?.grading):!!phaseSubmission(props.project,m,viewer.value)?.grading
</script>
<template><section class="section"><div class="section-head"><h2>{{project && viewer?'我的项目成绩与规则':'项目评分规则'}}</h2><strong v-if="project">当前累计 {{accumulated ?? '—'}} / 100</strong></div><p class="muted">累计成绩 = Σ（已通过任务分数 × 该任务权重）÷ 全部需提交任务的权重之和。首次通过前不显示累计分。缺交、待批或退回的任务没有阶段成绩。教师可动态调整任务和权重；每次评分明细保留当时的评分表。</p><p v-if="viewer" class="muted">共享阶段取团队分数；成员分别提交取本人分数；项目验收取教师确认的个人成绩。量表明细中的项目验收综合分是团队成果分，个人成绩由教师另行确认。</p><el-table :data="phases"><el-table-column label="任务" min-width="200"><template #default="{row}"><strong>{{row.name}}</strong><p class="muted">{{row.isFinal?'项目验收任务':'阶段任务'}} · {{row.submissionMode==='individual'?'成员分别提交':'整组由组长提交'}}</p></template></el-table-column><el-table-column label="当前权重" width="130"><template #default="{row}">{{((row.scoreWeight ?? 1)/weight*100).toFixed(2)}}%</template></el-table-column><el-table-column v-if="project" label="状态" width="115"><template #default="{row}">{{viewer && phaseStatus(project,row,viewer)==='待验收'?'已提交':phaseStatus(project,row,viewer)}}</template></el-table-column><el-table-column v-if="project" label="分数" width="95"><template #default="{row}">{{phaseScore(project,row,viewer) ?? '—'}}</template></el-table-column><el-table-column label="评分依据" min-width="220"><template #default="{row}"><p>{{row.criteria}}</p><el-button v-if="hasDetails(row)" text type="primary" @click="inspect(row)">查看当次评分明细</el-button><el-collapse v-if="row.rubric?.length"><el-collapse-item title="查看当前评分表"><p v-for="p in row.rubric" :key="p.id">{{p.dimension}} / {{p.title}} · {{p.maxPoints}} 分<br /><small>{{p.description}}</small></p></el-collapse-item></el-collapse></template></el-table-column></el-table></section><el-dialog v-if="project" v-model="showing" title="评分明细与当次规则" width="min(760px,95vw)"><el-select v-if="!viewer && selected?.submissionMode==='individual' && !selected?.isFinal" v-model="selectedMember" aria-label="查看成员评分"><el-option v-for="id in project.memberIds" :key="id" :value="id" :label="store.state?.users.find(u=>u.id===id)?.name || id" /></el-select><p v-if="!record?.grading" class="muted">该成员暂无已发布的评分明细。</p><ScoreEditor v-else :model-value="details" readonly /></el-dialog></template>
