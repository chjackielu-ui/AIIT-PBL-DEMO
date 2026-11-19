<script setup lang="ts">
import type {DeliverableRequirement,ProjectFile} from '../types'
defineProps<{requirements:DeliverableRequirement[];files:ProjectFile[];disabled?:boolean}>()
const selected=defineModel<Record<string,string[]>>({default:()=>({})})
const fits=(f:ProjectFile,r:DeliverableRequirement)=>r.extensions.some(ext=>f.name.toLowerCase().endsWith(ext.toLowerCase()))
</script>
<template><div v-if="requirements.length" class="deliverable-picker"><h3>逐项提交清单</h3><div v-for="r in requirements" :key="r.id" class="phase-form"><div class="section-head"><strong>{{r.title}} <el-tag :type="r.required?'danger':'info'" size="small">{{r.required?'必交':'选交'}}</el-tag></strong><span class="muted">{{r.extensions.join(' / ')}} · {{r.minFiles}}–{{r.maxFiles}} 份</span></div><el-select v-model="selected[r.id]" multiple filterable :multiple-limit="r.maxFiles" :disabled="disabled" :aria-label="r.title+'对应文件'" placeholder="从项目资料库选择符合格式的文件"><el-option v-for="f in files.filter(f=>fits(f,r))" :key="f.id" :value="f.id" :label="f.name" /></el-select><small v-if="r.required && (selected[r.id]?.length || 0)<r.minFiles" class="error-text">还需选择至少 {{r.minFiles-(selected[r.id]?.length || 0)}} 份</small></div></div></template>
