<script setup lang="ts">
import {computed, ref} from 'vue'
import {Search, Upload} from 'lucide-vue-next'
import {useStore} from '../../store'
import {roleName, confirmAction, run} from '../../lib'
import type {User} from '../../types'
import PageHead from '../../components/PageHead.vue'

const store = useStore()
const s = computed(() => store.state!)
const query = ref('')
const role = ref('')
const target = ref<User>()
const dialog = ref<'permissions' | 'password'>()
const password = ref('')
const selectedPermissions = ref<string[]>([])
const permissionLabels: Record<string,string> = {
  tasks:'任务发布与分派', projectReview:'项目审核与重新开放', assessment:'阶段与结项评分',
  courseGrades:'课程规则与成绩', courseFiles:'上传课程资料', projectFiles:'上传与编辑项目资料',
  discussion:'项目讨论发言', roster:'选课名单管理', sharing:'成果分享设置',
}
function open(user: User, kind: 'permissions' | 'password') {
  target.value = user
  dialog.value = kind
  password.value = ''
  selectedPermissions.value = [...(user.permissions ?? Object.keys(permissionLabels))]
}
async function save() {
  await run(async () => {
    const id = target.value!.id
    if (dialog.value === 'permissions') await store.act('setTeacherPermissions', {id, permissions: selectedPermissions.value})
    else await store.act('setAccountPassword', {id, password: password.value})
    dialog.value = undefined
    password.value = ''
  })
}

const users = computed(() => {
  const classes = new Map(s.value.classes.map(c => [c.id, c.name]))
  const teachingClasses = new Map(s.value.teachingClasses.map(c => [c.id, c.name]))
  const assignments = new Map<string, Set<string>>()
  function assign(id: string, className?: string) {
    if (!className) return
    const assigned = assignments.get(id) ?? new Set<string>()
    assigned.add(className)
    assignments.set(id, assigned)
  }
  for (const e of s.value.enrollments) assign(e.studentId, teachingClasses.get(e.classId))
  for (const c of s.value.teachingClasses) {
    for (const id of c.teacherIds) assign(id, c.name)
  }
  const search = query.value.trim()
  return s.value.users.map(u => ({
    ...u,
    login: u.role === 'student' && u.id !== 'student' ? u.number || u.id : u.id,
    administrativeClass: classes.get(u.classId ?? '') ?? '',
    teachingClasses: [...(assignments.get(u.id) ?? [])].join('、'),
  })).filter(u => (!role.value || u.role === role.value)
    && [u.name, u.login, u.number, u.administrativeClass, u.teachingClasses].some(value => value?.includes(search)))
})
</script>
<template>
  <PageHead title="账号管理">
    <el-button type="primary" @click="$router.push('/admin/import')"><Upload :size="16" />导入账号</el-button>
  </PageHead>
  <div class="toolbar">
    <el-input v-model="query" placeholder="搜索姓名、账号或班级" clearable>
      <template #prefix><Search :size="16" /></template>
    </el-input>
    <el-select v-model="role" clearable placeholder="全部角色">
      <el-option v-for="(name, r) in roleName" :key="r" :label="name" :value="r" />
    </el-select>
    <span class="muted">共 {{users.length}} 个账号</span>
  </div>
  <el-table :data="users">
    <el-table-column prop="login" label="登录账号" min-width="140" />
    <el-table-column prop="name" label="姓名" min-width="150" />
    <el-table-column label="角色" width="110">
      <template #default="{row: u}">{{roleName[u.role]}}</template>
    </el-table-column>
    <el-table-column prop="number" label="学号（仅内部）" min-width="170" />
    <el-table-column label="行政班" min-width="160">
      <template #default="{row: u}">{{u.administrativeClass || '—'}}</template>
    </el-table-column>
    <el-table-column label="教学班" min-width="195">
      <template #default="{row: u}">{{u.teachingClasses || '—'}}</template>
    </el-table-column>
    <el-table-column label="启用状态" width="140">
      <template #default="{row: u}">
        <el-switch :model-value="u.enabled" :disabled="u.id === store.user?.id" @change="(v: boolean) => confirmAction(`${v ? '启用' : '停用'}账号 ${u.name}？`, () => store.act('toggleUser', {id: u.id, enabled: v}))" />
      </template>
    </el-table-column>
    <el-table-column label="账号操作" min-width="200">
      <template #default="{row: u}">
        <el-button v-if="u.role !== 'admin'" text @click="open(u, 'password')">{{u.hasPassword ? '重设密码' : '设置初始密码'}}</el-button>
        <el-button v-if="u.role === 'teacher'" text @click="open(u, 'permissions')">功能权限</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-dialog :model-value="!!dialog" :title="(target?.name || '') + (dialog === 'permissions' ? ' · 教师权限' : ' · 账号密码')"
    width="min(520px,94vw)" @close="dialog = undefined; password = ''">
    <el-checkbox-group v-if="dialog === 'permissions'" v-model="selectedPermissions">
      <el-checkbox v-for="(label,key) in permissionLabels" :key="key" :value="key">{{label}}</el-checkbox>
    </el-checkbox-group>
    <el-input v-else v-model="password" type="password" show-password autocomplete="new-password" placeholder="输入至少 8 位密码" />
    <template #footer><el-button type="primary" :disabled="dialog === 'password' && password.length < 8" @click="save">保存</el-button></template>
  </el-dialog>
</template>
