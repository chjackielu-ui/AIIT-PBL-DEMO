<script setup lang="ts">
import {ref} from 'vue'
import {ElMessageBox} from 'element-plus'
import {useStore} from '../store'

import {run} from '../lib'
import type {Project} from '../types'
const props=defineProps<{project:Project;inline?:boolean}>()
const store=useStore(),busy=ref(false)
async function decide(pass:boolean){
 if(busy.value)return
 let comment='审核通过'
 try{
  if(pass)await ElMessageBox.confirm(`是否通过审核「${props.project.name}」项目？`,'项目申请与审核',{confirmButtonText:'确认通过',cancelButtonText:'取消',type:'warning'})
  else {const result=await ElMessageBox.prompt(`是否打回「${props.project.name}」项目？请填写修改意见。`,'项目申请与审核',{confirmButtonText:'确认打回',cancelButtonText:'取消',inputType:'textarea',inputValidator:value=>!!value?.trim() || '请填写打回原因'});comment=result.value}
 }catch{return}
 busy.value=true
 await run(()=>store.act('reviewProject',{projectId:props.project.id,pass,comment}),pass?'项目申请已通过':'项目申请已打回')
 busy.value=false
}
</script>
<template><div class="review-decision-bar" :class="{'inline-decision':inline}"><span>{{project.name}}</span><el-button type="primary" :loading="busy" :disabled="project.status!=='待审核'" @click="decide(true)">通过</el-button><el-button type="danger" :loading="busy" :disabled="project.status!=='待审核'" @click="decide(false)">打回</el-button></div></template>
<style scoped>.review-decision-bar.inline-decision{position:static;box-shadow:none;margin-top:18px;justify-content:flex-end;padding:12px 0 0;border:0;border-top:1px solid var(--line);background:transparent;flex-wrap:wrap}</style>
