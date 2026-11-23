<script setup lang="ts">
import MarkdownInput from '../../components/MarkdownInput.vue'
import {computed, onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type {FormInstance, FormRules} from 'element-plus'
import {useStore} from '../../store'
import {isIndividual,taskModeName,canStartTask,taskStarted} from '../../taskMode'
import {run, stamp} from '../../lib'
import PageHead from '../../components/PageHead.vue'
import ProjectTopics from '../../components/ProjectTopics.vue'
import {validOrigin} from '../../navigation'

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)
const task = computed(() => s.value.tasks.find(t => t.id === route.params.taskId))
onMounted(()=>{if(task.value?.taskKind==='exercise')router.replace({path:'/student/tasks/'+task.value.id,query:route.query})})

const alreadyJoined = computed(() =>
  s.value.projects.find(p => p.taskId === task.value?.id && p.memberIds.includes(store.user!.id)))

/** 组队/申请截止已过则不允许提交（与服务端校验一致，提前拦截）。 */
const deadlinePassed = computed(() => {
  if (!task.value) return false
  return !canStartTask(task.value)
})

const projectForm = reactive({name:'', description:'', tags: '', memberIds: [store.user!.id]})
const required = {required: true, message: '请填写此项', trigger: 'blur'}
const projectRules: FormRules = {name: [required], description: [required]}
const projectRef = ref<FormInstance>()
const busy = ref(false)

/** 可选成员：启用学生；跨班任务不限班级；已在同任务其他项目中的成员不可再选。 */
const available = computed(() =>
  s.value.users.filter(u =>
    u.role === 'student' && u.enabled
    && (task.value?.crossClass || s.value.enrollments.some(e => e.studentId === u.id && e.classId === task.value?.classId))
    && !s.value.projects.some(p => p.taskId === task.value?.id && p.memberIds.includes(u.id))))

const className = (id: string) => s.value.classes.find(c => c.id === id)?.name || id

async function createProject() {
  if (busy.value || alreadyJoined.value || deadlinePassed.value) return
  if (!await projectRef.value?.validate().catch(() => false)) return
  busy.value = true
  await run(async () => {
    const result = await store.act('createProject', {taskId: task.value!.id, ...projectForm})
    await router.push('/student/projects/' + result.id)
  }, '申请已提交，等待教师审核')
  busy.value = false
}
</script>
<template>
  <template v-if="task && task.taskKind !== 'exercise'">
    <PageHead title="创建项目">
    </PageHead>
    <div class="info-strip">关联任务：{{task.name}} · {{taskModeName(task)}} · {{isIndividual(task)?'本人独立提交':task.min+'–'+task.max+' 人，每组提交一份'}} · {{task.crossClass ? '允许跨班组队' : '仅限本教学班'}} · 截止 {{stamp(task.due)}}</div>
    <el-alert v-if="alreadyJoined" title="你已加入该课程任务的一个项目，不能重复组队。" type="warning" :closable="false">
      <el-button type="primary" @click="router.push(`/student/projects/${alreadyJoined.id}`)">进入我的项目</el-button>
    </el-alert>
    <el-alert v-else-if="deadlinePassed" :title="!taskStarted(task) ? '课程项目尚未开始' : '课程项目已截止，无法提交申请'" type="warning" :closable="false" />
    <el-form ref="projectRef" :model="projectForm" :rules="projectRules" label-position="top" class="section project-application" @submit.prevent="createProject">
      <el-form-item label="项目名称" prop="name"><el-input v-model="projectForm.name" maxlength="100" /></el-form-item>
      <el-form-item label="项目简介" prop="description"><MarkdownInput v-model="projectForm.description" :rows="5" /></el-form-item>
      <div class="application-fields">
      <el-form-item label="技术与主题"><ProjectTopics v-model="projectForm.tags" /></el-form-item>
      <el-form-item v-if="!isIndividual(task)" label="项目成员（包含当前负责人）" required>
        <el-select v-model="projectForm.memberIds" multiple filterable :multiple-limit="task.max" :disabled="!!alreadyJoined || deadlinePassed">
          <el-option v-for="u in available" :key="u.id" :value="u.id" :disabled="u.id === store.user?.id" :label="`${u.name} · ${className(u.classId || '')}`" />
        </el-select>
      </el-form-item>
      </div>
      <p v-if="!isIndividual(task)" class="muted">负责人：{{store.user?.name}}。可选 {{task.min}}–{{task.max}} 人（含负责人）。</p>
      <div class="actions application-actions">
        <el-button @click="router.push(validOrigin(route.query.origin,'student') || '/student/tasks/' + task.id)">取消</el-button>
        <el-button type="primary" native-type="submit" :loading="busy" :disabled="!!alreadyJoined || deadlinePassed || projectForm.memberIds.length<task.min || projectForm.memberIds.length>task.max">提交项目申请</el-button>
      </div>
    </el-form>
  </template>
  <el-result v-else title="任务不存在或无权访问">
    <template #extra><el-button @click="router.push('/student/tasks')">返回任务列表</el-button></template>
  </el-result>
</template>

<style scoped>
.application-fields {display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}
.application-actions {justify-content:flex-end;border-top:1px solid var(--line);padding-top:16px}
.project-application :deep(.el-select) {width:100%}
@media(max-width:760px){.application-fields{grid-template-columns:minmax(0,1fr)}}
</style>
