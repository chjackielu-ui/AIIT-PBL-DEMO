<script setup lang="ts">
import {computed, ref} from 'vue'
import {CheckCheck, Bell, Settings2} from 'lucide-vue-next'
import {useStore} from '../store'

import {run, stamp} from '../lib'
import type {Notice} from '../types'
import {notificationTypes, useNotificationActions} from '../notifications'
import PageHead from '../components/PageHead.vue'

const store = useStore()
const {open,readAll} = useNotificationActions()
const unread = ref(false)
const category = ref('全部')
const query = ref('')
const categoryOf = (n:Notice) => n.type==='fileUpload' || n.type==='codeUpdate' ? '文件与代码' :
  n.type==='mentionMe' || n.type==='mentionAll' || n.type==='reply' || /讨论|提及/.test(n.title) ? '讨论消息' :
  /验收|结项|审核|评分|成绩|退回/.test(n.title) ? '审核与评分' : '课程与任务'
const notices = computed(() => store.state!.notifications.filter(n => (!unread.value || !n.read) && (category.value==='全部' || categoryOf(n)===category.value) && (n.title+n.text).includes(query.value)).sort((a,b)=>b.time.localeCompare(a.time)))

const settings = ref(false), saving = ref(false), selectedProject = ref('')
async function save(op: string, data: Record<string,unknown>) {
  if (saving.value) return
  saving.value = true
  await run(() => store.act(op,data), '')
  saving.value = false
}
</script>
<template>
  <PageHead title="通知中心">
    <el-button v-if="store.user?.role!=='admin'" @click="settings=true"><Settings2 :size="16" />通知设置</el-button>
    <el-button @click="readAll"><CheckCheck :size="16" />全部标记已读</el-button>
  </PageHead>
  <div class="toolbar">
    <el-radio-group v-model="unread">
      <el-radio-button :value="false">全部</el-radio-button>
      <el-radio-button :value="true">未读</el-radio-button>
    </el-radio-group>
    <el-select v-model="category" aria-label="通知分类"><el-option v-for="v in ['全部','课程与任务','审核与评分','讨论消息','文件与代码']" :key="v" :value="v" /></el-select>
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
  <el-dialog v-model="settings" title="通知设置" width="min(470px, 94vw)">
    <h3 class="notification-settings-title">接收通知</h3>
    <div v-for="item in notificationTypes" :key="item.type" class="notification-setting">
      <span>{{item.label}}</span>
      <el-switch :model-value="store.state?.notificationPreferences?.[item.type] !== false" :disabled="saving" :aria-label="item.label"
        @change="(enabled:boolean)=>save('setNotificationPreference',{type:item.type,enabled})" />
    </div>
    <template v-if="store.state?.projects.length">
      <h3 class="notification-settings-title project-mute-title">项目讨论免打扰</h3>
      <el-select v-model="selectedProject" filterable placeholder="选择项目" aria-label="选择需设置免打扰的项目">
        <el-option v-for="project in store.state?.projects" :key="project.id" :value="project.id"
          :label="project.name+(store.state?.mutedDiscussions?.[project.id]?' · 已免打扰':'')" />
      </el-select>
      <div v-if="selectedProject" class="notification-setting">
        <span>免打扰</span>
        <el-switch :model-value="!!store.state?.mutedDiscussions?.[selectedProject]" :disabled="saving" aria-label="项目讨论免打扰"
          @change="(muted:boolean)=>save('setDiscussionMute',{projectId:selectedProject,muted})" />
      </div>
      <p class="muted">免打扰期间暂停该项目讨论的提及、回复和代码片段通知。</p>
    </template>
  </el-dialog>
</template>

<style scoped>
.notification-settings-title{font-size:14px;margin:0 0 6px}
.notification-setting{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:4px 0;border-bottom:1px solid var(--line)}
.project-mute-title{margin-top:18px}
</style>
