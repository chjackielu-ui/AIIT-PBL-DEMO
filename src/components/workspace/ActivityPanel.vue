<script setup lang="ts">
import {computed} from 'vue'
import {useStore} from '../../store'
import type {Project} from '../../types'
import {stamp} from '../../lib'

const props = defineProps<{project: Project}>()
const store = useStore()
const s = computed(() => store.state!)
const userName = (id: string) => s.value.users.find(u => u.id === id)?.name || id
</script>
<template>
  <section class="section">
    <h2>动态记录</h2>
    <div v-for="(a, i) in project.activity" :key="i" class="activity-row">
      <span class="timeline-dot" />
      <div>{{a.text}}<p class="muted">{{userName(a.userId)}}</p></div>
      <time>{{stamp(a.time)}}</time>
    </div>
    <el-empty v-if="!project.activity.length" description="暂无动态记录" :image-size="56" />
  </section>
</template>
