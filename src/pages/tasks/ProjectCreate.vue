<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type {FormInstance, FormRules} from 'element-plus'
import {useStore} from '../../store'
import {isIndividual,taskModeName} from '../../taskMode'
import {run, localDate} from '../../lib'
import PageHead from '../../components/PageHead.vue'

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
  const today = localDate()
  return today > task.value.teamDue || today > task.value.due
})

const projectForm = reactive({name: isIndividual(task.value)?`${task.value!.name} · ${store.user!.name}`:'', description: isIndividual(task.value)?task.value!.description:'', goal: isIndividual(task.value)?task.value!.description:'', tags: 'Java / Vue', memberIds: [store.user!.id]})
const required = {required: true, message: '请填写此项', trigger: 'blur'}
const projectRules: FormRules = {name: [required], description: [required], goal: [required]}
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
  if (!await projectRef.value?.validate().catch(() => false)) return
  busy.value = true
  await run(async () => {
    const result = await store.act('createProject', {taskId: task.value!.id, ...projectForm})
    await router.push('/student/projects/' + result.id)
  }, isIndividual(task.value)?'个人项目已创建，可独立提交材料':'申请已提交，成员名单已锁定')
  busy.value = false
}
</script>
<template>
  <template v-if="task">
    <PageHead title="创建项目">
      <el-button @click="router.back()">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="!!alreadyJoined || deadlinePassed" @click="createProject">{{isIndividual(task)?'创建个人项目':'提交小组项目申请'}}</el-button>
    </PageHead>
    <div class="info-strip">关联任务：{{task.name}} · {{taskModeName(task)}} · {{isIndividual(task)?'本人独立提交':task.min+'–'+task.max+' 人，每组提交一份'}} · {{task.crossClass ? '允许跨班组队' : '仅限本教学班'}} · 组队截止 {{task.teamDue}}</div>
    <el-alert v-if="alreadyJoined" title="你已加入该课程任务的一个项目，不能重复组队。" type="warning" :closable="false">
      <el-button type="primary" @click="router.push(`/student/projects/${alreadyJoined.id}`)">进入我的项目</el-button>
    </el-alert>
    <el-alert v-else-if="deadlinePassed" title="组队或申请截止时间已过，无法提交申请。" type="warning" :closable="false" />
    <el-form ref="projectRef" :model="projectForm" :rules="projectRules" label-position="top" class="form-page">
      <el-form-item label="项目名称" prop="name"><el-input v-model="projectForm.name" maxlength="100" /></el-form-item>
      <el-form-item label="项目简介" prop="description"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="项目目标" prop="goal"><el-input v-model="projectForm.goal" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="技术 / 主题标签"><el-input v-model="projectForm.tags" /></el-form-item>
      <el-form-item v-if="!isIndividual(task)" label="项目成员（包含当前负责人）" required>
        <el-select v-model="projectForm.memberIds" multiple filterable :disabled="!!alreadyJoined || deadlinePassed">
          <el-option v-for="u in available" :key="u.id" :value="u.id" :disabled="u.id === store.user?.id" :label="`${u.name} · ${className(u.classId || '')}`" />
        </el-select>
      </el-form-item>
      <p v-if="!isIndividual(task)" class="muted">负责人：{{store.user?.name}}。提交后成员自动加入，名单只能由教师调整。</p>
    </el-form>
  </template>
  <el-result v-else title="任务不存在或无权访问">
    <template #extra><el-button @click="router.push('/student/tasks')">返回任务列表</el-button></template>
  </el-result>
</template>
