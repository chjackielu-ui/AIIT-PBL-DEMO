<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import type {FormInstance, FormRules} from 'element-plus'
import type {CourseTask, Milestone} from '../types'
import DeliverableEditor from './DeliverableEditor.vue'
import RubricEditor from './RubricEditor.vue'
import {useStore} from '../store'

import {confirmAction, localDate, run} from '../lib'
const props = defineProps<{task: CourseTask}>()
const store = useStore()
const opened = ref(false), busy = ref(false), changing = ref(false)
const formRef = ref<FormInstance>()
const empty = (): Milestone => ({id:'',parentId:'',name:'',description:'',due:localDate(30),materials:'',criteria:'',isFinal:false,requireMaterials:false,requiresSubmission:true,scoreWeight:1,rubric:[],scoringRule:'',submissionMode:'shared',deliverables:[]})
const form = reactive(empty())
const required = {required:true,message:'请填写此项',trigger:'blur'}
const rules: FormRules = {name:[required],description:[required],due:[required],criteria:[required]}
const hasFinal = computed(() => props.task.milestones.some(m => m.isFinal && m.id !== form.id))
function open(m?: Milestone) {
  Object.assign(form, empty(), m ? JSON.parse(JSON.stringify(m)) : {})
  changing.value = !!m
  opened.value = true
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  busy.value = true
  await run(async () => {
    const milestones = props.task.milestones.filter(m => m.id !== form.id)
    const entry={...form, submissionMode:form.isFinal?'shared' as const:form.submissionMode, requiresSubmission:form.isFinal || form.requiresSubmission}
    const index=props.task.milestones.findIndex(m=>m.id===form.id)
    milestones.splice(index<0?milestones.length:index,0,entry)
    milestones.sort((a,b) => Number(!!a.isFinal)-Number(!!b.isFinal))
    await store.act('saveTask', {task:{...props.task,milestones}})
    opened.value = false
  }, changing.value ? '任务已修改' : '新任务已发布')
  busy.value = false
}
function remove(m: Milestone) {
  confirmAction(`删除“${m.name}”后将从任务列表移除，历史提交保留。`, async () => {
    const milestones = props.task.milestones.filter(x=>x.id!==m.id).map(x => x.parentId === m.id ? {...x,parentId:m.parentId} : x)
    await store.act('saveTask', {task:{...props.task,milestones}})
  }, '任务已删除，可随时发布新任务')
}
</script>
<template>
  <div class="section-head"><span class="muted">可随时发布阶段任务或项目验收任务，默认由教师按 100 分制评分。</span><el-button type="primary" @click="open()">发布任务</el-button></div>
  <el-collapse class="published-tasks"><el-collapse-item name="published" :title="`已发布的任务（${task.milestones.length}）`">
    <div v-for="m in task.milestones" :key="m.id" class="published-task-row">
      <div><strong>{{m.name}}</strong><p class="muted">{{m.isFinal?'项目验收任务':'阶段任务'}} · 截止 {{m.due}} · 教师评分</p><p v-if="m.parentId" class="muted">上级阶段 {{task.milestones.find(node => node.id === m.parentId)?.name}}</p><p>{{m.description}}</p><small>{{m.scoringRule || '默认评分规则：教师按 100 分制评分'}}</small></div>
      <div class="vertical-actions"><el-button @click="open(m)">修改任务</el-button><el-button type="danger" plain @click="remove(m)">删除任务</el-button></div>
    </div><el-empty v-if="!task.milestones.length" description="暂无已发布任务，可随时发布" :image-size="48" />
  </el-collapse-item></el-collapse>
  <el-dialog v-model="opened" :title="changing?'修改任务':'发布任务'" width="min(680px,94vw)" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="任务类别"><el-radio-group v-model="form.isFinal"><el-radio-button :value="false">阶段任务</el-radio-button><el-radio-button :value="true" :disabled="hasFinal">项目验收任务</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="任务名称" prop="name"><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <el-form-item v-if="!form.isFinal" label="上级阶段（可选）">
        <el-select v-model="form.parentId" clearable placeholder="直接归属大任务">
          <el-option v-for="node in task.milestones.filter(m => !m.isFinal && m.id !== form.id)" :key="node.id" :label="node.name" :value="node.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="任务说明" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="截止日期" prop="due"><el-date-picker v-model="form.due" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="成员提交方式"><el-radio-group v-model="form.submissionMode" :disabled="form.isFinal"><el-radio-button value="shared">整组提交 · 由组长提交</el-radio-button><el-radio-button value="individual">成员分别提交 · 可交不同材料</el-radio-button></el-radio-group><small v-if="form.isFinal" class="muted">项目验收由组长统一提交团队最终材料。</small></el-form-item><DeliverableEditor v-model="form.deliverables" /><el-form-item label="需要提交"><el-switch v-model="form.requiresSubmission" :disabled="form.isFinal" /></el-form-item>
      <el-form-item label="要求附件"><el-switch v-model="form.requireMaterials" /></el-form-item>
      <el-form-item v-if="form.requireMaterials" label="材料要求" prop="materials" :rules="required"><el-input v-model="form.materials" /></el-form-item>
      <el-form-item label="验收标准" prop="criteria"><el-input v-model="form.criteria" /></el-form-item>
      <el-form-item label="任务成绩权重"><el-input-number v-model="form.scoreWeight" :min="0.01" :max="100" :precision="2" /><small class="muted">各任务按权重占比汇总；全部为 1 时等权。</small></el-form-item><RubricEditor v-model="form.rubric" />
      <el-form-item label="特殊评分规则说明（可选）"><el-input v-model="form.scoringRule" type="textarea" placeholder="例如：按验收标准检查实现、测试和材料完整性" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="opened=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">{{changing?'保存修改':'发布任务'}}</el-button></template>
  </el-dialog>
</template>

