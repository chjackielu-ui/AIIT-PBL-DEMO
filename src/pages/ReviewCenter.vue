<script setup lang="ts">
import MarkdownText from '../components/MarkdownText.vue'
import {computed, ref, watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useStore} from '../store'

import {phaseScore, phaseStatus, projectCourseScore,pendingSubmissions,projectPhases} from '../projectPresentation'
import {projectPath} from '../teacher'
import {courseDimensions} from '../courseGrading'
import {ElMessageBox} from 'element-plus'
import {run} from '../lib'
import TeacherProjectDialog from '../components/TeacherProjectDialog.vue'
import type {Milestone, Project} from '../types'
import PageHead from '../components/PageHead.vue'
import ReviewWorkbench from '../components/ReviewWorkbench.vue'
import Status from '../components/Status.vue'
const store=useStore(),route=useRoute(),router=useRouter()
const s=computed(()=>store.state!)
const application=computed(()=>route.params.kind==='projects' || route.path.startsWith('/teacher/reviews/projects/'))
const classId=ref(String(route.query.classId || route.params.classId || '')), query=ref(''), status=ref(''), type=ref(String(route.query.type || (route.params.kind==='final'?'final':route.params.kind==='milestones'?'milestones':'')))
watch(()=>[route.query.type,route.params.kind],([v,kind])=>{type.value=String(v || (kind==='final'?'final':kind==='milestones'?'milestones':''))})
watch(()=>[route.query.classId,route.params.classId],([v,id])=>{classId.value=String(v || id || '')})
const selected=ref<{pid:string;mid:string}>()
const previewOpen=ref(false),previewId=ref('')
const preview=computed(()=>s.value.projects.find(p=>p.id===previewId.value))
function showProject(id:string){previewId.value=id;previewOpen.value=true}
watch(()=>route.params.id,id=>{if(typeof id==='string')showProject(id)},{immediate:true})
watch(previewOpen,open=>{if(!open && route.params.id)router.replace({path:'/teacher/reviews/projects',query:route.query})})
const project=computed(()=>s.value.projects.find(p=>p.id===selected.value?.pid))
const milestone=computed(()=>projectPhases(s.value.tasks.find(t=>t.id===project.value?.taskId),project.value).find(m=>m.id===selected.value?.mid))
const groups=computed(()=>s.value.projects.map(p=>{
 const task=s.value.tasks.find(t=>t.id===p.taskId)
 const tc=s.value.teachingClasses.find(c=>c.id===task?.classId)
 const phases=projectPhases(task,p).filter(m=>m.isFinal || m.requiresSubmission!==false).filter(m=>(!type.value || (type.value==='final' ? m.isFinal : !m.isFinal)) && (!status.value || phaseStatus(p,m)===status.value))
 return {p,task,tc,phases}
}).filter(g=>(!classId.value || g.tc?.id===classId.value) && (g.p.name+' '+g.task?.name+' '+g.tc?.name).includes(query.value) && (application.value ? g.task?.taskKind!=='exercise' && ['待审核','待修改'].includes(g.p.status) : !['待审核','待修改','已关闭'].includes(g.p.status) && g.phases.length>0)).sort((a,b)=>(a.tc?.name || '').localeCompare(b.tc?.name || '') || a.p.name.localeCompare(b.p.name)))
const bulkIds=ref<string[]>([]), bulkBusy=ref(false)
const reviewable=computed(()=>groups.value.filter(g=>g.p.status==='待审核').map(g=>g.p))
const batchProjects=computed(()=>reviewable.value.filter(p=>!bulkIds.value.length || bulkIds.value.includes(p.id)))
async function bulkApprove(){
 const targets=[...batchProjects.value];if(!targets.length || bulkBusy.value)return
 try{await ElMessageBox.confirm(`是否批量通过以下 ${targets.length} 个项目？\n${targets.map(p=>p.name).join('、')}`,'批量审核',{confirmButtonText:bulkIds.value.length?'通过所选':'全部通过',confirmButtonClass:'el-button--primary',cancelButtonText:'取消',type:'warning'})}catch{return}
 bulkBusy.value=true;await run(async()=>{await store.act('batchReviewProjects',{projectIds:targets.map(p=>p.id)});bulkIds.value=[]},'批量审核完成');bulkBusy.value=false
}
const pending=computed(()=>groups.value.reduce((n,g)=>n+pendingSubmissions(g.p,g.phases).length+g.phases.filter(m=>m.isFinal && phaseStatus(g.p,m)==='待验收').length,0))
function inspect(p:Project,m:Milestone){selected.value={pid:p.id,mid:m.id}}
</script>
<template>
 <div :class="{'application-list':application}">
  <PageHead :title="application?'项目申请与审核':'成果审核与评分'" />
  <div class="toolbar"><el-input v-model="query" clearable placeholder="搜索项目、实训任务或班级" /><el-select v-model="classId" clearable placeholder="全部教学班级"><el-option v-for="c in s.teachingClasses" :key="c.id" :value="c.id" :label="c.name" /></el-select><template v-if="!application"><el-select v-model="status" clearable placeholder="全部状态"><el-option v-for="v in ['未提交','待验收','退回修改','已通过']" :key="v" :value="v" /></el-select></template><span class="muted">{{groups.length}} 个项目<span v-if="!application"> · {{pending}} 项待评</span></span></div>
  <section v-for="g in groups" :key="g.p.id" class="section review-group">
   <div v-if="application" class="application-record"><el-checkbox v-if="g.p.status==='待审核'" :model-value="bulkIds.includes(g.p.id)" :aria-label="'选择审核 '+g.p.name" @change="(checked:unknown)=>bulkIds=checked?[...bulkIds,g.p.id]:bulkIds.filter(id=>id!==g.p.id)" /><div class="application-record-main"><small class="muted">{{g.tc?.name}} / {{g.task?.name}}</small><h2>{{g.p.name}}</h2><MarkdownText class="application-description" :text="g.p.description" /><div class="application-facts"><span>申请时间：{{g.p.created}}</span><span>组员：{{g.p.memberIds.map(id=>s.users.find(u=>u.id===id)?.name || id).join('、')}}</span></div></div><div class="application-record-actions"><Status :value="g.p.status" /><el-button type="primary" plain @click="showProject(g.p.id)">进入项目</el-button></div></div>
   <template v-else><div class="section-head"><div><small class="muted">{{g.tc?.name}} / {{g.task?.name}}</small><h2>{{g.p.name}}</h2><small class="muted">项目进度成绩 {{projectCourseScore(g.p,projectPhases(g.task,g.p)) ?? "—"}} / 100 · 占课程总评 {{courseDimensions(g.tc?.gradingRule).find(d=>d.source==='project')?.weight ?? 0}}%</small></div><el-button v-if="g.task?.taskKind!=='exercise'" plain @click="router.push(projectPath(s,g.p.id,'board'))">进入项目</el-button></div><el-table :data="g.phases"><el-table-column label="任务" min-width="180"><template #default="{row}">{{row.name}}</template></el-table-column><el-table-column prop="due" label="截止日期" width="120" /><el-table-column label="状态" width="120"><template #default="{row}"><Status :value="phaseStatus(g.p,row)" /></template></el-table-column><el-table-column label="评分" width="100"><template #default="{row}">{{phaseScore(g.p,row) ?? '—'}}</template></el-table-column><el-table-column label="操作" width="150"><template #default="{row}"><el-button :type="phaseStatus(g.p,row)==='待验收'?'primary':'default'" @click="inspect(g.p,row)">{{phaseStatus(g.p,row)==='待验收'?'预览与评分':'查看成果'}}</el-button></template></el-table-column></el-table></template>
  </section>
  <el-empty v-if="!groups.length" description="暂无符合筛选条件的项目" />
  <TeacherProjectDialog v-model="previewOpen" :project="preview" :application="application" />
  <el-dialog :model-value="!!selected" title="成果批阅" width="min(1200px,96vw)" top="4vh" destroy-on-close @close="selected=undefined"><ReviewWorkbench v-if="project && milestone" :key="project.id+milestone.id" :project="project" :milestone="milestone" @done="selected=undefined" /></el-dialog>
  <div v-if="application && !previewOpen" class="review-decision-bar"><span>待审核 {{reviewable.length}} 项</span><el-button v-if="bulkIds.length" @click="bulkIds=[]">清除选择</el-button><el-button type="primary" :disabled="!batchProjects.length" :loading="bulkBusy" @click="bulkApprove">批量审核 · 通过{{bulkIds.length?'所选':'全部'}} {{batchProjects.length}} 项</el-button></div>
 </div>
</template>
