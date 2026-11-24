<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import {BookOpen, Camera, KeyRound, Pencil, Trash2} from 'lucide-vue-next'
import {useStore} from '../store'
import {api} from '../api'
import {confirmAction, roleName, run} from '../lib'
import AccountAvatar from '../components/AccountAvatar.vue'
import PageHead from '../components/PageHead.vue'

const store = useStore()
const user = computed(() => store.user!)
const administrativeClass = computed(() => store.state?.classes.find(c => c.id === user.value.classId))
const classes = computed(() => {
  const state = store.state!
  const enrolled = new Set(state.enrollments.filter(e => e.studentId === user.value.id).map(e => e.classId))
  return state.teachingClasses.filter(c => enrolled.has(c.id))
    .map(c => ({...c, course: state.courses.find(course => course.id === c.courseId)?.name}))
})

const interestOptions = ['前端开发', '后端开发', '移动开发', '人工智能', '数据工程', '网络安全', '软件测试', '产品设计']
const stackOptions = ['Java', 'Spring Boot', 'Python', 'TypeScript', 'Vue', 'React', 'MySQL', 'Git', 'Docker']
const detailsOpen = ref(false)
const passwordOpen = ref(false)
const avatarInput = ref<HTMLInputElement>()
const saving = ref(false)
const uploading = ref(false)
const profile = reactive({bio: '', interests: [] as string[], techStack: [] as string[]})
const password = reactive({currentPassword: '', newPassword: '', confirm: ''})

function edit() {
  profile.bio = user.value.bio ?? ''
  profile.interests = [...(user.value.interests ?? [])]
  profile.techStack = [...(user.value.techStack ?? [])]
  detailsOpen.value = true
}

async function saveDetails() {
  saving.value = true
  await run(async () => {
    await store.act('updateProfile', profile)
    detailsOpen.value = false
  }, '个人资料已保存')
  saving.value = false
}

function clearPassword() {
  password.currentPassword = password.newPassword = password.confirm = ''
}

async function savePassword() {
  saving.value = true
  await run(async () => {
    if (password.newPassword.length < 8) throw Error('新密码至少需要 8 个字符')
    if (password.newPassword !== password.confirm) throw Error('两次输入的新密码不一致')
    await store.act('changePassword', {currentPassword: password.currentPassword, newPassword: password.newPassword})
    clearPassword()
    passwordOpen.value = false
  }, '密码已更新')
  saving.value = false
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  await run(async () => {
    if (file.size > 2 * 1024 * 1024) throw Error('请选择 2 MB 以内的 JPG 或 PNG 图片')
    const body = new FormData()
    body.append('file', file)
    const result = await api<{avatarVersion: number}>('/profile/avatar', body)
    user.value.avatarVersion = result.avatarVersion
    user.value.hasCustomAvatar = true
  }, '头像已更新')
  uploading.value = false
}

function removeAvatar() {
  confirmAction('确定恢复默认头像？', async () => {
    await api('/profile/avatar', undefined, 'DELETE')
    await store.load()
  }, '已恢复默认头像')
}
</script>

<template>
  <PageHead title="个人主页">
    <el-button v-if="user.role === 'student'" @click="$router.push('/student/personal')"><BookOpen :size="16" />个人情况</el-button>
    <el-button type="primary" @click="edit"><Pencil :size="16" />编辑资料</el-button>
  </PageHead>

  <section class="section profile-heading">
    <div class="profile-portrait">
      <AccountAvatar :user="user" :size="84" />
      <div class="profile-identity">
        <h2>{{user.name}}</h2>
        <p class="muted">{{roleName[user.role]}}<template v-if="administrativeClass"> · {{administrativeClass.name}}</template></p>
        <p class="profile-bio">{{user.bio || '还没有填写个性签名'}}</p>
      </div>
    </div>
    <div v-file-drop class="actions profile-photo-actions" title="也可将 JPG 或 PNG 拖到这里更换头像">
      <input ref="avatarInput" type="file" :disabled="uploading" accept="image/jpeg,image/png" hidden @change="uploadAvatar">
      <el-button :loading="uploading" @click="avatarInput?.click()"><Camera :size="16" />更换头像</el-button>
      <el-button v-if="user.hasCustomAvatar" :disabled="uploading" @click="removeAvatar"><Trash2 :size="16" />恢复默认头像</el-button>
    </div>
  </section>

  <div class="profile-columns">
    <div>
      <section class="section">
        <h2>身份与班级</h2>
        <div class="details-grid">
          <div><small>姓名</small><span>{{user.name}}</span></div>
          <div><small>身份</small><span>{{roleName[user.role]}}</span></div>
          <div v-if="user.role === 'student'"><small>学号</small><span>{{user.number || '未登记'}}</span></div>
          <div v-else><small>账号</small><span>{{user.id}}</span></div>
          <div v-if="user.role === 'student'"><small>行政班</small><span>{{administrativeClass?.name || '未分配'}}</span></div>
        </div>
        <template v-if="user.role === 'student'">
          <h3>所属教学班</h3>
          <ul v-if="classes.length" class="profile-classes">
            <li v-for="c in classes" :key="c.id"><strong>{{c.name}}</strong><span class="muted">{{c.course || (c.academicYear + ' · ' + c.semester)}}</span></li>
          </ul>
          <p v-else class="muted">暂无关联教学班</p>
        </template>
      </section>
    </div>
    <div>
      <section class="section">
        <h2>兴趣与技术栈</h2>
        <h3>感兴趣的方向</h3>
        <div v-if="user.interests?.length" class="profile-tags"><el-tag v-for="item in user.interests" :key="item" effect="plain">{{item}}</el-tag></div>
        <p v-else class="muted">尚未添加方向</p>
        <h3>常用技术栈</h3>
        <div v-if="user.techStack?.length" class="profile-tags"><el-tag v-for="item in user.techStack" :key="item" type="info" effect="plain">{{item}}</el-tag></div>
        <p v-else class="muted">尚未添加技术栈</p>
      </section>
      <section class="section">
        <div class="section-head profile-security"><h2>账号安全</h2><el-button @click="passwordOpen = true"><KeyRound :size="16" />修改密码</el-button></div>
      </section>
    </div>
  </div>

  <el-dialog v-model="detailsOpen" title="编辑个人资料" width="min(580px,94vw)" :close-on-click-modal="!saving">
    <el-form label-position="top" @submit.prevent="saveDetails">
      <el-form-item label="个性签名"><el-input v-model="profile.bio" type="textarea" :rows="3" :maxlength="160" show-word-limit /></el-form-item>
      <el-form-item label="感兴趣的方向">
        <el-select v-model="profile.interests" multiple filterable allow-create default-first-option :multiple-limit="10" placeholder="选择或输入自定义方向">
          <el-option v-for="item in interestOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="常用技术栈">
        <el-select v-model="profile.techStack" multiple filterable allow-create default-first-option :multiple-limit="10" placeholder="选择或输入自定义技术">
          <el-option v-for="item in stackOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="detailsOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDetails">保存</el-button></template>
  </el-dialog>

  <el-dialog v-model="passwordOpen" title="修改密码" width="min(480px,94vw)" :close-on-click-modal="false" @closed="clearPassword">
    <el-form label-position="top" @submit.prevent="savePassword">
      <el-form-item label="当前密码"><el-input v-model="password.currentPassword" type="password" show-password autocomplete="current-password" :maxlength="128" /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password" placeholder="至少 8 个字符" :maxlength="128" /></el-form-item>
      <el-form-item label="确认新密码"><el-input v-model="password.confirm" type="password" show-password autocomplete="new-password" :maxlength="128" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="passwordOpen = false">取消</el-button><el-button type="primary" :disabled="!password.currentPassword || !password.newPassword || !password.confirm" :loading="saving" @click="savePassword">更新密码</el-button></template>
  </el-dialog>
</template>

<style scoped>
.profile-heading, .profile-portrait, .profile-tags {display:flex; align-items:center; gap:16px}
.profile-heading {justify-content:space-between; flex-wrap:wrap}
.profile-portrait, .profile-identity {min-width:0}
.profile-identity h2, .profile-identity p {margin:0}
.profile-identity p + p {margin-top:8px}
.profile-bio {overflow-wrap:anywhere; white-space:pre-wrap}
.profile-photo-actions, .profile-tags {flex-wrap:wrap}
.profile-columns {display:grid; grid-template-columns:minmax(0, 1.4fr) minmax(0, 1fr); gap:16px}
.profile-columns .details-grid {grid-template-columns:repeat(2,minmax(0,1fr))}
.profile-classes {list-style:none; margin:0; padding:0}
.profile-classes li {display:grid; gap:4px; padding:10px 0; border-top:1px solid var(--line)}
.profile-tags {gap:8px}
.profile-security {margin-bottom:0}
.profile-security h2 {margin:0}
@media (max-width:760px) {.profile-columns {grid-template-columns:minmax(0,1fr)}}
</style>
