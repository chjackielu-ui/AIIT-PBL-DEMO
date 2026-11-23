<script setup lang="ts">
import MarkdownInput from '../../components/MarkdownInput.vue'
import {computed, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type {FormInstance, FormRules} from 'element-plus'
import {useStore} from '../../store'
import {confirmAction, localDate, run} from '../../lib'
import {classPath, taskPath, teachingClasses} from '../../teacher'
import {isIndividual} from '../../taskMode'
import type {CourseTask, DeliverableRequirement, Milestone} from '../../types'
import LibraryPicker from '../../components/LibraryPicker.vue'
import type {ProjectFile} from '../../types'
import PageHead from '../../components/PageHead.vue'
import Status from '../../components/Status.vue'
import TeacherContext from '../../components/TeacherContext.vue'
import SubmissionChecklist from '../../components/SubmissionChecklist.vue'

const store = useStore(), route = useRoute(), router = useRouter()
const tc = computed(() => teachingClasses(store.state!).find(c => c.id === route.params.classId))
const creating = route.params.id === undefined
const task = computed(() => store.state?.tasks.find(t => t.id === route.params.id && t.classId === tc.value?.id))
const course = computed(() => store.state?.courses.find(c => c.id === tc.value?.courseId))
const original = task.value
const toPicker = (value: string) => value.length === 10 ? value + 'T23:59' : value.replace(' ', 'T').slice(0, 16)
const defaultDue = localDate(14) + 'T23:59'
const previousDue = original?.milestones.find(m => m.isFinal || original.taskKind === 'exercise')?.due || original?.due
const defaults={name:'专业技术实践 '+localDate(),description:'按要求自主完成项目练习'}
const initialDefaults={
  name:original && /^专业技术实践 \d{4}-\d{2}-\d{2}$/.test(original.name)?original.name:defaults.name,
  description:defaults.description
}
const untouched={name:creating || original?.name===initialDefaults.name,description:creating || original?.description===defaults.description}
function clearDefault(key:'name'|'description'){
  if(untouched[key] && form[key]===initialDefaults[key]) form[key]=''
  untouched[key]=false
}
const roster=computed(()=>store.state?.enrollments.filter(e=>e.classId===tc.value?.id).length || 0)
const uploading=ref(false)
const references=ref<ProjectFile[]>((original?.references || []).map(f=>({...f})))
const form = reactive({
  kind: original?.taskKind ?? (original ? 'training' : 'exercise'),
  name: original?.name ?? defaults.name,
  min:original?.min ?? 1,max:original?.max ?? Math.max(1,Math.min(5,roster.value)),
  description: original?.description ?? defaults.description,
  startAt: toPicker(original?.startAt || ((original?.created?.slice(0,10) || localDate()) + 'T00:00')),
  due: previousDue ? toPicker(previousDue) : defaultDue,
})
const classTask = computed(() => form.kind === 'exercise')
const checklist = reactive<Record<typeof form.kind, DeliverableRequirement[]>>({
  exercise: original?.taskKind === 'exercise' ? structuredChecklist(original.milestones[0]) : [],
  training: original?.taskKind === 'exercise' ? [] : structuredChecklist(original?.milestones.find(m => m.isFinal)),
})
const hasResponses = computed(() => !!original && store.state?.projects.some(p => p.taskId === original.id))
const legacyIndividual = computed(() => hasResponses.value && !classTask.value && isIndividual(original))
const currentStatus = computed(() => task.value?.status || '草稿')
const formRef = ref<FormInstance>()
const busy = ref(false)
const required = {required: true, message: '请填写此项', trigger: 'blur'}
const rules: FormRules = {name: [required], description: [required], startAt: [required], due: [required]}

function structuredChecklist(phase?: Milestone) {
  return phase?.deliverables?.map(row => ({...row, extensions:[...row.extensions]})) ?? []
}

function milestones(): Milestone[] {
  const sameKind = original && (original.taskKind || 'training') === form.kind
  const old = sameKind ? original.milestones : []
  const existing = classTask.value ? old[0] : old.find(m => m.isFinal)
  const phase: Milestone = {
    id: existing?.id || '',
    name: classTask.value ? '任务作答' : '最终成果验收',
    description: form.description.trim() || '按任务说明完成并提交附件',
    criteria: '按任务说明验收',
    due: form.due,
    materials: '至少上传 1 个附件',
    isFinal: !classTask.value,
    requireMaterials: true,
    requiresSubmission: true,
    submissionMode: 'shared',
    deliverables: checklist[form.kind],
  }
  return classTask.value ? [phase] : [...old.filter(m => !m.isFinal), phase]
}

async function save(status: '草稿' | '已发布' | '已关闭') {
  if (!tc.value || busy.value || uploading.value) return
  const valid = status === '草稿'
    ? await formRef.value?.validateField(['name','startAt','due']).catch(() => false)
    : await formRef.value?.validate().catch(() => false)
  if (!valid) return
  busy.value = true
  await run(async () => {
    if (form.startAt >= form.due) throw Error('结束时间必须晚于开始时间')
    const group = !classTask.value && !legacyIndividual.value
    if(group && status!=='已关闭' && (!Number.isInteger(form.min) || !Number.isInteger(form.max) || form.min<1 || form.min>form.max || form.max>roster.value))
      throw Error(roster.value?'请将小组人数范围设在 1 至 '+roster.value+' 人之间':'请先将学生加入教学班，再发布课程项目')
    const model: CourseTask = {
      id: original?.id || '', taskKind: form.kind, name: form.name.trim(), description: form.description.trim(),
      courseId: tc.value!.courseId, classId: tc.value!.id, teacherId: store.user!.id,
      status, startAt: form.startAt, due: form.due, teamDue: form.due,
      projectType: group ? 'group' : 'individual',
      references:references.value,
      min: group ? form.min : 1,
      max: group ? form.max : 1,
      crossClass: group && !!original?.crossClass,
      milestones: milestones(),
    }
    const result = await store.act('saveTask', {task: model})
    await router.push(status === '草稿' ? classPath(tc.value!.id) : taskPath(tc.value!.id, result.id))
  }, {草稿:'已暂存草稿',已发布:'已发布',已关闭:'任务已关闭'}[status])
  busy.value = false
}
</script>

<template>
  <TeacherContext v-if="tc" label="发布教学任务" />
  <template v-if="tc && (creating || task) && currentStatus !== '已关闭'">
    <div class="publish-page">
      <PageHead :title="creating ? '发布教学任务' : '修改发布内容'" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="section publish-form" @submit.prevent>
        <div class="section-head publication-status">
          <div class="actions"><strong>任务状态</strong><Status :value="currentStatus" /></div>
          <el-button v-if="currentStatus === '已发布'" text @click="confirmAction('关闭后学生无法新开始作答或申请项目。', () => save('已关闭'), '')">关闭任务</el-button>
        </div>
        <el-form-item label="发布类型">
          <el-radio-group v-model="form.kind" :disabled="hasResponses">
            <el-radio-button value="exercise">班级任务</el-radio-button>
            <el-radio-button value="training">课程项目</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="classTask ? '任务名称' : '项目名称'" prop="name"><el-input v-model="form.name" @focus="clearDefault('name')" maxlength="100" /></el-form-item>
        <div class="publish-fields">
          <el-form-item label="所属课程"><el-input :model-value="course?.name || ''" readonly /></el-form-item>
          <el-form-item label="教学班"><el-input :model-value="tc.name" readonly /></el-form-item>
          <el-form-item label="开始时间" prop="startAt"><el-date-picker v-model="form.startAt" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm" /></el-form-item>
          <el-form-item :label="classTask ? '结束时间' : '截止时间'" prop="due"><el-date-picker v-model="form.due" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm" /></el-form-item>
        </div>
        <el-form-item label="任务说明" prop="description"><MarkdownInput v-model="form.description" @focus="clearDefault('description')" :rows="6" /></el-form-item>
        <div v-if="!classTask && !legacyIndividual" class="publish-fields">
          <el-form-item label="每组最少人数"><el-input-number v-model="form.min" :min="1" :max="form.max" :step-strictly="true" /></el-form-item>
          <el-form-item :label="'每组最多人数（班级共 '+roster+' 人）'"><el-input-number v-model="form.max" :min="form.min" :max="roster" :step-strictly="true" /></el-form-item>
        </div>
        <el-form-item label="任务参考资料"><LibraryPicker v-model="references" v-model:uploading="uploading" upload permissions /></el-form-item>
        <SubmissionChecklist :key="form.kind" v-model="checklist[form.kind]" />
        <p v-if="!classTask" class="muted">{{legacyIndividual ? '此项目已有个人申请。' : '学生以小组申请，最终成果验收与项目使用同一截止时间。'}}</p>
      </el-form>
      <div class="review-decision-bar publish-actions">
        <el-button :disabled="busy" @click="router.push(classPath(tc.id))">取消</el-button>
        <el-button type="primary" plain :disabled="busy || uploading || currentStatus === '已发布'" @click="save('草稿')">暂存</el-button>
        <el-button type="primary" :loading="busy" :disabled="uploading" @click="save('已发布')">{{classTask ? '发布任务' : '发布项目'}}</el-button>
      </div>
    </div>
  </template>
  <el-result v-else :title="currentStatus === '已关闭' ? '任务已关闭' : '任务不存在或无权访问'">
    <template #extra><el-button @click="router.push(tc ? classPath(tc.id) : '/teacher/classes')">返回教学班级</el-button></template>
  </el-result>
</template>

<style scoped>
.publish-page {padding-bottom:84px}
.publication-status {padding-bottom:14px; border-bottom:1px solid var(--line)}
.publish-fields {display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:0 18px}
.publish-fields :deep(.el-date-editor) {width:100%}
.publish-actions .el-button {margin-left:0}
@media (max-width:650px) {.publish-fields {grid-template-columns:minmax(0,1fr)}}
</style>
