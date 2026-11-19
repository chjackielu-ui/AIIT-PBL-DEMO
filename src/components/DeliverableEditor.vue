<script setup lang="ts">
import type {DeliverableRequirement} from '../types'
const rows=defineModel<DeliverableRequirement[]>({default:()=>[]})
function add(kind:'slides'|'source'|'custom'){
 rows.value=[...rows.value,{id:crypto.randomUUID(),title:kind==='slides'?'汇报 PPT':kind==='source'?'项目源代码':'提交材料',extensions:kind==='slides'?['.ppt','.pptx']:kind==='source'?['.zip','.rar','.7z']:['.pdf'],required:true,minFiles:1,maxFiles:5}]
}
function extensions(row:DeliverableRequirement,value:string){row.extensions=value.toLowerCase().split(/[\s,，;；]+/).filter(Boolean).map(v=>v.startsWith('.')?v:'.'+v)}
</script>
<template>
 <div class="deliverable-editor"><div class="section-head"><h3>提交清单</h3><div class="actions wrap"><el-button @click="add('slides')">＋ PPT</el-button><el-button @click="add('source')">＋ 源代码压缩包</el-button><el-button @click="add('custom')">＋ 其他材料</el-button></div></div>
 <p class="muted">逐项填写材料名称、格式和数量。学生按清单选择文件，提交时系统再次校验。</p>
 <div v-for="(r,i) in rows" :key="r.id" class="phase-form">
  <div class="section-head"><strong>材料 {{i+1}}</strong><el-button text type="danger" @click="rows=rows.filter(x=>x.id!==r.id)">移出清单</el-button></div>
  <div class="form-grid"><el-form-item label="材料名称"><el-input v-model="r.title" /></el-form-item><el-form-item label="允许的文件后缀"><el-input :model-value="r.extensions.join(', ')" placeholder=".ppt, .pptx" @change="(v:string)=>extensions(r,v)" /></el-form-item><el-form-item label="最少文件数"><el-input-number v-model="r.minFiles" :min="1" :max="20" /></el-form-item><el-form-item label="最多文件数"><el-input-number v-model="r.maxFiles" :min="r.minFiles" :max="20" /></el-form-item></div>
  <el-switch v-model="r.required" active-text="必交" inactive-text="选交" />
 </div><p v-if="!rows.length" class="muted">未设置逐项清单，按任务的附件要求提交。</p></div>
</template>
