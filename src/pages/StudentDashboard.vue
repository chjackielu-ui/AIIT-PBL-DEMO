<script setup lang="ts">
import {computed, ref, useTemplateRef} from 'vue'
import {ArrowRight, Bell, FileText, FolderKanban} from 'lucide-vue-next'
import {useStore} from '../store'
import {latestTerm, relevantClasses} from '../academic'
import {canStartTask} from '../taskMode'
import {projectDevelopmentStatuses, projectMembers, projectProgress} from '../projectPresentation'
import {studentTodo} from '../studentTodo'
import {stamp} from '../lib'
import {useNotificationActions} from '../notifications'
import AccountAvatar from '../components/AccountAvatar.vue'
import BorderGlow from '../components/BorderGlow.vue'
import PageHead from '../components/PageHead.vue'
import ProjectTeam from '../components/ProjectTeam.vue'
import StudentShortcuts from '../components/StudentShortcuts.vue'
import StudentTodo from '../components/StudentTodo.vue'
import TermFilter from '../components/TermFilter.vue'

const store = useStore()
const s = computed(() => store.state!)
const me = computed(() => s.value.user)
const allClasses = computed(() => relevantClasses(s.value))
const term = latestTerm(allClasses.value)
const year = ref(term.academicYear)
const semester = ref(term.semester)
const todo = useTemplateRef<InstanceType<typeof StudentTodo>>('todo')
const {open:openNotice} = useNotificationActions()
const classById = computed(() => new Map(allClasses.value.map(cls => [cls.id, cls])))
const courseById = computed(() => new Map(s.value.courses.map(course => [course.id, course])))
const tasks = computed(() => s.value.tasks.filter(task => {
  const cls = classById.value.get(task.classId)
  return cls?.academicYear === year.value && cls.semester === semester.value
}))
const taskById = computed(() => new Map(tasks.value.map(task => [task.id, task])))
const records = computed(() => s.value.projects.filter(project => taskById.value.has(project.taskId) && project.memberIds.includes(me.value.id)))
const projects = computed(() => records.value.filter(project =>
  projectDevelopmentStatuses.includes(project.status) && taskById.value.get(project.taskId)?.taskKind !== 'exercise')
  .map(project => ({
    project,
    course:courseById.value.get(taskById.value.get(project.taskId)!.courseId)?.name,
    progress:projectProgress(s.value, project),
    members:projectMembers(s.value, project),
    latestFiles:[...project.files].sort((a,b) => b.time.localeCompare(a.time)).slice(0,2),
    updated:project.activity.reduce((latest, event) => event.time > latest ? event.time : latest, project.created),
  })).sort((a,b) => b.updated.localeCompare(a.updated)))
const rows = computed(() => [
  ...(me.value.personalTodos || []).map(todo => ({
    ...todo, personal:true, project:(todo.messageId ? '聊天待办' : '个人待办') + ' · ' + todo.start,
    status:todo.done ? '已完成' : '待完成',
  })),
  ...studentTodo(records.value, taskById.value, me.value.id),
].sort((a,b) => a.due.localeCompare(b.due)))
const pendingCount = computed(() => rows.value.filter(row => !row.done).length)
const available = computed(() => {
  const joined = new Set(records.value.map(project => project.taskId))
  return tasks.value.filter(task => canStartTask(task,courseById.value.get(task.courseId)) && !joined.has(task.id)).length
})
const notices = computed(() => s.value.notifications)
const unread = computed(() => notices.value.filter(notice => !notice.read).length)
const allProjects = computed(() => ({
  path:'/student/projects',
  query:{year:year.value,semester:semester.value,scope:projects.value.length ? 'development' : undefined},
}))
const administrativeClass = computed(() => s.value.classes.find(cls => cls.id === me.value.classId)?.name)
function projectLink(id:string, tab?:string, file?:string) {
  return {path:'/student/projects/'+id,query:{tab,file}}
}
</script>

<template>
  <div class="student-dashboard">
    <PageHead title="学生工作台"><TermFilter :classes="allClasses" v-model:year="year" v-model:semester="semester" /></PageHead>
    <div class="student-overview">
      <AccountAvatar :user="me" :size="42" />
      <div class="student-overview-name"><strong>{{ me.name }}</strong><small v-if="administrativeClass">{{ administrativeClass }}</small></div>
      <div class="student-overview-metrics">
        <button type="button" @click="todo?.reveal()"><strong>{{ pendingCount }}</strong><span>待完成</span></button>
        <RouterLink :to="allProjects"><strong>{{ projects.length }}</strong><span>开发中</span></RouterLink>
        <RouterLink to="/notifications"><strong>{{ unread }}</strong><span>未读通知</span></RouterLink>
      </div>
    </div>

    <div class="student-columns">
      <div class="student-column">
      <section class="section student-panel projects-panel">
        <div class="section-head"><h2><FolderKanban :size="18" />正在开发的项目</h2><RouterLink :to="allProjects">查看我的项目 <ArrowRight :size="14" /></RouterLink></div>
        <div v-if="projects.length" class="recent-projects">
          <BorderGlow v-for="{project,course,progress,members,latestFiles} in projects.slice(0,4)" :key="project.id" class="recent-project">
            <article>
              <div class="recent-heading">
                <small v-if="course">{{ course }}</small>
                <h3><RouterLink :to="projectLink(project.id)">{{ project.name }}</RouterLink></h3>
                <p v-if="project.description">{{ project.description }}</p>
              </div>
              <div class="recent-progress">
                <span>项目进度</span><strong>{{ progress }}%</strong>
                <progress :value="progress" max="100" :aria-label="project.name + '进度'" />
              </div>
              <div class="recent-footer">
                <ProjectTeam :members="members" />
                <RouterLink :to="projectLink(project.id,'files')"><FileText :size="15" />{{ project.files.length }} 份资料</RouterLink>
              </div>
              <div v-if="latestFiles.length" class="recent-files">
                <span>最近更新</span>
                <RouterLink v-for="file in latestFiles" :key="file.id" :to="projectLink(project.id,'files',file.id)" :title="file.name">{{ file.name }}</RouterLink>
              </div>
            </article>
          </BorderGlow>
        </div>
        <p v-if="!projects.length" class="panel-note">本学期还没有开发中的项目。<RouterLink to="/student/tasks">去看看有哪些任务可以参与</RouterLink></p>
      </section>

      <StudentShortcuts @pending="todo?.reveal()" />
      </div>
      <div class="student-column">
      <StudentTodo ref="todo" class="tasks-panel" :rows="rows" :available="available" />

      <section class="section student-panel notices-panel">
        <div class="section-head"><h2><Bell :size="17" />通知 <span v-if="unread" class="notice-count">{{ unread }} 未读</span></h2><RouterLink to="/notifications">查看全部 <ArrowRight :size="14" /></RouterLink></div>
        <RouterLink v-for="notice in notices.slice(0,4)" :key="notice.id" :to="notice.path" class="student-notice" @click.prevent="openNotice(notice)">
          <strong :class="{'unread-notice':!notice.read}">{{ notice.title }}</strong><time :datetime="notice.time">{{ stamp(notice.time).slice(5,10) }}</time><small :title="notice.text">{{ notice.text }}</small>
        </RouterLink>
        <p v-if="!notices.length" class="panel-note">暂时没有新消息，课程和项目提醒会出现在这里。</p>
      </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.student-dashboard{--study-ink:#243e62;--study-blue:#345d91;--study-border:#d9e4f0;--study-pale:#f0f5fb;--study-muted:#52647c}
.student-dashboard :deep(.page-head){margin-bottom:13px}
.student-overview{display:flex;align-items:center;gap:12px;padding:10px 16px;margin-bottom:14px;background:var(--study-pale);border:1px solid var(--study-border);border-radius:10px}
.student-overview-name{display:grid;line-height:1.3;color:var(--study-ink)}.student-overview-name strong{font-size:17px}.student-overview small{font-size:12px;color:var(--study-muted)}
.student-overview-metrics{margin-left:auto;display:flex;align-items:stretch}
.student-overview-metrics :is(a,button){border:0;border-left:1px solid var(--study-border);background:none;color:var(--study-blue);padding:0 15px;text-align:center;display:grid;line-height:1.2}
.student-overview-metrics strong{font-size:20px;font-variant-numeric:tabular-nums}
.student-overview-metrics span{font-size:12px;color:var(--study-muted);margin-top:3px;white-space:nowrap}
.student-columns{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(285px,1fr);align-items:start;gap:14px}
.student-column{display:flex;min-width:0;flex-direction:column;gap:14px}
.projects-panel{grid-area:projects}.tasks-panel{grid-area:tasks}.shortcuts-panel{grid-area:shortcuts}.notices-panel{grid-area:notices}
.student-panel{margin:0;padding:15px 16px;min-width:0}
.student-panel .section-head{margin-bottom:10px}
.student-panel .section-head h2,.student-panel .section-head a,.recent-footer a{display:flex;align-items:center;gap:6px}
.student-panel .section-head a{font-size:13px;color:var(--study-blue);white-space:nowrap}
.recent-projects{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}
.recent-project:only-child{grid-column:1/-1}
.recent-project article{display:grid;align-content:start;gap:9px;padding:12px;min-width:0}
.recent-project:only-child article{grid-template-columns:minmax(0,1.4fr) minmax(0,1fr);gap:10px 18px;padding:15px}
.recent-project:only-child .recent-heading{grid-row:span 2}
.recent-heading{min-width:0}
.recent-heading small{display:block;color:var(--study-muted);font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.recent-heading h3{margin:2px 0;font-size:16px;line-height:1.35;overflow-wrap:anywhere}
.recent-heading h3 a{color:var(--study-ink)}
.recent-heading p{font-size:13px;color:var(--study-muted);margin:3px 0 0;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden}
.recent-progress{display:grid;grid-template-columns:1fr auto;gap:3px;font-size:12px;color:var(--study-muted)}
.recent-progress strong{color:var(--study-blue);font-variant-numeric:tabular-nums}
.recent-progress progress{grid-column:1/-1;width:100%;height:5px;border:0;border-radius:99px;appearance:none;background:#e7eef8;overflow:hidden}
.recent-progress progress::-webkit-progress-bar{background:#e7eef8}
.recent-progress progress::-webkit-progress-value{background:#5379b5;border-radius:99px}
.recent-progress progress::-moz-progress-bar{background:#5379b5}
.recent-footer{display:flex;align-items:center;justify-content:space-between;gap:6px;font-size:12px}
.recent-footer a{margin-left:auto}
.recent-files{grid-column:1/-1;display:flex;align-items:center;gap:4px 9px;flex-wrap:wrap;border-top:1px solid var(--study-border);padding-top:7px;font-size:12px;min-width:0}
.recent-files span{color:var(--study-muted);white-space:nowrap}
.recent-files a{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.notice-count{background:#edf3fb;color:var(--study-blue);padding:1px 7px;border-radius:5px;font-size:12px;white-space:nowrap}
.student-notice {
  display:grid;
  grid-template-columns:minmax(0,1fr) auto;
  gap:0 8px;
  color:var(--study-ink);
  padding:8px 4px;
  border-bottom:1px solid var(--study-border);
}
.student-notice:last-of-type {border-bottom:0}
.student-notice:hover {background:var(--hover);text-decoration:none}
.student-notice:hover strong,.notices-panel .section-head>a:hover {text-decoration:underline}
.student-notice:focus-visible,.notices-panel .section-head>a:focus-visible {
  outline:2px solid var(--primary);
  outline-offset:-2px;
}
.notices-panel .section-head>a {min-height:32px;padding:4px;border-radius:var(--small-radius)}
.student-notice :is(strong,small){white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.student-notice :is(time,small){font-size:12px;color:var(--study-muted)}
.student-notice small{grid-column:1/-1}
.student-notice .unread-notice::before{content:'';display:inline-block;width:6px;height:6px;background:#436dae;border-radius:50%;margin:0 5px 2px 0}
.panel-note{font-size:13px;color:var(--study-muted);margin:8px 0 0}
@media(max-width:950px){.student-column{display:contents}.student-columns{grid-template-columns:minmax(0,1fr);grid-template-areas:"projects" "tasks" "shortcuts" "notices"}}
@media(max-width:560px){
  .student-overview{gap:7px;padding-inline:8px}
  .student-overview-metrics :is(a,button){padding:0 6px}
  .recent-projects,.recent-project:only-child article{grid-template-columns:minmax(0,1fr)}
  .recent-project:only-child .recent-heading{grid-row:auto}
  .student-panel{padding-inline:12px}
}
</style>
