<script setup lang="ts">
import MarkdownInput from './MarkdownInput.vue'
import {computed} from 'vue'
import type {RubricPoint} from '../types'
const rows=defineModel<RubricPoint[]>({default:()=>[]})
const total=computed(()=>rows.value.reduce((sum,p)=>sum+(p.maxPoints || 0),0))
function add(){rows.value.push({id:crypto.randomUUID(),dimension:'',title:'',description:'',maxPoints:25})}
</script>
<template><div class="rubric-editor"><div class="section-head"><h3>评分表与观测点</h3><el-button size="small" :disabled="rows.length>=30" @click="add">添加观测点</el-button></div><p class="muted">留空使用教师百分制。设置观测点后，按“得分总和 ÷ 满分总和 × 100”换算人员得分。</p><div v-for="(p,index) in rows" :key="p.id" class="phase-form"><div class="section-head"><strong>观测点 {{index+1}}</strong><el-button text type="danger" @click="rows.splice(index,1)">移除此项</el-button></div><div class="form-grid"><el-form-item label="评分维度"><el-input v-model="p.dimension" placeholder="例如：项目内涵" /></el-form-item><el-form-item label="观测点"><el-input v-model="p.title" placeholder="例如：创新度" /></el-form-item><el-form-item label="满分"><el-input-number v-model="p.maxPoints" :min="0.01" :max="100" :precision="2" /></el-form-item></div><el-form-item label="得分依据 / 评分说明"><MarkdownInput v-model="p.description" :rows="2" /></el-form-item></div><p v-if="rows.length">{{rows.length}} 个观测点 · 原始满分 {{total}} 分 · 最终统一为 100 分制</p></div></template>
