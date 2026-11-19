<script setup lang="ts">
import {ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {GraduationCap, BookOpen, Settings, Layers} from 'lucide-vue-next'
import {useStore} from '../store'
import {api} from '../api'
import {run, safeRedirect} from '../lib'
import type {Role} from '../types'

const store = useStore(), route = useRoute(), router = useRouter()
const roles = [
  {id: 'student' as const, label: '学生', icon: GraduationCap},
  {id: 'teacher' as const, label: '教师', icon: BookOpen},
  {id: 'admin' as const, label: '管理员', icon: Settings},
]
const role = ref<Role>('student')
const id = ref(''), name = ref(''), password = ref(''), busy = ref(false), registering = ref(false)

async function submit() {
  busy.value = true
  try {
    await run(async () => {
      if (registering.value) {
        await api('/register', {number: id.value.trim(), name: name.value.trim(), password: password.value})
        registering.value = false
      }
      await store.login(id.value.trim(), role.value, password.value)
      await router.push(safeRedirect(String(route.query.redirect || '')) || `/${role.value}/dashboard`)
    }, '')
  } finally { busy.value = false }
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand"><Layers :size="20" />安信工项目管理平台</div>
    <section class="login-box">
      <div class="page-head"><h1>{{registering ? '学生注册' : '登录'}}</h1></div>
      <div v-if="!registering" class="login-identities" aria-label="登录身份">
        <el-button v-for="item in roles" :key="item.id" native-type="button"
          :type="role === item.id ? 'primary' : 'default'" :aria-pressed="role === item.id"
          :disabled="busy" @click="role = item.id">
          <component :is="item.icon" :size="16" />{{item.label}}
        </el-button>
      </div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item :label="registering ? '学号' : role === 'student' ? '学号或账号' : '账号'">
          <el-input v-model="id" autocomplete="username" :maxlength="60" />
        </el-form-item>
        <el-form-item v-if="registering" label="姓名">
          <el-input v-model="name" autocomplete="name" :maxlength="60" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password :maxlength="128"
            :autocomplete="registering ? 'new-password' : 'current-password'" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy"
          :disabled="!id.trim() || !password || registering && !name.trim()">
          {{registering ? '注册并登录' : '登录'}}
        </el-button>
        <el-button v-if="role === 'student'" text :disabled="busy" @click="registering = !registering">
          {{registering ? '返回登录' : '注册学生账号'}}
        </el-button>
      </el-form>
      <p v-if="registering" class="muted">已由教务名单导入的学号，请联系管理员开通。</p>
    </section>
  </div>
</template>

<style scoped>
.login-identities {display:flex;flex-wrap:wrap;gap:8px;margin:16px 0}
.login-identities .el-button {margin:0}
.login-identities svg {margin-right:5px}
</style>
