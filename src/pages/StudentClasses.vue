<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {ArrowRight, BookOpen, Library} from 'lucide-vue-next'
import {latestTerm, termLabel} from '../academic'
import {useStore} from '../store'
import CourseCover from '../components/CourseCover.vue'
import PageHead from '../components/PageHead.vue'
import TermFilter from '../components/TermFilter.vue'

const store = useStore()
const state = computed(() => store.state!)
const selected = ref('')
const classes = computed(() => {
  const enrolled = new Set(state.value.enrollments.filter(entry => entry.studentId === state.value.user.id).map(entry => entry.classId))
  return state.value.teachingClasses.filter(teachingClass => enrolled.has(teachingClass.id))
})
const term = latestTerm(classes.value)
const year = ref(term.academicYear)
const semester = ref(term.semester)
watch([year, semester], () => { selected.value = '' })

const courses = computed(() => {
  const byCourse = new Map<string, typeof classes.value>()
  const names = new Map(state.value.users.map(user => [user.id, user.name]))
  const tasks = new Map<string, number>()
  for (const task of state.value.tasks) {
    if (task.status === '已发布') tasks.set(task.classId, (tasks.get(task.classId) || 0) + 1)
  }
  for (const teachingClass of classes.value) {
    if (teachingClass.academicYear !== year.value || teachingClass.semester !== semester.value) continue
    const entries = byCourse.get(teachingClass.courseId)
    if (entries) entries.push(teachingClass)
    else byCourse.set(teachingClass.courseId, [teachingClass])
  }
  return state.value.courses.flatMap(course => {
    const entries = byCourse.get(course.id)
    if (!entries) return []
    return [{
      course,
      classes: entries.map(teachingClass => ({
        ...teachingClass, teachers:teachingClass.teacherIds.map(id => names.get(id)).filter(Boolean).join('、'),
        published:tasks.get(teachingClass.id) || 0,
      })),
    }]
  })
})
const filtered = computed(() => courses.value.filter(({course}) => !selected.value || course.id === selected.value))
const classCount = computed(() => filtered.value.reduce((sum, group) => sum + group.classes.length, 0))
</script>

<template>
  <PageHead title="我的课程与班级">
    <TermFilter :classes="classes" v-model:year="year" v-model:semester="semester" />
  </PageHead>
  <div v-if="courses.length" class="toolbar student-class-toolbar">
    <el-select v-model="selected" clearable aria-label="筛选我的课程" placeholder="全部课程">
      <el-option v-for="{course} in courses" :key="course.id" :value="course.id" :label="course.name" />
    </el-select>
    <span class="muted">{{ filtered.length }} 门课程，{{ classCount }} 个教学班</span>
  </div>
  <div class="student-course-grid">
    <article v-for="entry in filtered" :key="entry.course.id" class="section student-course">
      <RouterLink class="course-heading" :to="{path:'/student/tasks',query:{classId:entry.classes[0].id}}">
        <CourseCover class="course-thumbnail" :course="entry.course" />
        <div class="course-info">
          <div class="course-meta">
            <span>{{ termLabel(year, semester) }}</span>
            <span v-if="entry.course.status" class="course-status">{{ entry.course.status }}</span>
          </div>
          <h2>{{ entry.course.name }}</h2>
          <div v-if="entry.course.plannedStart || entry.course.plannedEnd" class="course-dates">
            <span>计划</span>
            <time v-if="entry.course.plannedStart" :datetime="entry.course.plannedStart">{{ entry.course.plannedStart.replace('T', ' ') }}</time>
            <span v-if="entry.course.plannedStart && entry.course.plannedEnd">至</span>
            <time v-if="entry.course.plannedEnd" :datetime="entry.course.plannedEnd">{{ entry.course.plannedEnd.replace('T', ' ') }}</time>
          </div>
        </div>
      </RouterLink>
      <section v-for="teachingClass in entry.classes" :key="teachingClass.id" class="course-class">
        <h3><RouterLink :to="{path:'/student/tasks',query:{classId:teachingClass.id}}">{{ teachingClass.name }}</RouterLink></h3>
        <p>{{ teachingClass.teachers || '任课教师待定' }} <span>{{ teachingClass.published }} 项已发布任务</span></p>
        <div class="course-links">
          <RouterLink :to="{path:'/student/tasks',query:{classId:teachingClass.id}}"><BookOpen :size="15" aria-hidden="true" />进入课程</RouterLink>
          <RouterLink :to="{path:'/student/tasks',query:{classId:teachingClass.id,module:'resources'}}"><Library :size="15" aria-hidden="true" />班级资料库</RouterLink>
          <RouterLink :to="'/student/classes/' + teachingClass.id + '/details'">课程说明与规则 <ArrowRight :size="14" aria-hidden="true" /></RouterLink>
        </div>
      </section>
    </article>
  </div>
  <el-empty v-if="!filtered.length" :description="classes.length ? '这个学期还没有选课记录，可以切换学年或学期查看。' : '加入教学班后，课程会显示在这里。'" />
</template>

<style scoped>
.student-class-toolbar {gap:12px;margin-bottom:14px}
.student-class-toolbar .el-select {width:min(285px,100%)}
.student-course-grid {
  display:grid;
  grid-template-columns:repeat(auto-fill,minmax(min(365px,100%),1fr));
  align-items:start;
  gap:14px;
}
.student-course {min-width:0;margin:0;padding:0}
.course-heading {
  display:grid;
  grid-template-columns:clamp(68px,23%,112px) minmax(0,1fr);
  align-items:start;
  gap:14px;
  padding:15px;
  border-radius:var(--radius) var(--radius) 0 0;
  background:#f0f5fc;
  color:var(--text);
}
.course-heading:hover {background:var(--selected);text-decoration:none}
.course-heading:hover h2 {color:var(--primary);text-decoration:underline}
.course-heading .course-thumbnail {
  height:62px;
  aspect-ratio:auto;
  border-radius:var(--small-radius);
}
.course-info {min-width:0}
.course-meta,.course-dates {
  display:flex;
  flex-wrap:wrap;
  align-items:center;
  gap:3px 7px;
  color:var(--muted);
  font-size:12px;
}
.course-status {padding:0 6px;border-radius:4px;background:#e0eaf9;color:var(--primary-hover)}
.course-info h2 {margin:3px 0 5px;font-size:17px;line-height:1.4;overflow-wrap:anywhere}
.course-dates time {font:inherit;font-variant-numeric:tabular-nums}
.course-class {padding:12px 15px 14px;border-top:1px solid var(--line);overflow-wrap:anywhere}
.course-class h3 {font-size:15px;margin:0}
.course-class p {
  display:flex;
  flex-wrap:wrap;
  gap:2px 10px;
  margin:3px 0 9px;
  color:var(--muted);
  font-size:13px;
}
.course-links {display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:13px}
.course-links a {
  display:inline-flex;
  align-items:center;
  gap:5px;
  min-height:32px;
  padding:3px 9px;
  border:1px solid var(--line);
  border-radius:var(--small-radius);
  color:var(--primary);
}
.course-links a:hover {background:var(--selected);border-color:#bfcee6;text-decoration:none}
.course-links a:first-child {
  min-height:40px;
  padding:7px 13px;
  border-color:var(--primary);
  background:var(--primary);
  color:white;
  font-weight:600;
}
.course-links a:first-child:hover {background:var(--primary-hover);border-color:var(--primary-hover)}
.student-course a:focus-visible {outline:2px solid var(--primary);outline-offset:2px}
.course-heading:focus-visible {outline-offset:-2px}
</style>
