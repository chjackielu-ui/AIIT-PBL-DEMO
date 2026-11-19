<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {Search} from 'lucide-vue-next'
import {useStore} from '../store'

import {latestTerm,relevantClasses} from '../academic'
import {isIndividual} from '../taskMode'
import {teachingClasses} from '../teacher'
import PageHead from '../components/PageHead.vue'
import ProjectGrid from '../components/ProjectGrid.vue'
import TermFilter from '../components/TermFilter.vue'
import TeacherContext from '../components/TeacherContext.vue'
const store=useStore(),route=useRoute(),router=useRouter()
const s=computed(()=>store.state!)
const teacher=computed(()=>store.user?.role==='teacher')
const tc=computed(()=>teachingClasses(s.value).find(c=>c.id===route.params.classId))
const title=computed(()=>teacher.value?'关联项目':store.user?.role==='admin'?'项目列表':'我的项目')
const term=latestTerm(relevantClasses(s.value))
const year=ref(term.academicYear),semester=ref(term.semester),query=ref(''),status=ref(String(route.query.status || '')),page=ref(1),sort=ref('newest'),projectType=ref('')
watch(()=>route.query.status,v=>{status.value=String(v || '');page.value=1})
watch([query,status,projectType,sort,year,semester,()=>route.query.taskId],()=>{page.value=1})
const filtered=computed(()=>s.value.projects.filter(p=>
 (teacher.value?s.value.tasks.some(t=>t.id===p.taskId && t.classId===tc.value?.id):s.value.tasks.some(t=>t.id===p.taskId && relevantClasses(s.value).some(c=>c.id===t.classId && c.academicYear===year.value && c.semester===semester.value)))
 && (!route.query.taskId || p.taskId===route.query.taskId)
 && (!projectType.value || (isIndividual(s.value.tasks.find(t=>t.id===p.taskId))?'individual':'group')===projectType.value)
 && (!status.value || p.status===status.value)
 && (p.name+' '+p.tags+' '+p.memberIds.map(id=>s.value.users.find(u=>u.id===id)?.name).join(' ')).toLowerCase().includes(query.value.toLowerCase()))
 .sort((a,b)=>sort.value==='newest'?b.created.localeCompare(a.created):a.name.localeCompare(b.name,'zh-CN')))
</script>
<template>
 <TeacherContext v-if="teacher && tc" :label="title" />
 <PageHead :title="title"><TermFilter v-if="!teacher" :classes="relevantClasses(s)" v-model:year="year" v-model:semester="semester" /><el-button v-if="store.user?.role==='student'" type="primary" @click="router.push('/student/tasks')">从课程任务创建项目</el-button></PageHead>
 <div class="toolbar"><el-input v-model="query" placeholder="搜索项目名称、技术或成员" clearable><template #prefix><Search :size="16" /></template></el-input><el-select v-model="status" clearable placeholder="全部状态"><el-option v-for="v in ['待审核','待修改','进行中','结项待审核','结项待修改','已归档','已关闭']" :key="v" :value="v" /></el-select><el-select v-model="projectType" clearable placeholder="全部项目类型"><el-option value="individual" label="个人项目" /><el-option value="group" label="小组集体项目" /></el-select><el-select v-model="sort" aria-label="项目排序"><el-option value="newest" label="最近创建" /><el-option value="name" label="项目名称" /></el-select><el-button v-if="route.query.taskId" @click="router.replace({path:route.path})">查看班级全部项目</el-button><span class="muted">共 {{filtered.length}} 个项目</span></div>
 <section v-if="store.user?.role==='student' && !s.projects.length" class="section"><h2>尚未创建项目</h2><el-button type="primary" @click="router.push('/student/tasks')">查看可参与的任务并创建项目</el-button></section>
 <ProjectGrid :projects="filtered.slice((page-1)*12,page*12)" />
 <el-pagination v-if="filtered.length>12" v-model:current-page="page" :page-size="12" :total="filtered.length" layout="total,prev,pager,next" />
</template>
