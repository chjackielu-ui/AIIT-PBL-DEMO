<script setup lang="ts">
import {ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ArrowRight, BookOpen, GraduationCap, LockKeyhole, Settings, UserRound} from 'lucide-vue-next'
import {useStore} from '../store'
import {api} from '../api'
import {run, safeRedirect} from '../lib'
import type {Role} from '../types'
import ShinyText from '../components/ShinyText.vue'

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
  <main class="login-page">
    <header class="login-intro">
      <h1 aria-label="安徽信息工程学院项目管理平台">
        <ShinyText color="#132f56" shine-color="#3279ce"
          :speed="2" :delay="3" :shine-width="40" :softness="0.8" trigger="loop" follow-pointer>
          安徽信息工程学院<br />项目管理平台
        </ShinyText>
      </h1>
      <p>课程实训、团队协作与成果提交</p>
    </header>

    <section class="login-box" aria-labelledby="login-heading">
      <div class="login-heading">
        <span class="login-mark"><BookOpen :size="21" aria-hidden="true" /></span>
        <h2 id="login-heading">{{registering ? '学生注册' : '登录平台'}}</h2>
      </div>
      <p class="login-description">{{registering ? '填写学号、姓名和密码，创建学生账号。' : '请选择身份，使用你的账号登录。'}}</p>
      <div v-if="!registering" class="login-identities" role="group" aria-label="登录身份">
        <button v-for="item in roles" :key="item.id" type="button" :aria-pressed="role === item.id"
          :disabled="busy" @click="role = item.id">
          <component :is="item.icon" :size="19" aria-hidden="true" />{{item.label}}
        </button>
      </div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item :label="registering ? '学号' : role === 'student' ? '学号或账号' : '账号'">
          <el-input v-model="id" autocomplete="username" :maxlength="60" :prefix-icon="UserRound" />
        </el-form-item>
        <el-form-item v-if="registering" label="姓名">
          <el-input v-model="name" autocomplete="name" :maxlength="60" :prefix-icon="UserRound" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password :maxlength="128" :prefix-icon="LockKeyhole"
            :autocomplete="registering ? 'new-password' : 'current-password'" />
        </el-form-item>
        <el-button class="login-submit" type="primary" native-type="submit" :loading="busy"
          :disabled="!id.trim() || !password || registering && !name.trim()">
          {{registering ? '注册并登录' : '登录'}}
          <ArrowRight v-if="!busy" :size="17" aria-hidden="true" />
        </el-button>
      </el-form>
      <div v-if="role === 'student'" class="login-alternate">
        <p v-if="registering">已由教务名单导入的学号，请联系管理员开通。</p>
        <el-button link type="primary" :disabled="busy" @click="registering = !registering">
          {{registering ? '返回登录' : '注册学生账号'}}
        </el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  --login-accent: #225cb1;
  --login-ink: #132f56;
  --login-muted: #425979;
  --login-line: #cfdef0;
  --el-color-primary: var(--login-accent);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(350px, 420px);
  min-height: 100vh;
  min-height: 100svh;
  align-items: center;
  gap: clamp(32px, 5vw, 80px);
  padding: clamp(24px, 6svh, 78px) max(5.5vw, calc((100% - 1280px) / 2)) clamp(108px, 22svh, 270px);
  background: #eef4fd url('../assets/login-campus-landscape.webp') center bottom / cover no-repeat;
  color: var(--login-ink);
  font-family: "Inter", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}
.login-mark {
  display: inline-grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 11px;
  color: var(--login-accent);
  background: #e7f0ff;
}
.login-intro h1 {
  margin: 0 0 20px;
  font-size: clamp(36px, 4.6vw, 78px);
  font-weight: 800;
  line-height: 1.19;
  letter-spacing: -.035em;
}
.login-intro p { margin: 0; color: var(--login-muted); font-size: clamp(15px, 1.35vw, 20px); }
.login-box {
  width: 100%;
  padding: clamp(24px, 2.8vw, 38px);
  border: 1px solid #fff;
  border-radius: 24px;
  background: #ffffffee;
  box-shadow: 0 22px 62px #18498020, 0 3px 12px #1849800b;
}
.login-heading { display: flex; align-items: center; gap: 12px; }
.login-box h2 { margin: 0; font-size: clamp(24px, 2.2vw, 32px); line-height: 1.2; }
.login-description { margin: 12px 0 20px; color: var(--login-muted); }
.login-identities {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 20px;
}
.login-identities button {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 58px;
  padding: 6px;
  background: #fff;
  border: 1px solid var(--login-line);
  border-radius: 11px;
  color: var(--login-muted);
  font: inherit;
  font-weight: 600;
}
.login-identities button:hover:not(:disabled) { border-color: #7b9cc6; }
.login-identities button[aria-pressed="true"] {
  color: var(--login-accent);
  background: #edf4ff;
  border-color: #83a9df;
}
.login-page :is(a, button):focus-visible { outline: 2px solid #5276cf; outline-offset: 3px; }
.login-box :deep(.el-form-item) { margin-bottom: 18px; }
.login-box :deep(.el-form-item__label) { color: var(--login-ink); font-weight: 600; }
.login-box :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 10px;
  padding-inline: 12px;
  box-shadow: 0 0 0 1px var(--login-line) inset;
}
.login-box :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 2px #7491da inset; }
.login-box :deep(.el-input__inner) { font-size: 16px; }
.login-submit {
  width: 100%;
  min-height: 46px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 650;
}
.login-submit svg { margin-left: 8px; }
.login-alternate { margin-top: 18px; text-align: center; color: var(--login-muted); }
.login-alternate p { margin: 0 0 8px; }

@media (max-width: 870px) {
  .login-page {
    grid-template-columns: minmax(0, 440px);
    justify-content: center;
    align-content: center;
    gap: 24px;
    padding: 32px 18px clamp(100px, 16svh, 186px);
    background:
      linear-gradient(#eef4fd, #eef4fd00 52%) center bottom / 100% max(237px, 56.3vw) no-repeat,
      #eef4fd url('../assets/login-campus-landscape.webp') center bottom / max(100%, 420px) auto no-repeat;
  }
  .login-intro h1 { margin-bottom: 9px; font-size: clamp(30px, 8vw, 52px); }
  .login-box { padding: clamp(18px, 5vw, 30px); border-radius: 20px; }
}
</style>
