<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import {useStore} from '../store'

import type {Project} from '../types'
import {run} from '../lib'

const props = defineProps<{project: Project; readonly: boolean}>()
const store = useStore()
const s = computed(() => store.state!)
const dialog = ref(false)
const form = reactive({role: '', description: '', evidence: ''})

function open() {
  Object.assign(form, {role: '', description: '', evidence: ''})
  const c = props.project.contributions.find(c => c.userId === store.user?.id)
  if (c) Object.assign(form, c)
  dialog.value = true
}

const userName = (id: string) => s.value.users.find(u => u.id === id)?.name || id
</script>
<template>
  <div class="section-head">
    <h2>成员与贡献</h2>
    <el-button v-if="store.user?.role === 'student' && !readonly && ['进行中', '结项待修改'].includes(project.status)" @click="open">填写我的职责与贡献</el-button>
  </div>
  <el-table :data="project.contributions">
    <el-table-column label="成员" width="130">
      <template #default="{row: c}">{{userName(c.userId)}}<div class="table-sub">{{c.userId === project.leaderId ? '项目负责人' : '成员'}}</div></template>
    </el-table-column>
    <el-table-column prop="role" label="职责" min-width="130" />
    <el-table-column prop="description" label="个人贡献" min-width="200" />
    <el-table-column prop="evidence" label="佐证说明" min-width="160" />
    <el-table-column label="贡献比例" width="110">
      <template #default="{row: c}">{{project.final ? c.percent + '%' : '最终验收时填写'}}</template>
    </el-table-column>
    <el-table-column label="个人成绩 / 评语" min-width="170">
      <template #default="{row: c}">
        <template v-if="c.score != null">{{c.score}} 分 · {{c.comment || '—'}}</template>
        <span v-else class="muted">待确认</span>
      </template>
    </el-table-column>
  </el-table>
  <p class="muted">贡献比例仅在最终验收提交时由提交成员填写，提交后由教师确认；审核中不可修改。成员名单提交后仅教师可调整。</p>
  <el-dialog v-model="dialog" title="我的职责与个人贡献" width="600px">
    <el-form label-position="top">
      <el-form-item label="职责" required><el-input v-model="form.role" /></el-form-item>
      <el-form-item label="个人贡献说明" required><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="佐证材料 / 文件说明" required><el-input v-model="form.evidence" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" @click="run(async () => {await store.act('saveContribution', {projectId: project.id, ...form}); dialog = false})">保存贡献</el-button>
    </template>
  </el-dialog>
</template>

