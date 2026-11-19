<script setup lang="ts">
import {computed} from 'vue'
import {useRouter} from 'vue-router'
import {useStore} from '../store'

import {roleName, run} from '../lib'
import PageHead from '../components/PageHead.vue'

const store = useStore()
const router = useRouter()
const s = computed(() => store.state!)
const administrativeClass = computed(() =>
  s.value?.classes.find(c => c.id === store.user?.classId)?.name)
</script>
<template>
  <PageHead title="当前用户信息" />
  <section class="section narrow">
    <div class="details-grid">
      <div><small>姓名</small><span>{{store.user?.name}}</span></div>
      <div><small>身份</small><span>{{roleName[store.user!.role]}}</span></div>
      <div><small>用户标识</small><span>{{store.user?.id}}</span></div>
      <div v-if="store.user?.number"><small>学号（仅本人 / 管理员可见）</small><span>{{store.user.number}}</span></div>
      <div v-if="store.user?.role === 'student'"><small>行政班级</small><span>{{administrativeClass || '未分配'}}</span></div>
    </div>
    <el-button @click="run(async () => {await store.logout(); await router.push('/login')}, '已退出登录')">退出登录</el-button>
  </section>
</template>
