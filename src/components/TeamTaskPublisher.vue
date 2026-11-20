<script setup lang="ts">
import {reactive,ref} from 'vue'
import type {Project} from '../types'
import {useStore} from '../store'

import {run,localDate} from '../lib'
const props=defineProps<{project:Project}>(),store=useStore()
const open=ref(false),busy=ref(false)
const form=reactive({title:'',due:'',ownerId:''})
function start(){
  Object.assign(form,{title:'',due:localDate(14),ownerId:props.project.leaderId})
  open.value=true
}
async function save(){
  busy.value=true
  await run(async()=>{
    if(!form.title.trim() || !form.due || !form.ownerId)throw Error('请填写任务标题、截止日期和负责人')
    await store.act('addItem',{projectId:props.project.id,...form})
    open.value=false
  },'工作项已布置')
  busy.value=false
}
</script>
<template>
  <el-button @click="start">布置工作项</el-button>
  <el-dialog v-model="open" title="布置工作项" width="min(560px,94vw)">
    <el-form label-position="top">
      <el-form-item label="任务标题" required><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="负责学生" required><el-select v-model="form.ownerId"><el-option v-for="id in project.memberIds" :key="id" :value="id" :label="store.state?.users.find(u=>u.id===id)?.name || id" /></el-select></el-form-item>
      <el-form-item label="截止日期" required><el-date-picker v-model="form.due" value-format="YYYY-MM-DD" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="open=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">布置工作项</el-button></template>
  </el-dialog>
</template>
