<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {Printer, ArrowLeft, Layers} from 'lucide-vue-next'
import {useStore} from '../store'
import {termLabel} from '../academic'

import {projectPresentation, portfolioRecords} from '../projectPresentation'
import {downloadMarkdown} from '../markdown'
import ProjectDocument from '../components/ProjectDocument.vue'
import PortfolioDocument from '../components/PortfolioDocument.vue'
import StudentReport from '../components/StudentReport.vue'

const route = useRoute()
const router = useRouter()
const store = useStore()
const s = computed(() => store.state)
const u = computed(() => store.user)
const kind = computed(() => String(route.params.kind || ''))
const studentReport = computed(() => {
  if (u.value?.role !== 'student') return undefined
  if (kind.value === 'transcript' || kind.value === 'learning') return kind.value
})
const reportClass = computed(() => typeof route.query.classId === 'string' ? route.query.classId : undefined)
const busy = ref(false)

const p = computed(() => s.value?.projects.find(p => p.id === route.params.id))
const projectData = computed(() => {
  if (kind.value !== 'project' || !p.value || !s.value) return undefined
  return projectPresentation(s.value, p.value)
})
const portfolio = computed(() =>
  (s.value && u.value && kind.value === 'portfolio') ? portfolioRecords(s.value, u.value.id) : [])

const docTitle = computed(() => {
  if (studentReport.value) return studentReport.value === 'transcript' ? '个人课程成绩单' : '个人学情报告'
  if (kind.value === 'portfolio') return '个人项目履历'
  return projectData.value ? `项目介绍_${projectData.value.name}` : '项目介绍'
})

function exportMd(){
  const d=projectData.value
  const sections=d ? [
   '# '+d.name,[d.course,d.teachingClass,d.created,d.status,d.tags].filter(Boolean).join(' · '),
   '## 项目简介',d.description,'## 项目成员',d.members.map(m=>'- '+m.name+' · '+m.role).join('\n'),
   d.summary && '## 已审核最终成果',d.summary,d.result,d.link,d.comment && '## 教师评价',d.comment,
   ...d.images.map(img=>'['+img.name+']('+location.origin+'/api/projects/'+p.value!.id+'/files/'+img.id+')')
  ] : ['# '+(u.value?.name || '')+'的项目履历',...portfolio.value.flatMap(r=>[
   '## '+r.name,[r.course,r.teachingClass,termLabel(r.year,r.semester),r.role,r.date].filter(Boolean).join(' · '),r.description,r.contribution,r.summary,r.score!=null?'成绩：'+r.score:'',r.comment
  ])]
  downloadMarkdown(docTitle.value,sections.filter(Boolean).join('\n\n'))
}
async function print() {
  if (!projectData.value && kind.value === 'project') {
    const {ElMessage} = await import('element-plus')
    ElMessage.warning('当前无可打印内容')
    return
  }
  busy.value = true
  const old = document.title
  document.title = docTitle.value
  await Promise.all([...document.images].map(img => img.decode().catch(() => {})))
  window.print()
  document.title = old
  busy.value = false
}
</script>
<template>
  <div class="public-page">
    <div class="public-toolbar no-print">
      <span class="actions"><Layers :size="20" />安信工项目管理平台 <span class="muted">/ 打印预览</span></span>
      <div class="actions">
        <el-button @click="studentReport ? router.push('/student/learning') : router.back()"><ArrowLeft :size="16" />返回</el-button>
        <el-button v-if="projectData || kind==='portfolio'" @click="exportMd">导出 Markdown</el-button>
        <el-button type="primary" :loading="busy" @click="print"><Printer :size="16" />打印 / 保存为 PDF</el-button>
      </div>
    </div>
    <p class="print-tip no-print">使用浏览器打印功能，目标选择“另存为 PDF”。{{ studentReport ? '此报告包含本人学号及课程记录。' : '此预览已排除学号、讨论与内部资料。' }}</p>
    <StudentReport v-if="studentReport && s" :state="s" :kind="studentReport" :class-id="reportClass" />
    <PortfolioDocument v-else-if="kind === 'portfolio' && u?.role === 'student'" :records="portfolio" :name="u.name" :admin-class="s?.classes.find(c => c.id === u?.classId)?.name" />
    <ProjectDocument v-else-if="projectData" :data="projectData" :image-url="(id: string) => `/api/projects/${p!.id}/files/${id}`" />
    <el-result v-else title="无可导出的内容" sub-title="请从项目工作区或个人档案进入打印预览。" />
    <div class="public-footer" :class="{'transcript-footer':studentReport === 'transcript'}">安信工项目管理平台 · {{docTitle}}</div>
  </div>
</template>

<style scoped>
.transcript-footer{color:#000}
</style>
