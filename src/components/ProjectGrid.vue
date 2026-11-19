<script setup lang="ts">
import {computed,ref} from 'vue'
import type {Project} from '../types'
import {useStore} from '../store'

import {projectPath} from '../teacher'
import {projectDeadline, projectProgress} from '../projectPresentation'
import {isIndividual} from '../taskMode'
import Status from './Status.vue'
import TeacherProjectDialog from './TeacherProjectDialog.vue'
const props = defineProps<{projects: Project[]; tab?: string; actionLabel?: string; from?: string}>()
const store = useStore()
const s = computed(() => store.state!)
const teacher=computed(()=>store.user?.role==='teacher')
const previewId=ref(''),previewOpen=ref(false)
const preview=computed(()=>s.value.projects.find(p=>p.id===previewId.value))
function show(id:string){previewId.value=id;previewOpen.value=true}
const withFrom = (path: string) => props.from ? path + (path.includes('?') ? '&' : '?') + 'from=' + encodeURIComponent(props.from) : path
/** 每张卡片只计算一次进度与截止日期，并统一生成跳转链接。 */
const cards = computed(() => props.projects.map(p => {
  const link = store.user?.role === 'teacher'
    ? projectPath(s.value, p.id, props.tab)
    : `/${store.user?.role}/projects/${p.id}${props.tab ? '?tab=' + props.tab : ''}`
  const membersLink = store.user?.role === 'teacher'
    ? projectPath(s.value, p.id, 'members')
    : `/${store.user?.role}/projects/${p.id}?tab=members`
  return {
    p,
    individual: isIndividual(s.value.tasks.find(t=>t.id===p.taskId)),
    progress: projectProgress(s.value, p),
    deadline: projectDeadline(s.value, p),
    leader: s.value.users.find(u => u.id === p.leaderId)?.name || '—',
    link: withFrom(link),
    membersLink,
  }
}))
</script>
<template>
  <template v-if="cards.length">
    <div class="project-grid">
      <article v-for="card in cards" :key="card.p.id" class="project-card">
        <div class="section-head">
          <Status :value="card.p.status" />
          <span>{{card.individual?'个人项目':'小组项目 · '+card.p.memberIds.length+' 人'}}</span>
        </div>
        <button v-if="teacher" class="text-button card-title" @click="show(card.p.id)">{{card.p.name}}</button><RouterLink v-else :to="card.link" class="card-title">{{card.p.name}}</RouterLink>
        <dl class="project-facts">
          <div><dt>组长</dt><dd>{{card.leader}}</dd></div>
          <div><dt>开始</dt><dd>{{card.p.created.slice(0, 10)}}</dd></div>
          <div><dt>截止</dt><dd>{{card.deadline}}</dd></div>
        </dl>
        <div class="project-progress"><span>完成进度</span><strong>{{card.progress}}%</strong></div>
        <el-progress :percentage="card.progress" :show-text="false" :stroke-width="6" />
        <div class="card-bottom">
          <span v-if="teacher" class="muted">{{card.p.memberIds.map(id=>s.users.find(u=>u.id===id)?.name).join('、')}}</span><RouterLink v-else :to="card.membersLink">{{card.individual?'查看个人项目':'查看组队'}}</RouterLink>
          <el-button type="primary" plain @click="teacher?show(card.p.id):$router.push(card.link)">{{props.actionLabel || '进入项目'}}</el-button>
        </div>
      </article>
    </div>
  </template>
  <el-empty v-else description="暂无符合条件的项目" :image-size="56" />
  <TeacherProjectDialog v-if="teacher" v-model="previewOpen" :project="preview" />
</template>
