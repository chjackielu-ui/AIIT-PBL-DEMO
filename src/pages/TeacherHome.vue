<script setup lang="ts">
import {computed, ref} from 'vue'
import {useStore} from '../store'

import {latestTerm} from '../academic'
import {teachingClasses, intentPath} from '../teacher'
import TermFilter from '../components/TermFilter.vue'
import PageHead from '../components/PageHead.vue'
import IdentityCard from '../components/IdentityCard.vue'
import ClassGrid from '../components/ClassGrid.vue'
import NotificationList from '../components/NotificationList.vue'

const store = useStore()
const s = computed(() => store.state!)
const allClasses = computed(() => teachingClasses(s.value))

const term = latestTerm(allClasses.value)
const year = ref(term.academicYear)
const semester = ref(term.semester)
const classes = computed(() =>
  allClasses.value.filter(c => c.academicYear === year.value && c.semester === semester.value))

</script>
<template>
  <PageHead title="教师工作台">
    <TermFilter :classes="allClasses" v-model:year="year" v-model:semester="semester" />
  </PageHead>
  <IdentityCard>
    <el-button type="primary" @click="$router.push(intentPath('publish'))">发布实训任务</el-button>
    <el-button @click="$router.push('/teacher/task-register')">任务总表</el-button>
  </IdentityCard>
  
  <section class="section">
    <div class="section-head">
      <h2>我的实训班级</h2>
      <div class="actions">
        <el-button @click="$router.push('/teacher/classes')">全部班级</el-button>
        
      </div>
    </div>
    <ClassGrid :classes="classes" />
  </section>
  <section class="section">
    <div class="section-head">
      <h2>通知</h2>
      <RouterLink to="/notifications">查看全部</RouterLink>
    </div>
    <NotificationList :notices="s.notifications.slice(0, 3)" />
    <el-empty v-if="!s.notifications.length" description="暂无新通知" :image-size="40" />
  </section>
</template>
