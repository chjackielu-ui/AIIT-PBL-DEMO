<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import QRCode from 'qrcode'
import {Copy, ExternalLink, Download} from 'lucide-vue-next'
import {useStore} from '../store'

import type {Project} from '../types'
import {confirmAction, run, printPage, openLink} from '../lib'

const props = defineProps<{project: Project}>()
const store = useStore()
const qr = ref('')
const link = computed(() => `${location.origin}/share/${props.project.shareId}`)
const canManage = computed(() => store.user?.role === 'teacher' || props.project.leaderId === store.user?.id)
watch(link, async value => {
  qr.value = await QRCode.toDataURL(value, {width: 180, margin: 1, color: {dark: '#1f2937', light: '#ffffff'}})
}, {immediate: true})

async function copy() {
  await run(async () => {await navigator.clipboard.writeText(link.value)}, '分享链接已复制')
}

async function toggle() {
  if (props.project.share) {
    await confirmAction('关闭后，此链接和二维码将不再展示项目内容。', () => store.act('share', {projectId: props.project.id, enabled: false}))
  } else {
    await run(() => store.act('share', {projectId: props.project.id, enabled: true}), '成果分享已启用')
  }
}
</script>
<template>
  <div class="section-head">
    <h2>项目介绍与分享</h2>
    <el-button @click="printPage('project', project.id)"><Download :size="16" />项目介绍 PDF</el-button>
  </div>
  <div class="share-layout">
    <div>
      <p>公开内容：项目简介、成员姓名与角色、已审核的最终成果及截图。</p>
      <p class="muted">学号、讨论、内部文件、草稿和未审核成果不会进入分享页。</p>
      <div class="info-strip">分享状态：<strong>{{project.share ? '已开启' : '已关闭'}}</strong></div>
      <el-input :model-value="link" readonly aria-label="项目分享链接" />
      <div class="actions wrap">
        <el-button @click="copy"><Copy :size="16" />复制链接</el-button>
        <el-button @click="openLink(link)"><ExternalLink :size="16" />{{project.share ? '打开公开页' : '预览链接状态'}}</el-button>
        <el-button v-if="canManage" :type="project.share ? 'default' : 'primary'" :disabled="(!project.final?.approved || project.status !== '已归档') && !project.share" @click="toggle">{{project.share ? '关闭分享' : '开启分享'}}</el-button>
      </div>
      <p v-if="!project.final?.approved" class="muted">最终成果经教师审核通过后可开启分享。</p>
      <p v-else-if="project.status !== '已归档'" class="muted">项目已重新开放，请完成结项归档后再开启分享。</p>
    </div>
    <div class="qr">
      <img :src="qr" alt="项目分享二维码" />
      <small>当前本机地址 · 同机浏览器可访问</small>
    </div>
  </div>
</template>
