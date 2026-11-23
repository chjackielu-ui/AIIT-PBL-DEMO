<script setup lang="ts">
import MarkdownText from './MarkdownText.vue'
import MarkdownInput from './MarkdownInput.vue'
import {computed, reactive, ref} from 'vue'
import {CalendarClock, ClipboardCheck, Flag, Plus} from 'lucide-vue-next'
import type {FormInstance, FormRules} from 'element-plus'
import type {CourseTask, DeliverableRequirement, Milestone, Project} from '../types'
import DeliverableEditor from './DeliverableEditor.vue'
import SubmissionChecklist from './SubmissionChecklist.vue'
import RubricEditor from './RubricEditor.vue'
import {useStore} from '../store'

import {confirmAction, localDate, run, stamp} from '../lib'
const props = defineProps<{task: CourseTask; project?: Project}>()
const store = useStore()
const opened = ref(false), busy = ref(false), changing = ref(false)
const expanded = ref(['published'])
const formRef = ref<FormInstance>()
const scheduled = computed(() => !!props.task.startAt)
const empty = (): Milestone & {deliverables:DeliverableRequirement[]} => ({id:'',parentId:'',name:'',description:'',due:scheduled.value ? props.task.due : localDate(30),materials:'',criteria:'',isFinal:false,requireMaterials:false,requiresSubmission:true,scoreWeight:1,rubric:[],scoringRule:'',submissionMode:'shared',deliverables:[]})
const form = reactive(empty())
const required = {required:true,message:'请填写此项',trigger:'blur'}
const rules: FormRules = {name:[required],description:[required],due:[required],criteria:[required]}
const published = computed(() => props.project ? props.project.milestones ?? [] : props.task.milestones)
const hasSubmissions = (id: string) => !!props.project?.submissions.some(sub => sub.milestoneId === id)
async function persist(milestones: Milestone[]) {
  if (props.project) await store.act('saveProjectPhases', {projectId: props.project.id, milestones, revision: props.project.milestoneRevision ?? 0})
  else await store.act('saveTask', {task: {...props.task, milestones}})
}
function open(m?: Milestone) {
  Object.assign(form, empty(), m ? JSON.parse(JSON.stringify(m)) : {})
  if (scheduled.value && form.due.length === 10) form.due += 'T23:59'
  changing.value = !!m
  opened.value = true
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  busy.value = true
  await run(async () => {
    const milestones = published.value.filter(m => m.id !== form.id)
    const entry={...form, submissionMode:form.isFinal?'shared' as const:form.submissionMode, requiresSubmission:form.isFinal || form.requiresSubmission}
    if (scheduled.value) Object.assign(entry, {requireMaterials:true, materials:'至少上传 1 个附件', criteria:form.description})
    const index=published.value.findIndex(m=>m.id===form.id)
    milestones.splice(index<0?milestones.length:index,0,entry)
    milestones.sort((a,b) => Number(!!a.isFinal)-Number(!!b.isFinal))
    await persist(milestones)
    opened.value = false
  }, changing.value ? '任务已修改' : '新任务已发布')
  busy.value = false
}
function remove(m: Milestone) {
  confirmAction(`删除“${m.name}”后将从任务列表移除，历史提交保留。`, async () => {
    const milestones = published.value.filter(x=>x.id!==m.id).map(x => x.parentId === m.id ? {...x,parentId:m.parentId} : x)
    await persist(milestones)
  }, '任务已删除，可随时发布新任务')
}
</script>
<template>
  <section class="stage-publisher">
    <div class="section-head stage-heading">
      <h2>{{project ? '本项目子任务' : '课程项目共用阶段'}}</h2>
      <el-button v-if="task.status !== '已关闭' || project" type="primary" @click="open()"><Plus :size="16" />发布子任务</el-button>
    </div>
    <el-collapse v-model="expanded" class="published-tasks">
      <el-collapse-item name="published">
        <template #title><strong>已发布</strong><span class="stage-count">{{published.length}}</span></template>
        <div v-if="published.length" class="stage-list">
          <article v-for="m in published" :key="m.id" class="stage-card" :class="{'stage-card-final':m.isFinal}">
            <span class="stage-icon" aria-hidden="true"><Flag v-if="m.isFinal" :size="17" /><ClipboardCheck v-else :size="17" /></span>
            <div class="stage-content">
              <div class="stage-title">
                <h3>{{m.name}}</h3>
                <el-tag v-if="m.isFinal" size="small" effect="plain">最终验收</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">{{m.requiresSubmission===false?'指导事项':'阶段验收'}}</el-tag>
              </div>
              <div class="stage-meta muted">
                <span><CalendarClock :size="14" aria-hidden="true" />截止 {{stamp(m.due)}}</span>
                <span v-if="m.parentId">上级阶段：{{published.find(node=>node.id===m.parentId)?.name}}</span>
              </div>
              <MarkdownText v-if="m.description" class=" stage-description" :text="m.description" />
            </div>
            <div v-if="(task.status !== '已关闭' || project) && (!m.isFinal || !scheduled)" class="stage-actions">
              <el-button size="small" @click="open(m)">修改任务</el-button>
              <el-button v-if="!m.isFinal && !hasSubmissions(m.id)" size="small" text type="danger" @click="remove(m)">删除任务</el-button>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无已发布的子任务" :image-size="48" />
      </el-collapse-item>
    </el-collapse>
  </section>
  <el-dialog v-model="opened" :title="changing?'修改任务':'发布任务'" width="min(680px,94vw)" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="任务名称" prop="name"><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <el-form-item v-if="!form.isFinal" label="上级阶段（可选）">
        <el-select v-model="form.parentId" clearable placeholder="直接归属大任务">
          <el-option v-for="node in published.filter(m => !m.isFinal && m.id !== form.id)" :key="node.id" :label="node.name" :value="node.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="任务说明" prop="description"><MarkdownInput v-model="form.description" :rows="3" /></el-form-item>
      <el-form-item label="截止时间" prop="due"><el-date-picker v-if="scheduled" v-model="form.due" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm" /><el-date-picker v-else v-model="form.due" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item v-if="!scheduled" label="需要提交并验收"><el-switch v-model="form.requiresSubmission" :disabled="form.isFinal || hasSubmissions(form.id)" /></el-form-item>
      <el-form-item v-if="form.requiresSubmission" label="成员提交方式"><el-radio-group v-model="form.submissionMode" :disabled="form.isFinal || hasSubmissions(form.id)"><el-radio-button value="shared">由负责人统一提交</el-radio-button><el-radio-button value="individual">成员分别提交</el-radio-button></el-radio-group></el-form-item>
      <template v-if="scheduled"><SubmissionChecklist v-if="form.requiresSubmission" v-model="form.deliverables" /></template>
      <template v-else>
        <DeliverableEditor v-if="form.requiresSubmission" v-model="form.deliverables" />
        <el-form-item label="要求附件"><el-switch v-model="form.requireMaterials" /></el-form-item>
        <el-form-item v-if="form.requireMaterials" label="材料要求" prop="materials" :rules="required"><el-input v-model="form.materials" /></el-form-item>
        <el-form-item label="验收标准" prop="criteria"><el-input v-model="form.criteria" /></el-form-item>
      </template>
      <el-form-item label="任务成绩权重"><el-input-number v-model="form.scoreWeight" :min="0.01" :max="100" :precision="2" /><small class="muted">各任务按权重占比汇总；全部为 1 时等权。</small></el-form-item><RubricEditor v-model="form.rubric" />
      <el-form-item label="特殊评分规则说明（可选）"><MarkdownInput v-model="form.scoringRule" placeholder="例如：按验收标准检查实现、测试和材料完整性" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="opened=false">取消</el-button><el-button type="primary" :loading="busy" @click="save">{{changing?'保存修改':'发布任务'}}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.stage-publisher {min-width:0; margin-bottom:20px}
.stage-heading {flex-wrap:wrap; gap:12px}
.stage-heading h2 {margin:0}
.published-tasks {overflow:hidden; border:1px solid var(--line); border-radius:var(--radius); --el-collapse-border-color:var(--line)}
.published-tasks :deep(.el-collapse-item__header) {padding-inline:16px; gap:10px; background:#f7f9fd}
.published-tasks :deep(.el-collapse-item__wrap) {border-bottom:0; background:rgb(255 255 255 / 64%)}
.published-tasks :deep(.el-collapse-item__content) {padding:12px}
.stage-count {display:inline-grid; place-items:center; min-width:22px; height:22px; padding:0 6px; border-radius:12px; color:var(--primary); background:#eaf0fc; line-height:1}
.stage-list {display:grid; gap:10px}
.stage-card {display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:start; gap:12px; padding:14px; border:1px solid var(--line); border-radius:8px; background:#fff}
.stage-card-final {background:#f5f8ff; border-color:#d9e3f8}
.stage-icon {display:grid; place-items:center; width:32px; height:32px; border-radius:8px; background:#eaf0fa; color:var(--primary)}
.stage-content {min-width:0; overflow-wrap:anywhere}
.stage-title,.stage-meta,.stage-actions {display:flex; align-items:center; flex-wrap:wrap; gap:8px}
.stage-title h3 {font-size:15px; margin:0}
.stage-meta {margin-top:5px; font-size:12px; column-gap:16px}
.stage-meta span {display:inline-flex; align-items:center; gap:5px}
.stage-description {margin:8px 0 0}
.stage-actions .el-button {margin-left:0}
@media(max-width:650px) {
  .stage-card {grid-template-columns:auto minmax(0,1fr); gap:8px 10px; padding:12px}
  .stage-actions {grid-column:2}
}
</style>

