<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useStore} from '../store'

import {phaseScore, phaseStatus, phaseSubmission, projectPhases} from '../projectPresentation'
import {stamp} from '../lib'
import {taskPath,projectPath} from '../teacher'
import PageHead from '../components/PageHead.vue'
import Status from '../components/Status.vue'
import ReviewWorkbench from '../components/ReviewWorkbench.vue'
const store=useStore(),route=useRoute(),router=useRouter()
const s=computed(()=>store.state!)
const task=computed(()=>s.value.tasks.find(t=>t.id===route.params.id && t.classId===route.params.classId))
const scopedProject = computed(() => s.value.projects.find(p => p.id === route.query.project && p.taskId === task.value?.id))
const phases = computed(() => projectPhases(task.value, scopedProject.value))
const milestone = computed(() => phases.value.find(m => m.id === route.params.phaseId))
const back = computed(() => scopedProject.value ? projectPath(s.value, scopedProject.value.id, 'board') : taskPath(task.value!.classId,task.value!.id) + '?view=grading')
const query=ref(''), filter=ref(''), selected=ref(''), selectedMember=ref('')
const teams=computed(()=>s.value.projects.filter(p=>p.taskId===task.value?.id && (!scopedProject.value || p.id===scopedProject.value.id)).flatMap(p=>
 (milestone.value?.submissionMode==='individual' && !milestone.value.isFinal ? p.memberIds : ['']).map(memberId=>({p,memberId,sub:milestone.value?phaseSubmission(p,milestone.value,memberId || undefined):undefined,status:milestone.value?phaseStatus(p,milestone.value,memberId || undefined):'未提交'}))))
const rows=computed(()=>teams.value.filter(r=>(r.p.name+' '+r.p.memberIds.map(id=>s.value.users.find(u=>u.id===id)?.name).join(' ')).includes(query.value)&&(!filter.value||r.status===filter.value)))
const project=computed(()=>s.value.projects.find(p=>p.id===selected.value))
const hasContent=(row:typeof teams.value[number])=>milestone.value?.isFinal?!!row.p.final:!!row.sub
</script>
<template>
 <template v-if="task && milestone"><PageHead title="子任务批改"><el-button @click="router.push(back)">{{scopedProject ? '返回项目' : '返回发布详情'}}</el-button><el-select :model-value="milestone.id" aria-label="切换批改子任务" style="width:210px" @change="(id:string)=>router.push({path:taskPath(task!.classId,task!.id)+'/milestones/'+id, query:route.query})"><el-option v-for="m in phases" :key="m.id" :value="m.id" :label="m.name" /></el-select></PageHead>
 <section class="section"><div class="section-head"><h2>{{milestone.name}}</h2><span class="muted">截止 {{milestone.due}}</span></div><p class="pre-wrap">{{milestone.description}}</p><p class="muted pre-wrap">课程任务说明：{{task.description}}</p></section>
 
 <div class="toolbar"><el-input v-model="query" clearable placeholder="搜索小组、项目或成员" /><el-select v-model="filter" clearable placeholder="全部提交状态" aria-label="筛选小组提交状态"><el-option v-for="v in ['未提交','待验收','退回修改','已通过','无需提交']" :key="v" :value="v" /></el-select><span class="muted">{{rows.length}} 条提交记录</span></div>
 <el-table :data="rows" class="assignment-table"><el-table-column label="项目 / 小组成员" min-width="210"><template #default="{row}"><strong>{{row.p.name}}</strong><p v-if="row.memberId">提交成员：{{s.users.find(u=>u.id===row.memberId)?.name}}</p><p class="assignment-meta">{{row.p.memberIds.map((id:string)=>s.users.find(u=>u.id===id)?.name).join('、')}}</p><small v-if="['待审核','待修改','已关闭'].includes(row.p.status)" class="muted">项目{{row.p.status}}</small></template></el-table-column><el-table-column label="提交信息" min-width="230"><template #default="{row}"><span>{{milestone.isFinal?row.p.final?.summary || '尚未提交':row.sub?.description || '尚未提交'}}</span><p v-if="hasContent(row)" class="assignment-meta">{{milestone.isFinal?(row.p.final?.fileIds.length || 0)+(row.p.final?.imageIds.length || 0):row.sub?.fileIds.length || 0}} 个附件 <span v-if="row.sub"> · {{stamp(row.sub.time)}}</span></p></template></el-table-column><el-table-column label="状态" width="120" align="center"><template #default="{row}"><Status :value="row.status" /></template></el-table-column><el-table-column label="评分" width="95" align="center"><template #default="{row}">{{phaseScore(row.p,milestone,row.memberId || undefined) ?? '—'}}</template></el-table-column><el-table-column label="操作" width="195" align="center" header-align="center" fixed="right"><template #default="{row}"><el-button :disabled="!hasContent(row)" :type="row.status==='待验收'?'primary':'default'" @click="selectedMember=row.memberId;selected=row.p.id">{{row.status==='待验收'?'查看并批改':'查看提交与评分'}}</el-button></template></el-table-column><template #empty>暂无符合条件的小组</template></el-table>
 <el-dialog :model-value="!!selected" :title="(project?.name || '')+' · '+milestone.name" width="min(1300px,96vw)" top="4vh" destroy-on-close @close="selected=''"><ReviewWorkbench v-if="project" :key="project.id+milestone.id+selectedMember" :member-id="selectedMember || undefined" :project="project" :milestone="milestone" @done="selected=''" /></el-dialog>
 </template><el-result v-else title="子任务不存在或无访问权限"><template #extra><el-button @click="router.push('/teacher/task-register')">返回实训任务总表</el-button></template></el-result>
</template>

