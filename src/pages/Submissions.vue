<script setup lang="ts">
import {computed, ref} from 'vue'
import {useStore} from '../store'

import PageHead from '../components/PageHead.vue'
import Status from '../components/Status.vue'
import {phaseStatus, phaseScore} from '../projectPresentation'
const store=useStore()
const s=computed(()=>store.state!)
const filter=ref('待完成'), query=ref('')
const rows=computed(()=>s.value.projects.filter(p=>p.memberIds.includes(store.user!.id) && !['待审核','待修改','已关闭'].includes(p.status)).flatMap(p=>{
 const task=s.value.tasks.find(t=>t.id===p.taskId)!
 return (task?.milestones || []).filter(m=>m.requiresSubmission!==false || m.isFinal).map((m,index)=>({p,m,task,index,status:phaseStatus(p,m,store.user?.id)}))
}).sort((a,b)=>(a.task.created || '').localeCompare(b.task.created || '') || s.value.tasks.indexOf(a.task)-s.value.tasks.indexOf(b.task) || a.index-b.index))
const today=new Date().toLocaleDateString('sv-SE')
const overdue=computed(()=>rows.value.filter(r=>['未提交','退回修改'].includes(r.status)&&r.m.due<today))
const matches=(status:string,f:string)=>f==='全部' || (f==='待完成' ? ['未提交','待验收','退回修改'].includes(status) : status==='已通过')
const visible=computed(()=>rows.value.filter(r=>(filter.value==='已过期'?overdue.value.includes(r):matches(r.status,filter.value)) && (r.p.name+r.m.name+r.task.name).includes(query.value)))
const newTasks=computed(()=>s.value.tasks.filter(t=>t.status==='已发布' && !s.value.projects.some(p=>p.taskId===t.id)))
</script>
<template><PageHead title="任务概览与提交" /><section v-if="newTasks.length" class="section"><div class="section-head"><h2>新发布 · {{newTasks.length}} 项实训任务</h2><RouterLink to="/student/tasks">查看全部</RouterLink></div><div v-for="t in newTasks" :key="t.id" class="list-row"><span>{{t.name}} <small class="muted">{{s.teachingClasses.find(c=>c.id===t.classId)?.name}}</small></span><el-button @click="$router.push('/student/tasks/'+t.id)">查看任务 / 参与项目</el-button></div></section><div class="toolbar"><el-radio-group v-model="filter"><el-radio-button v-for="f in ['全部','待完成','已完成','已过期']" :key="f" :value="f">{{f}} · {{f==='已过期'?overdue.length:rows.filter(r=>matches(r.status,f)).length}}</el-radio-button></el-radio-group><el-input v-model="query" clearable placeholder="搜索任务或项目" /></div><el-table :data="visible" class="assignment-table"><el-table-column label="任务 / 所属项目" min-width="200"><template #default="{row}"><strong>{{row.m.name}}</strong><p class="muted">{{row.task.name}} / {{row.p.name}}</p></template></el-table-column><el-table-column label="提交要求" min-width="180"><template #default="{row}">{{row.m.materials || row.m.description}}</template></el-table-column><el-table-column label="截止日期" width="120"><template #default="{row}">{{row.m.due}}</template></el-table-column><el-table-column label="状态" width="120"><template #default="{row}"><Status :value="row.status" /></template></el-table-column><el-table-column label="评分" width="90"><template #default="{row}">{{phaseScore(row.p,row.m,store.user?.id) ?? '—'}}</template></el-table-column><el-table-column label="操作" width="140"><template #default="{row}"><el-button :type="['未提交','退回修改'].includes(row.status)?'primary':'default'" @click="$router.push(`/student/projects/${row.p.id}/submit/${row.m.id}`)">{{row.status==='退回修改'?'修改并提交':row.status==='未提交'?'查看 / 提交':'查看提交'}}</el-button></template></el-table-column><template #empty>暂无任务，可前往「可参与任务」查看教师发布的实训任务。</template></el-table></template>

