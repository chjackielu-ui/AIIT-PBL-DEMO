<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type {FormInstance, FormRules} from 'element-plus'
import {Plus, Trash2} from 'lucide-vue-next'
import {useStore} from '../../store'
import {run, localDate} from '../../lib'
import {classPath, taskPath, teachingClasses as ownClasses} from '../../teacher'
import {isIndividual} from '../../taskMode'
import type {CourseTask} from '../../types'
import DeliverableEditor from '../../components/DeliverableEditor.vue'
import RubricEditor from '../../components/RubricEditor.vue'
import PageHead from '../../components/PageHead.vue'
import TeacherContext from '../../components/TeacherContext.vue'

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)
const tc = computed(() => ownClasses(s.value).find(c => c.id === route.params.classId))
const creating = computed(() => route.path.endsWith('/tasks/create'))
const task = computed(() => creating.value
  ? undefined
  : s.value.tasks.find(t => t.id === route.params.id && t.classId === tc.value?.id))

/** 新建表单基线：课程与教学班由班级上下文锁定。 */
const base: CourseTask = {
  id: '', name: '', teacherId: store.user!.id, courseId: tc.value?.courseId || '', classId: tc.value?.id || '',
  projectType:'group', description: '', crossClass: false, min: 2, max: 5,
  teamDue: localDate(14), due: localDate(16), status: '已发布',
  milestones: [{
    id: '', name: '最终项目验收', isFinal: true, requireMaterials: true,
    description: '提交项目成果、团队总结及成员贡献比例',
    due: localDate(45), materials: '项目成果材料', criteria: '完成任务目标，成果完整可用',
  }],
}

const form = reactive<CourseTask>(JSON.parse(JSON.stringify(task.value || base)))
form.taskKind ??= 'training'
form.projectType ??= isIndividual(task.value)?'individual':'group'
const hasProjects=computed(()=>s.value.projects.some(p=>p.taskId===task.value?.id))
watch(()=>form.taskKind,value=>{
  if(value==='exercise'){
    form.projectType='individual'
    form.milestones=[{id:'',name:form.name || '练习提交',description:form.description || '按任务要求完成练习',due:localDate(30),criteria:'达到练习目标',materials:'',requiresSubmission:true,requireMaterials:false,submissionMode:'shared',deliverables:[],rubric:[],scoreWeight:1}]
  } else if(!hasProjects.value)form.milestones=[]
})
watch(()=>form.projectType,value=>{if(value==='individual'){form.min=1;form.max=1;form.crossClass=false}else{form.min=2;form.max=5}})
form.milestones.forEach(m => { m.requireMaterials ??= true; m.requiresSubmission ??= true; m.deliverables ??= []; m.submissionMode ??= 'shared';m.scoreWeight ??= 1;m.rubric ??= [];if(m.isFinal){m.submissionMode='shared';m.requiresSubmission=true} })

function addPhase() {
  form.milestones.splice(form.milestones.findIndex(m=>m.isFinal) < 0 ? form.milestones.length : form.milestones.findIndex(m=>m.isFinal), 0, {
    id: '', name: '', description: '', due: localDate(30), materials: '', criteria: '', requireMaterials: false, requiresSubmission: true, isFinal: false,scoreWeight:1,rubric:[],deliverables:[],submissionMode:'shared',
  })
}

function removePhase(index: number) {
  const [removed] = form.milestones.splice(index, 1)
  if (!removed.id) return
  for (const node of form.milestones) {
    if (node.parentId === removed.id) node.parentId = removed.parentId
  }
}

/** 保存按钮文案随“原状态→新状态”组合变化。 */
const saveLabel = computed(() => {
  if (form.status === '草稿') return creating.value ? '保存草稿' : '保存任务'
  return form.status === '已发布' ? '发布任务' : '保存任务'
})

const classOptions = computed(() =>
  s.value.teachingClasses.filter(c => c.teacherIds.includes(store.user!.id) && c.courseId === form.courseId))

const required = {required: true, message: '请填写此项', trigger: 'blur'}
const taskRules: FormRules = {name: [required], description: [required], due: [required], teamDue: [required]}
const formRef = ref<FormInstance>()
const busy = ref(false)

async function saveTask() {
  if (!await formRef.value?.validate().catch(() => false)) return
  busy.value = true
  await run(async () => {
    const result = await store.act('saveTask', {task: form})
    await router.push(taskPath(tc.value!.id, result.id))
  }, '课程任务已保存')
  busy.value = false
}
</script>
<template>
  <TeacherContext v-if="tc" label="课程任务" />
  <el-result v-if="task && task.status === '已关闭'" title="任务已关闭">
    <template #extra><el-button @click="router.push(taskPath(task.classId, task.id))">返回任务详情</el-button></template>
  </el-result>
  <template v-else-if="creating || task">
    <PageHead :title="creating ? '发布实训任务' : '修改实训任务'">
      <el-button @click="router.push(classPath(String(route.params.classId)))">取消</el-button>
      <el-button type="primary" :loading="busy" @click="saveTask">{{saveLabel}}</el-button>
    </PageHead>
    <el-form ref="formRef" :model="form" :rules="taskRules" label-position="top" class="form-page">
      <h2>基本信息</h2>
      <el-form-item label="课程任务形式"><el-radio-group v-model="form.taskKind" :disabled="hasProjects"><el-radio-button value="training">实训 · 可持续发布子任务</el-radio-button><el-radio-button value="exercise">小练习 · 学生直接提交</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="任务名称" prop="name"><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <div class="form-grid">
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="form.courseId" disabled>
            <el-option v-for="c in s.courses" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学班" prop="classId">
          <el-select v-model="form.classId" disabled>
            <el-option v-for="c in classOptions" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="任务说明" prop="description"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="任务状态">
        <el-radio-group v-model="form.status">
          <el-radio-button value="草稿">草稿</el-radio-button>
          <el-radio-button value="已发布">已发布</el-radio-button>
          <el-radio-button value="已关闭">已关闭</el-radio-button>
        </el-radio-group>
        <p class="muted">发布后全班选课学生将收到通知；草稿仅自己可见，不发送通知。</p>
      </el-form-item>
      <el-form-item label="项目类型"><el-radio-group v-model="form.projectType" :disabled="hasProjects || form.taskKind==='exercise'"><el-radio-button value="individual">个人项目 · 每人独立提交</el-radio-button><el-radio-button value="group">小组集体项目 · 每组提交一份</el-radio-button></el-radio-group></el-form-item>
      <h2>{{form.projectType==='individual'?'个人项目截止时间':'组队规则'}}</h2>
      <div class="form-grid">
        <el-form-item v-if="form.projectType!=='individual'" label="组队人数（含负责人）">
          <div class="actions team-size-inputs">
            <el-input-number v-model="form.min" :min="1" :max="20" />
            <span>至</span>
            <el-input-number v-model="form.max" :min="form.min" :max="20" />
          </div>
        </el-form-item>
        <el-form-item v-if="form.projectType!=='individual'" label="跨班组队">
          <el-switch v-model="form.crossClass" active-text="允许跨教学班选择队友" />
        </el-form-item>
        <el-form-item :label="form.projectType==='individual'?'项目创建截止':'组队截止时间'" prop="teamDue">
          <el-date-picker v-model="form.teamDue" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="申请截止时间" prop="due">
          <el-date-picker v-model="form.due" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </div>
      <div class="section-head">
        <h2>阶段与验收要求</h2>
        <el-button v-if="form.taskKind!=='exercise'" @click="addPhase"><Plus :size="16" />添加阶段</el-button>
      </div>
      <div v-for="(m, i) in form.milestones" :key="i" class="phase-form">
        <div class="section-head">
          <strong>{{m.isFinal ? '项目验收任务' : `阶段 ${i + 1}`}}</strong>
          <el-button v-if="form.taskKind!=='exercise'" @click="removePhase(i)"><Trash2 :size="16" />删除任务</el-button>
        </div>
        <div class="form-grid">
          <el-form-item label="阶段名称" :prop="`milestones.${i}.name`" :rules="required"><el-input v-model="m.name" /></el-form-item>
          <el-form-item label="截止时间" :prop="`milestones.${i}.due`" :rules="required"><el-date-picker v-model="m.due" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        </div>
        <el-form-item label="阶段说明" :prop="`milestones.${i}.description`" :rules="required"><el-input v-model="m.description" /></el-form-item>
        <DeliverableEditor v-model="m.deliverables" /><el-form-item label="任务成绩权重"><el-input-number v-model="m.scoreWeight" :min="0.01" :max="100" :precision="2" /></el-form-item><RubricEditor v-model="m.rubric" /><el-form-item label="成员提交方式"><el-select v-model="m.submissionMode" :disabled="m.isFinal"><el-option value="shared" label="整组提交 · 由组长提交" /><el-option value="individual" label="成员分别提交" /></el-select></el-form-item><el-form-item label="阶段任务提交"><el-switch v-model="m.requiresSubmission" :disabled="m.isFinal" active-text="需要提交" inactive-text="无需提交" /></el-form-item>
          <el-form-item label="要求提交附件材料">
          <el-switch v-model="m.requireMaterials" active-text="需要附件" inactive-text="仅填写说明" />
        </el-form-item>
        <el-form-item v-if="m.requireMaterials" label="提交材料要求"><el-input v-model="m.materials" placeholder="填写需要提交的材料说明" /></el-form-item>
        <el-form-item label="验收标准" :prop="`milestones.${i}.criteria`" :rules="required"><el-input v-model="m.criteria" /></el-form-item>
      </div>
    </el-form>
  </template>
  <el-result v-else title="任务不存在或无权访问">
    <template #extra><el-button @click="router.push('/teacher/classes')">返回教学班级</el-button></template>
  </el-result>
</template>


