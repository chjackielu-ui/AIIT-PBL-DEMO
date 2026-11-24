<script setup lang="ts">
import {computed, ref} from 'vue'
import {useStore} from '../store'

import {latestTerm, relevantClasses} from '../academic'
import TermFilter from '../components/TermFilter.vue'
import PageHead from '../components/PageHead.vue'
import IdentityCard from '../components/IdentityCard.vue'
import ProjectGrid from '../components/ProjectGrid.vue'
import NotificationList from '../components/NotificationList.vue'

const store = useStore()
const s = computed(() => store.state!)

const term = latestTerm(relevantClasses(s.value))
const year = ref(term.academicYear)
const semester = ref(term.semester)

const classes = computed(() =>
  relevantClasses(s.value).filter(c => c.academicYear === year.value && c.semester === semester.value))
const tasks = computed(() => s.value.tasks.filter(t => classes.value.some(c => c.id === t.classId)))
const records = computed(() => s.value.projects.filter(p => tasks.value.some(t => t.id === p.taskId)))
const projects = computed(() => records.value.filter(p => tasks.value.find(t => t.id === p.taskId)?.taskKind !== 'exercise'))

</script>
<template>
  <PageHead title="管理员工作台">
    <TermFilter :classes="relevantClasses(s)" v-model:year="year" v-model:semester="semester" />
  </PageHead>
  <IdentityCard>
    <el-button type="primary" @click="$router.push('/admin/course-import')">导入小学期课程</el-button>
    <el-button @click="$router.push('/admin/users')">账号管理</el-button>
  </IdentityCard>
  
  <div>
  <section class="section">
    <div class="section-head">
      <h2>最近项目</h2>
      <RouterLink to="/admin/projects">查看全部</RouterLink>
    </div>
    <ProjectGrid :projects="[...projects].sort((a, b) => b.created.localeCompare(a.created)).slice(0, 8)" />
  </section>
  </div>
  <section class="section">
    <div class="section-head">
      <h2>通知</h2>
      <RouterLink to="/notifications">查看全部</RouterLink>
    </div>
    <NotificationList :notices="s.notifications.slice(0, 3)" />
    <el-empty v-if="!s.notifications.length" description="暂无通知" :image-size="40" />
  </section>
</template>

