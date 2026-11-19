<script setup lang="ts">
import {computed,reactive,ref} from 'vue'
import type {Project} from '../types'
import {useStore} from '../store'

import {run,localDate} from '../lib'
const props=defineProps<{projects:Project[]}>(),store=useStore()
const open=ref(false),busy=ref(false)
const available=computed(()=>props.projects.filter(p=>['进行中','结项待修改'].includes(p.status)))
const form=reactive({projectId:'',title:'',due:localDate(14),ownerId:''})
const project=computed(()=>available.value.find(p=>p.id===form.projectId))
function select(){form.ownerId=project.value?.leaderId || ''}
function start(){Object.assign(form,{projectId:available.value[0]?.id || '',title:'',due:localDate(14)});select();open.value=true}
async function save(){busy.value=true;await run(async()=>{if(!form.title.trim() || !form.due || !project.value)throw Error('请填写任务标题、截止日期并选择团队');await store.act('addItem',form);open.value=false},'团队任务已布置，可在学生任务看板查看');busy.value=false}
</script>
<template><div class="team-task-publisher"><el-button :disabled="!available.length" @click="start">发布团队任务</el-button><small class="muted">布置到指定团队的任务看板，由负责学生更新完成情况。</small></div><el-dialog v-model="open" title="发布团队任务" width="min(560px,94vw)"><el-form label-position="top"><el-form-item label="团队项目" required><el-select v-model="form.projectId" @change="select"><el-option v-for="p in available" :key="p.id" :value="p.id" :label="p.name" /></el-select></el-form-item><el-form-item label="任务标题" required><el-input v-model="form.title" /></el-form-item><el-form-item label="负责学生" required><el-select v-model="form.ownerId"><el-option v-for="id in project?.memberIds" :key="id" :value="id" :label="store.state?.users.find(u=>u.id===id)?.name || id" /></el-select></el-form-item><el-form-item label="截止日期" required><el-date-picker v-model="form.due" value-format="YYYY-MM-DD" /></el-form-item></el-form><template #footer><el-button @click="open=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">发布团队任务</el-button></template></el-dialog></template>
<style scoped>.team-task-publisher{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:16px}</style>
