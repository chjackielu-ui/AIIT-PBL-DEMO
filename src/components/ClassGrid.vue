<script setup lang="ts">
import {computed} from 'vue'
import type {TeachingClass} from '../types'
import {useStore} from '../store'

import {classMetrics, classPath} from '../teacher'
const props = defineProps<{classes: TeachingClass[]; destination?: string}>()
const store = useStore()
const s = computed(() => store.state!)
/** 每张卡片只计算一次指标，避免模板内重复全量扫描。 */
const cards = computed(() => props.classes.map(c => ({
  c,
  course: s.value.courses.find(x => x.id === c.courseId)?.name,
  link: classPath(c.id) + (props.destination || ''),
  metrics: classMetrics(s.value, c.id),
})))
</script>
<template>
  <div class="teaching-class-grid">
    <article v-for="card in cards" :key="card.c.id" class="teaching-class-card">
      <div class="section-head">
        <span>{{card.c.academicYear}} · {{card.c.semester}}</span>
        <span class="status">{{card.metrics.pending}} 项待办</span>
      </div>
      <RouterLink :to="card.link" class="card-title">{{card.c.name}}</RouterLink>
      <p class="class-course">{{card.course}}</p>
      <div class="card-bottom">
        <div class="class-numbers">
          <span>{{card.metrics.students}} 名学生</span>
          <span>{{card.metrics.projects.length}} 个项目</span>
        </div>
        <el-button type="primary" plain @click="$router.push(card.link)">进入班级</el-button>
      </div>
    </article>
    <el-empty v-if="!classes.length" description="暂无符合条件的教学班级" :image-size="56" />
  </div>
</template>
