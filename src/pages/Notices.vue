<script setup lang="ts">
import {computed, ref} from 'vue'
import {CheckCheck, Bell} from 'lucide-vue-next'
import {useStore} from '../store'

import {run, stamp} from '../lib'
import type {Notice} from '../types'
import {useRouter} from 'vue-router'
import PageHead from '../components/PageHead.vue'

const store = useStore()
const router = useRouter()
const unread = ref(false)
const category = ref('全部')
const query = ref('')
const categoryOf = (n:Notice) => /讨论|提及/.test(n.title) ? '讨论消息' : /验收|结项|审核|评分|成绩|退回/.test(n.title) ? '审核与评分' : '课程与任务'
const notices = computed(() => store.state!.notifications.filter(n => (!unread.value || !n.read) && (category.value==='全部' || categoryOf(n)===category.value) && (n.title+n.text).includes(query.value)).sort((a,b)=>b.time.localeCompare(a.time)))

async function open(n: Notice) {
  await run(async () => {
    await store.act('readNotices', {id: n.id})
    await router.push(n.path)
  }, '')
}
</script>
<template>
  <PageHead title="通知中心">
    <el-button @click="run(() => store.act('readNotices'), '全部通知已读')"><CheckCheck :size="16" />全部标记已读</el-button>
  </PageHead>
  <div class="toolbar">
    <el-radio-group v-model="unread">
      <el-radio-button :value="false">全部</el-radio-button>
      <el-radio-button :value="true">未读</el-radio-button>
    </el-radio-group>
    <el-select v-model="category" aria-label="通知分类"><el-option v-for="v in ['全部','课程与任务','审核与评分','讨论消息']" :key="v" :value="v" /></el-select>
    <el-input v-model="query" placeholder="搜索通知" clearable />
    <span class="muted">共 {{notices.length}} 条</span>
  </div>
  <button v-for="n in notices" :key="n.id" class="notification-row" @click="open(n)">
    <span class="notice-icon"><Bell :size="20" /></span>
    <div>
      <strong>{{n.title}} <span v-if="!n.read" class="unread-dot" /></strong>
      <p>{{n.text}}</p>
      <small>{{stamp(n.time)}}</small>
    </div>
    <span class="muted">{{n.read ? '已读' : '查看通知'}}</span>
  </button>
  <el-empty v-if="!notices.length" description="暂无通知" :image-size="64" />
</template>
