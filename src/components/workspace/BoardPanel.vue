<script setup lang="ts">
import {computed} from 'vue'
import {useStore} from '../../store'
import type {Project, WorkItem} from '../../types'
import {run} from '../../lib'

const props = defineProps<{project: Project; readonly: boolean}>()
const store = useStore()
const userName = (id: string) => store.state?.users.find(u => u.id === id)?.name || id

const view=defineModel<string>('filter',{default:'待完成'})
const today=new Date().toLocaleDateString('sv-SE')
const isOverdue=(i:WorkItem)=>i.status!=='已完成' && !!i.due && i.due<today
const statusClass=(i:WorkItem)=>i.status==='已完成'?'completed':isOverdue(i)?'overdue':i.status==='待开始'?'pending':'active'
const statusLabel=(i:WorkItem)=>i.status==='已完成'?'已完成':isOverdue(i)?'已过期':i.status
const canChange=(i:WorkItem)=>!props.readonly && store.user?.role==='student' && i.ownerId===store.user?.id
const visible=computed(()=>props.project.items.filter(i=>view.value==='全部' || (view.value==='已完成'?i.status==='已完成':view.value==='已过期'?isOverdue(i):i.status!=='已完成')))
async function changeStatus(itemId: string, status: string) {
  await run(() => store.act('itemStatus', {projectId: props.project.id, itemId, status}), '任务状态已更新')
}
</script>
<template>
 <section class="section project-task-board">
  <div class="section-head"><h2>项目任务概览与提交</h2></div>
  <div class="toolbar"><el-radio-group v-model="view"><el-radio-button v-for="v in ['待完成','已完成','已过期','全部']" :key="v" :value="v">{{v}}</el-radio-button></el-radio-group></div>
  <el-table :data="visible" class="assignment-table"><el-table-column label="任务" min-width="200"><template #default="{row:i}"><strong>{{i.title}}</strong></template></el-table-column><el-table-column label="负责人" min-width="100"><template #default="{row:i}">{{userName(i.ownerId)}}</template></el-table-column><el-table-column prop="due" label="截止日期" width="125" /><el-table-column label="状态" width="115"><template #default="{row:i}"><span class="task-status-label" :class="statusClass(i)">{{statusLabel(i)}}</span></template></el-table-column><el-table-column label="操作" width="145" fixed="right" align="right"><template #default="{row:i}"><el-button v-if="canChange(i) && i.status==='待开始'" type="primary" plain @click="changeStatus(i.id,'进行中')">开始任务</el-button><el-button v-else-if="canChange(i) && ['进行中','待验收'].includes(i.status)" type="primary" @click="changeStatus(i.id,'已完成')">标记完成</el-button><span v-else class="muted">{{i.status==='已完成'?'已完成':i.status==='待验收'?'等待验收':'—'}}</span></template></el-table-column><template #empty>暂无符合条件的任务</template></el-table>
 </section>
</template>
