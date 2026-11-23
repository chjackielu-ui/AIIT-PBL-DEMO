<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {MessagesSquare, ArrowUpRight, Bell, BellOff, Search, Users} from 'lucide-vue-next'
import {useStore} from '../store'

import {projectPath} from '../teacher'
import {run} from '../lib'
import Discussion from '../components/Discussion.vue'
import '../discussion.css'
const store=useStore(),route=useRoute(),router=useRouter()
const search=ref(''), searchOpen=ref(false)
const discussion=ref<InstanceType<typeof Discussion>>()
const courseId=ref('')
const courses=computed(()=>store.state!.courses.filter(c=>store.state!.projects.some(p=>store.state!.tasks.some(t=>t.id===p.taskId && t.courseId===c.id))))
const latest=computed(()=>store.state!.projects.map((p,index)=>({p,index})).sort((a,b)=>b.p.created.localeCompare(a.p.created) || b.index-a.index).map(entry=>entry.p))
const projects=computed(()=>latest.value.filter(p=>!courseId.value || store.state!.tasks.some(t=>t.id===p.taskId && t.courseId===courseId.value)))
const project=computed(()=>latest.value.find(p=>p.id===route.params.id))
const muted=computed(()=>!!store.state?.mutedDiscussions?.[project.value?.id || ''])
const savingMute=ref(false)
async function toggleMute(){
 if(!project.value || savingMute.value)return
 savingMute.value=true
 const value=!muted.value
 await run(()=>store.act('setDiscussionMute',{projectId:project.value!.id,muted:value}),value?'已开启讨论免打扰':'已恢复讨论通知')
 savingMute.value=false
}
const readonly=computed(()=>!project.value || store.user?.role==='admin' || ['待审核','待修改','已归档','已关闭'].includes(project.value.status))
const course=(taskId:string)=>{const t=store.state!.tasks.find(t=>t.id===taskId);return store.state!.teachingClasses.find(c=>c.id===t?.classId)?.name || ''}
function selectProject(id:string){router.push(`/${store.user?.role}/discussions/${id}`)}
function selectCourse(){const first=projects.value[0];if(first)selectProject(first.id)}
watch(()=>route.params.id,()=>{search.value='';searchOpen.value=false})
watch([()=>route.params.id,latest],()=>{
 const selected=project.value
 if(selected)courseId.value=store.state!.tasks.find(t=>t.id===selected.taskId)?.courseId || ''
 else if(!route.params.id && latest.value[0])router.replace(`/${store.user?.role}/discussions/${latest.value[0].id}`)
},{immediate:true})
function openProject(){if(project.value)router.push(store.user?.role==='teacher'?projectPath(store.state!,project.value.id):`/${store.user?.role}/projects/${project.value.id}`)}
</script>
<template>
 <div class="discussion-page">
  <div class="collaboration-heading">
   <div class="discussion-context-row"><span class="muted">课程</span><el-select v-model="courseId" class="discussion-course-select" placeholder="选择课程" aria-label="按课程筛选讨论项目" @change="selectCourse"><el-option v-for="c in courses" :key="c.id" :value="c.id" :label="c.name" /></el-select>
   <h1><el-select :model-value="project?.id" filterable placeholder="选择项目" aria-label="筛选并进入项目协作空间" @change="selectProject"><el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name"><span>{{p.name}}</span><small class="muted"> · {{course(p.taskId)}}</small></el-option></el-select><span>的协作空间</span></h1></div>
   <div class="collaboration-heading-actions">
    <button v-if="project && store.user?.role!=='admin'" class="icon-button" :class="{selected:muted}" :disabled="savingMute"
     :aria-pressed="muted" :aria-label="muted?'关闭讨论免打扰':'打开讨论免打扰'" :title="muted?'关闭讨论免打扰':'打开讨论免打扰'" @click="toggleMute">
      <component :is="muted ? BellOff : Bell" :size="19" />
    </button>
    <button v-if="project" class="icon-button" aria-label="项目成员" title="项目成员" @click="discussion?.openMembers()"><Users :size="19" /></button>
    <el-popover v-model:visible="searchOpen" trigger="click" :width="320" placement="bottom-end"><template #reference><button class="icon-button" aria-label="搜索协作消息与文件" title="搜索消息与群文件" :class="{selected:search}"><Search :size="19" /></button></template><el-input v-model="search" clearable placeholder="搜索消息、成员、代码或群文件" aria-label="搜索协作消息与文件" /><small class="muted">同时筛选当前项目的消息与群文件</small></el-popover>
    <el-button v-if="project" @click="openProject">返回项目<ArrowUpRight :size="15" /></el-button>
   </div>
  </div>
  <div v-if="search" class="collaboration-search-summary">正在筛选：{{search}}<el-button text size="small" @click="search=''">清除</el-button></div>
  <div class="collaboration-layout">
   <Discussion ref="discussion" v-if="project" :key="project.id" :project="project" :readonly="readonly" :search="search"
    :target-message="typeof route.query.message==='string'?route.query.message:undefined"
    :target-code="typeof route.query.code==='string'?route.query.code:undefined" @clear-search="search=''" />
   <div v-else class="channel-empty"><MessagesSquare :size="44" /><h2>{{route.params.id?'项目不存在或无访问权限':'暂无可进入的项目'}}</h2><p>在上方选择项目，进入协作空间。</p></div>
  </div>
 </div>
</template>
