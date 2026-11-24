<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {ArrowRight, CalendarDays, ChevronDown, Flag} from 'lucide-vue-next'
import type {CourseTask, Project} from '../types'
import {projectPhases, type PortfolioRecord} from '../projectPresentation'
import {stamp} from '../lib'
import LineSidebar from './LineSidebar.vue'

const {records, projects, tasks, month} = defineProps<{records:PortfolioRecord[];projects:Project[];tasks:CourseTask[];month:string}>()
const emit = defineEmits<{select:[id:string]}>()
const route = useRoute()
const projectById = computed(() => new Map(projects.map(project => [project.id, project])))
const taskById = computed(() => new Map(tasks.map(task => [task.id, task])))

const milestoneKinds = {
  complete: {label:'完成项目', rank:5},
  final: {label:'提交结项', rank:4},
  develop: {label:'推进开发', rank:3},
  start: {label:'创建项目', rank:2},
  history: {label:'补录经历', rank:1},
}
type MilestoneKind = keyof typeof milestoneKinds
type Entry = {id:string;time:string;text:string;kind:MilestoneKind;record:PortfolioRecord}

const months = computed(() => {
  const grouped = new Map<string, Map<string, Entry>>()
  for (const record of records) {
    function add(time: string | undefined, text: string, kind: MilestoneKind) {
      const key = time?.match(/^\d{4}-(0[1-9]|1[0-2])/)?.[0]
      if (!key || !time) return
      const id = [record.id, time, text].join('/')
      const entries = grouped.get(key) || new Map()
      entries.set(id, {id, time, text, kind, record})
      grouped.set(key, entries)
    }

    add(record.date, record.history ? '补录项目经历' : '创建项目', record.history ? 'history' : 'start')
    const project = projectById.value.get(record.id)
    if (!project || record.history) continue
    const phases = new Map(projectPhases(taskById.value.get(project.taskId), project).map(phase => [phase.id, phase.name]))
    for (const event of project.activity) {
      if (event.text.startsWith('项目申请审核通过') || event.text === '开始班级任务') add(event.time, '进入项目开发', 'develop')
      if (event.text === '移出归档，重新开放项目') add(event.time, '重新开放项目', 'develop')
    }
    for (const submission of project.submissions) {
      const name = phases.get(submission.milestoneId) || '项目阶段'
      add(submission.time, name + '已提交', 'develop')
      if (submission.status === '已通过') add(submission.reviewTime, name + '通过验收', 'develop')
      if (submission.status === '退回修改') add(submission.reviewTime, name + '退回修改', 'develop')
    }
    for (const final of [...(project.finalHistory || []), project.final]) {
      if (!final) continue
      add(final.submitTime, '提交结项材料', 'final')
      if (final.approved) add(final.reviewTime, '通过结项验收', 'complete')
      else if (final.reviewTime) add(final.reviewTime, '结项退回修改', 'final')
    }
  }

  return [...grouped].sort(([a],[b]) => b.localeCompare(a)).map(([id, entries]) => {
    const [year, monthNumber] = id.split('-')
    const events = [...entries.values()].sort((a,b) => b.time.localeCompare(a.time))
    const keyProjects = new Map<string, Entry>()
    for (const event of events) {
      const latest = keyProjects.get(event.record.id)
      if (!latest || milestoneKinds[event.kind].rank > milestoneKinds[latest.kind].rank) keyProjects.set(event.record.id, event)
    }
    const highlights = [...keyProjects.values()].sort((a,b) => milestoneKinds[b.kind].rank - milestoneKinds[a.kind].rank)
    const counts = new Map<MilestoneKind, number>()
    for (const {kind} of highlights) counts.set(kind, (counts.get(kind) || 0) + 1)
    const preview = [...counts].map(([kind,count]) => milestoneKinds[kind].label + ' ' + count + ' 项').join('、')
    return {id, label:`${year} 年 ${Number(monthNumber)} 月`, preview, events, highlights}
  })
})
const selected = computed(() => months.value.find(item => item.id === month) || months.value[0])
function recordPath(id: string, monthId: string) {
  if (projectById.value.has(id)) return {path:'/student/projects/'+id,query:{origin:'/student/personal'}}
  return {path:'/student/personal/'+id, query:{...route.query, month:monthId}}
}
</script>

<template>
  <section class="section archive-timeline">
    <div class="section-head"><h2><CalendarDays :size="18" />个人时间线</h2></div>
    <div v-if="selected" class="timeline-layout">
      <LineSidebar :items="months" :active="selected.id" @select="emit('select', $event)" />
      <div class="timeline-month">
        <div class="timeline-heading" aria-live="polite">
          <h3>{{ selected.label }}</h3>
          <p>{{ selected.preview }}</p>
        </div>
        <ul class="month-highlights">
          <li v-for="entry in selected.highlights" :key="entry.record.id">
            <RouterLink :to="recordPath(entry.record.id, selected.id)">
              <span>{{ entry.record.name }}</span>
              <small :class="{complete:entry.kind === 'complete'}">{{ milestoneKinds[entry.kind].label }}</small>
              <ArrowRight :size="14" aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
        <details :key="selected.id" class="month-details">
          <summary><ChevronDown :size="16" aria-hidden="true" />里程碑详情 <small>{{ selected.events.length }} 条</small></summary>
          <ol class="timeline-events" tabindex="0" aria-label="里程碑详情">
            <li v-for="event in selected.events" :key="event.id">
              <time :datetime="event.time">{{ stamp(event.time).slice(5) }}</time>
              <Flag :size="15" aria-hidden="true" />
              <div>
                <RouterLink :to="recordPath(event.record.id, selected.id)">{{ event.text }}</RouterLink>
                <small>{{ event.record.name }}</small>
              </div>
            </li>
          </ol>
        </details>
      </div>
    </div>
    <p v-else class="muted">开始项目后，立项、阶段验收和结项会按月汇总在这里。</p>
  </section>
</template>

<style scoped>
.archive-timeline{margin-top:16px;padding:14px 16px}
.archive-timeline h2,.month-details summary{display:flex;align-items:center;gap:7px}
.timeline-layout{display:grid;grid-template-columns:minmax(144px,23%) minmax(0,1fr);gap:16px}
.timeline-month{min-width:0}
.timeline-heading h3,.timeline-heading p{margin:0}
.timeline-heading p{font-size:13px;color:#445b79}
.month-highlights,.timeline-events{list-style:none;padding:0;margin:6px 0}
.month-highlights a{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:7px;padding:6px 0;color:#294d79;border-bottom:1px solid var(--line);overflow-wrap:anywhere}
.month-highlights small{padding:1px 6px;border-radius:4px;background:#edf3fa;color:#465c7b;font-size:12px}
.month-highlights small.complete{background:#e9f3ef;color:#276353}
.month-details summary{cursor:pointer;width:fit-content;padding:4px 0;font-size:13px;color:#345b88;font-weight:600;list-style:none}
.month-details summary::-webkit-details-marker{display:none}
.month-details[open] summary svg{transform:rotate(180deg)}
.month-details small{font-weight:400;color:var(--muted)}
.timeline-events{max-height:290px;overflow:auto}
.timeline-events li{display:grid;grid-template-columns:79px 16px minmax(0,1fr);gap:7px;align-items:start;padding:6px 0;border-top:1px solid var(--line)}
.timeline-events time,.timeline-events small{font-size:12px}
.timeline-events time{font-variant-numeric:tabular-nums}
.timeline-events svg{margin-top:3px;color:#5778a8}
.timeline-events small{display:block}
@media(max-width:620px){.timeline-layout{grid-template-columns:minmax(0,1fr);gap:9px}}
</style>
