<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import {UserPlus, Users} from 'lucide-vue-next'
import {useStore} from '../store'
import {confirmAction, run} from '../lib'
import type {User} from '../types'
import ClassEnrollment from './ClassEnrollment.vue'

const props = defineProps<{classId: string}>()
const store = useStore()
const query = ref('')
const adding = ref(false), importing = ref(false), resetting = ref<User>(), busy = ref(false)
const password = ref('')
const empty = () => ({name:'', number:'', administrativeClassId:'', contact:'', password:''})
const student = reactive(empty())
const manageable = computed(() => !store.user?.permissions || store.user.permissions.includes('roster'))
const classNames = computed(() => new Map(store.state?.classes.map(c => [c.id,c.name])))
const roster = computed(() => {
  const ids = new Set(store.state?.enrollments.filter(e => e.classId === props.classId).map(e => e.studentId))
  return (store.state?.users ?? []).filter(u => ids.has(u.id) && u.role === 'student')
    .sort((a,b) => (a.number ?? '').localeCompare(b.number ?? ''))
})
const visible = computed(() => roster.value.filter(u =>
  [u.name, u.number, classNames.value.get(u.classId ?? ''), u.contact].some(v => v?.includes(query.value.trim()))))

async function add() {
  busy.value = true
  await run(async () => {
    await store.act('addClassStudent', {classId:props.classId,...student})
    adding.value = false
  }, '学生已加入本班')
  busy.value = false
}
function remove(user: User) {
  confirmAction(`将${user.name}移出本教学班？学生账号和历史提交仍会保留。`,
    () => store.act('removeClassStudent', {classId:props.classId,studentId:user.id}), '已移出教学班')
}
async function reset() {
  if (!resetting.value) return
  busy.value = true
  await run(async () => {
    await store.act('resetClassStudentPassword', {classId:props.classId,studentId:resetting.value!.id,password:password.value})
    resetting.value = undefined
  }, '密码已重置，原有登录已失效')
  busy.value = false
}
</script>

<template>
  <section class="section">
    <div class="section-head">
      <h2>班级学生</h2>
      <div v-if="manageable" class="actions wrap">
        <el-button @click="importing=true"><Users :size="16" />扫码或批量导入</el-button>
        <el-button type="primary" @click="Object.assign(student,empty());adding=true"><UserPlus :size="16" />添加学生</el-button>
      </div>
    </div>
    <div class="toolbar"><el-input v-model="query" clearable placeholder="搜索姓名、学号、行政班或联系方式" /><span class="muted">本班共 {{roster.length}} 人</span></div>
    <el-table :data="visible">
      <el-table-column type="index" label="序号" width="64" />
      <el-table-column prop="name" label="姓名" min-width="110" />
      <el-table-column prop="number" label="学号" min-width="140" />
      <el-table-column label="行政班" min-width="130"><template #default="{row}">{{classNames.get(row.classId) || '—'}}</template></el-table-column>
      <el-table-column label="联系方式" min-width="145"><template #default="{row}">{{row.contact || '—'}}</template></el-table-column>
      <el-table-column label="账号状态" width="105"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template></el-table-column>
      <el-table-column v-if="manageable" label="操作" min-width="178"><template #default="{row}">
        <el-button text type="primary" @click="password='';resetting=row">重置密码</el-button>
        <el-button text type="danger" @click="remove(row)">移出班级</el-button>
      </template></el-table-column>
      <template #empty>没有符合条件的学生</template>
    </el-table>
  </section>
  <ClassEnrollment v-if="manageable" v-model="importing" :class-id="classId" />
  <el-dialog v-model="adding" title="添加学生" width="min(510px,94vw)" :close-on-click-modal="!busy">
    <el-form label-position="top" @submit.prevent="add">
      <div class="form-grid">
        <el-form-item label="姓名" required><el-input v-model.trim="student.name" maxlength="60" /></el-form-item>
        <el-form-item label="学号" required><el-input v-model.trim="student.number" maxlength="10" inputmode="numeric" /></el-form-item>
      </div>
      <p class="muted">已有账号按姓名和学号加入班级。以下信息用于新建账号。</p>
      <el-form-item label="行政班"><el-select v-model="student.administrativeClassId" clearable filterable><el-option v-for="c in store.state?.classes" :key="c.id" :value="c.id" :label="c.name" /></el-select></el-form-item>
      <el-form-item label="联系方式"><el-input v-model.trim="student.contact" maxlength="100" /></el-form-item>
      <el-form-item label="初始密码"><el-input v-model="student.password" type="password" show-password maxlength="128" autocomplete="new-password" placeholder="至少 8 位，可入班后再设置" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="adding=false">取消</el-button><el-button type="primary" :loading="busy" :disabled="!student.name || !/^[0-9]{10}$/.test(student.number)" @click="add">添加学生</el-button></template>
  </el-dialog>
  <el-dialog :model-value="!!resetting" :title="'重置密码 · ' + (resetting?.name || '')" width="min(450px,94vw)" @close="resetting=undefined;password=''">
    <el-form label-position="top" @submit.prevent="reset">
      <el-form-item label="新密码" required><el-input v-model="password" type="password" show-password maxlength="128" autocomplete="new-password" placeholder="请输入至少 8 位密码" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="resetting=undefined">取消</el-button><el-button type="primary" :loading="busy" :disabled="password.length<8" @click="reset">确认重置</el-button></template>
  </el-dialog>
</template>
