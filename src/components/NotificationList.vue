<script setup lang="ts">
import {useRouter} from 'vue-router'
import {useStore} from '../store'

import type {Notice} from '../types'
import {run, stamp} from '../lib'

defineProps<{notices: Notice[]}>()
const store = useStore()
const router = useRouter()

/** 打开通知：先标记已读再跳转目标地址（首页摘要与通知中心共用同一行为）。 */
async function open(n: Notice) {
  await run(async () => {
    await store.act('readNotices', {id: n.id})
    await router.push(n.path)
  }, '')
}
</script>
<template>
  <RouterLink v-for="n in notices" :key="n.id" to="/notifications" class="notice-summary" @click.prevent="open(n)">
    <strong>{{n.title}}</strong>
    <span>{{n.text}}</span>
    <small class="muted">{{stamp(n.time)}}</small>
  </RouterLink>
</template>
