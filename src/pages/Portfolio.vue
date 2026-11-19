<script setup lang="ts">
import {computed, reactive, ref, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {Plus, Download, Search} from 'lucide-vue-next'
import {useStore} from '../store'
import {api} from '../api'
import {currentTerm} from '../academic'
import {run, confirmAction, printPage, localDate} from '../lib'
import {portfolioRecords} from '../projectPresentation'
import type {HistoryRecord} from '../types'
import PageHead from '../components/PageHead.vue'
defineProps<{embedded?:boolean}>()
import Status from '../components/Status.vue'

const store = useStore()
const route = useRoute()
const router = useRouter()
const s = computed(() => store.state!)
onMounted(()=>run(async()=>{const data=await api<{projects: typeof s.value.projects;history:typeof s.value.history}>('/portfolio');s.value.projects=data.projects;s.value.history=data.history},''))

const q = ref('')
const year = ref('')
const status = ref('')
const dialog = ref(false)

const blank = (): HistoryRecord => ({
  id: '', userId: store.user!.id, name: '', year: currentTerm().academicYear.replace('-', '–'),
  date: localDate(), role: '', description: '', contribution: '', summary: '',
})
const form = reactive(blank())

/** 项目经历与历史补录合并（字段语义与打印页共用 portfolioRecords）。 */
const records = computed(() => portfolioRecords(s.value, store.user!.id))
const filtered = computed(() => records.value.filter(r =>
  (r.name + ' ' + r.contribution).includes(q.value)
  && (!year.value || r.year === year.value)
  && (!status.value || r.status === status.value)))
const detail = computed(() => records.value.find(r => r.id === route.params.id))

function edit(id = '') {
  Object.assign(form, blank(), s.value.history.find(h => h.id === id) || {})
  dialog.value = true
}

async function save() {
  if (!form.name.trim() || !form.role.trim() || !form.description.trim() || !form.contribution.trim() || !form.summary.trim()) {
    const {ElMessage} = await import('element-plus')
    ElMessage.warning('请完整填写补录信息')
    return
  }
  await run(async () => {
    const res = await store.act('historySave', {record: form})
    dialog.value = false
    if (!route.params.id) await router.push('/student/personal/' + res.id)
  }, '历史补录已保存')
}

async function remove(id: string) {
  await confirmAction('删除这条未认证的历史补录记录？', async () => {
    await store.act('historyDelete', {id})
    await router.push('/student/personal')
  }, '补录已删除')
}
</script>
<template>
  <PageHead :title="detail ? detail.name : embedded ? '项目档案' : '个人情况'">
    <el-button v-if="detail" @click="router.push('/student/personal')">返回档案</el-button>
    <el-button @click="printPage('portfolio')"><Download :size="16" />个人履历 PDF</el-button>
    <el-button v-if="!detail" type="primary" @click="edit()"><Plus :size="16" />历史项目补录</el-button>
    <el-button v-if="detail?.history" @click="edit(detail.id)">编辑补录</el-button>
    <el-button v-if="detail?.history" @click="remove(detail.id)">删除补录</el-button>
  </PageHead>
  <template v-if="detail">
    <section class="section">
      <div class="section-head"><h2>项目经历</h2><Status :value="detail.status" /></div>
      <p>{{detail.date}} · {{detail.year}} 学年 · {{detail.role}}</p>
      <h3>个人职责</h3>
      <p class="pre-wrap">{{detail.description}}</p>
      <h3>个人贡献</h3>
      <p class="pre-wrap">{{detail.contribution}}</p>
      <h3>成果摘要</h3>
      <p class="pre-wrap">{{detail.summary}}</p>
      <p v-if="detail.score != null">个人成绩：{{detail.score}} 分 · {{detail.comment}}</p>
      <p v-if="detail.history" class="muted">历史补录：本人填写，未经平台教师认证。</p>
      <el-button v-else @click="router.push('/student/projects/' + detail.id)">进入项目工作区</el-button>
    </section>
  </template>
  <template v-else>
    <div class="toolbar">
      <el-input v-model="q" placeholder="搜索项目名称或个人贡献" clearable>
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-select v-model="year" placeholder="学年">
        <el-option v-for="y in [...new Set([currentTerm().academicYear.replace('-', '–'), ...records.map(r => r.year)])].sort().reverse()" :key="y" :value="y" />
      </el-select>
      <el-select v-model="status" clearable placeholder="全部状态">
        <el-option v-for="v in [...new Set(records.map(r => r.status))]" :key="v" :value="v" />
      </el-select>
    </div>
    <div class="portfolio-summary">
      <span class="avatar">{{store.user?.name.slice(0, 1)}}</span>
      <div>
        <h2>{{store.user?.name}}</h2>
        <p class="muted">{{s.classes.find(c => c.id === store.user?.classId)?.name}} · {{records.length}} 条项目经历</p>
      </div>
    </div>
    <article v-for="r in filtered" :key="r.id" class="portfolio-record">
      <div class="portfolio-date">{{r.year}}<small>{{r.date}}</small></div>
      <div class="portfolio-content">
        <div class="section-head">
          <RouterLink :to="'/student/personal/' + r.id" class="card-title">{{r.name}}</RouterLink>
          <Status :value="r.status" />
        </div>
        <p>{{r.role}} · {{r.description}}</p>
        <p class="muted">{{r.summary}}</p>
        <p v-if="r.history" class="muted">历史补录 · 未经教师认证</p>
        <p v-if="r.score != null">个人成绩 {{r.score}} 分 · {{r.comment}}</p>
        <el-button @click="router.push('/student/personal/' + r.id)">查看经历</el-button>
      </div>
    </article>
    <el-empty v-if="!filtered.length" description="没有符合条件的项目经历">
      <el-button @click="q = ''; year = currentTerm().academicYear.replace('-', '–'); status = ''">清除筛选</el-button>
    </el-empty>
  </template>
  <el-dialog v-model="dialog" :title="form.id ? '编辑历史补录' : '历史项目补录'" width="680px">
    <el-alert title="本记录将标注为历史补录，未经教师认证。" :closable="false" />
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="项目名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="学年" required><el-input v-model="form.year" /></el-form-item>
        <el-form-item label="项目日期" required><el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="个人角色" required><el-input v-model="form.role" /></el-form-item>
      </div>
      <el-form-item label="职责说明" required><el-input v-model="form.description" type="textarea" /></el-form-item>
      <el-form-item label="个人贡献" required><el-input v-model="form.contribution" type="textarea" /></el-form-item>
      <el-form-item label="成果摘要" required><el-input v-model="form.summary" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" @click="save">保存补录</el-button>
    </template>
  </el-dialog>
</template>
