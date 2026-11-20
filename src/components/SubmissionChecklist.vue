<script setup lang="ts">
import {ref} from 'vue'
import type {DeliverableRequirement} from '../types'
import DeliverableEditor from './DeliverableEditor.vue'

const rows = defineModel<DeliverableRequirement[]>({required:true})
const enabled = ref(rows.value.length > 0)
let disabledRows: DeliverableRequirement[] = []
function toggle() {
  if (enabled.value) rows.value = disabledRows
  else {
    disabledRows = rows.value
    rows.value = []
  }
}
</script>

<template>
  <el-form-item label="配置逐项提交清单">
    <el-switch v-model="enabled" @change="toggle" />
    <small v-if="!enabled" class="muted checklist-hint">学生至少上传 1 个附件。</small>
  </el-form-item>
  <DeliverableEditor v-if="enabled" v-model="rows" />
</template>

<style scoped>
.checklist-hint {margin-left:12px}
</style>
