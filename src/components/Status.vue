<script setup lang="ts">
import {useStore} from '../store'

const store=useStore()
const tone: Record<string, 'ok' | 'warn' | 'muted'> = {
  // 进行态 / 通过态
  '进行中': 'ok', '已通过': 'ok', '已完成': 'ok', '已发布': 'ok', '已归档': 'ok',
  // 待办 / 退回 / 锁定
  '待审核': 'warn', '待修改': 'warn', '待验收': 'warn', '待开始': 'warn',
  '结项待审核': 'warn', '结项待修改': 'warn', '退回修改': 'warn',
  // 中性 / 关闭
  '已关闭': 'muted', '未提交': 'muted', '草稿': 'muted', '历史补录': 'muted',
}
defineProps<{value: string}>()
</script>
<template><span class="status" :class="tone[value]">{{store.user?.role==='student' && value==='待验收'?'已提交':value}}</span></template>
