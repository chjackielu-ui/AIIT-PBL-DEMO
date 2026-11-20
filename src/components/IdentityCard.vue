<script setup lang="ts">
import {computed} from 'vue'
import {useStore} from '../store'

import {roleName} from '../lib'
const store = useStore()
const u = computed(() => store.user!)
const displayName = computed(() =>
  u.value.role === 'student' && !u.value.name.includes('同学') ? u.value.name + '同学' : u.value.name)
const administrativeClass = computed(() =>
  store.state?.classes.find(c => c.id === u.value.classId)?.name)
</script>
<template>
  <section class="identity-card">
    <div class="identity-person">
      <span class="identity-avatar">{{u.name.slice(0, 1)}}</span>
      <div class="identity-copy">
        <h2>{{displayName}}，欢迎您</h2>
        <div class="identity-facts">
          <span>{{roleName[u.role]}}</span>
          <template v-if="u.role === 'student'">
            <span>学号 {{u.number || '未填写'}}</span>
            <span>{{administrativeClass || '未分配行政班'}}</span>
          </template>
          <span v-else>账号 {{u.id}}</span>
          <RouterLink to="/profile/settings">个人信息</RouterLink>
        </div>
      </div>
    </div>
    <div class="identity-shortcuts"><slot /></div>
  </section>
</template>
