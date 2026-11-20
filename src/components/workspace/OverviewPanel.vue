<script setup lang="ts">
import {computed} from 'vue'
import {useStore} from '../../store'
import type {Project} from '../../types'
import {fileDownloadUrl, stamp} from '../../lib'
import {projectDeadline, projectProgress,pendingSubmissions,projectPhases} from '../../projectPresentation'

const props = defineProps<{project: Project}>()
const emit = defineEmits<{goto: [tab: string]}>()
const store = useStore()
const s = computed(() => store.state!)
const teacher = computed(() => store.user?.role === 'teacher')
const task = computed(() => s.value.tasks.find(t => t.id === props.project.taskId))
const userName = (id: string) => s.value.users.find(u => u.id === id)?.name || id
const progress = computed(() => projectProgress(s.value, props.project))
const completed = computed(() => props.project.items.filter(i => i.status === '已完成').length)
const pendingReviews = computed(() => pendingSubmissions(props.project,projectPhases(task.value,props.project)).length)
</script>
<template>
  <div class="dashboard-grid">
    <section class="section">
      <h2>项目信息</h2>
      <div class="tags">
        <span v-for="tag in project.tags.split(/[,，/]/).filter(Boolean)" :key="tag">{{tag.trim()}}</span>
      </div>
      <div class="details-grid">
        <div><small>项目负责人</small><span>{{userName(project.leaderId)}}</span></div>
        <div><small>任课教师</small><span>{{s.teachingClasses.find(c => c.id === task?.classId)?.teacherIds.map(userName).join('、')}}</span></div>
        <div v-if="!teacher"><small>所属课程</small><span>{{s.courses.find(c => c.id === task?.courseId)?.name}}</span></div>
        <div v-if="!teacher"><small>教学班</small><span>{{s.teachingClasses.find(c => c.id === task?.classId)?.name}}</span></div>
        <div><small>开始时间</small><span>{{project.created.slice(0, 10)}}</span></div>
        <div><small>截止时间</small><span>{{projectDeadline(s, project)}}</span></div>
      </div>
    </section>
    <section class="section">
      <h2>项目进度</h2>
      <div class="progress-summary"><span>完成进度</span><strong>{{progress}}%</strong></div>
      <el-progress :percentage="progress" :stroke-width="6" :show-text="false" />
      <div class="progress-summary"><span>任务完成</span><strong>{{completed}} / {{project.items.length}}</strong></div>
      <div class="list-row"><span>{{teacher?'待验收阶段成果':'已提交阶段成果'}}</span><button class="text-button" @click="emit('goto', 'milestones')">{{pendingReviews}} 项</button></div>
      <div class="list-row"><span>项目资料</span><button class="text-button" @click="emit('goto', 'files')">{{project.files.length}} 份</button></div>
      <div class="list-row"><span>讨论消息</span><button class="text-button" @click="emit('goto', 'discussion')">{{project.messages.length}} 条</button></div>
    </section>
  </div>
  <div class="project-bottom-grid">
  <section class="section">
    <div class="section-head">
      <h2>当前项目资料 <span class="muted">{{project.files.length}} 份</span></h2>
      <el-button @click="emit('goto', 'files')">查看全部与预览</el-button>
    </div>
    <div v-for="f in project.files.slice(0, 3)" :key="f.id" class="list-row">
      <div>
        <button class="text-button" @click="emit('goto', 'files')">{{f.name}}</button>
        <p class="muted">{{f.category}} · {{userName(f.userId)}} · {{stamp(f.time)}}</p>
      </div>
      <a :href="fileDownloadUrl(project.id, f.id)">下载</a>
    </div>
    <el-empty v-if="!project.files.length" description="当前项目尚未上传资料" :image-size="56" />
  </section>
  <section class="section">
    <div class="section-head">
      <h2>最近动态</h2>
      <el-button @click="emit('goto', 'activity')">全部动态</el-button>
    </div>
    <div v-for="(a, i) in project.activity.slice(0, 3)" :key="i" class="activity-row">
      <span class="timeline-dot" />
      <div>{{a.text}}<p class="muted">{{userName(a.userId)}}</p></div>
      <time>{{stamp(a.time)}}</time>
    </div>
  </section>
  </div>
</template>
