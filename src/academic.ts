import type {TeachingClass, State} from './types'

/** 当前学年学期（Asia/Shanghai 时区；8 月起为新学年，2–7 月为第二学期）。全站唯一实现。 */
export function currentTerm() {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone: 'Asia/Shanghai', year: 'numeric', month: 'numeric'}).formatToParts(new Date())
  const y = Number(parts.find(p => p.type === 'year')!.value)
  const m = Number(parts.find(p => p.type === 'month')!.value)
  const start = m >= 8 ? y : y - 1
  return {academicYear: `${start}-${start + 1}`, semester: m >= 2 && m <= 7 ? '第二学期' : '第一学期'}
}

export const semesters: readonly string[] = ['第一学期', '第二学期', '第三学期']
export const termOrder = (semester: string) => semesters.indexOf(semester)

/** 最新可用学年学期：优先返回与当前学期相同的班级；否则取排序最新（未来学年班级不遮蔽当前学期）。 */
export function latestTerm(classes: TeachingClass[]) {
  const now = currentTerm()
  const current = classes.find(c => c.academicYear === now.academicYear && c.semester === now.semester)
  if (current) return now
  return [...classes].sort((a, b) =>
    b.academicYear.localeCompare(a.academicYear) || termOrder(b.semester) - termOrder(a.semester)
  )[0] || now
}

export const colleges: Record<string, string[]> = {
  '计算机与软件工程学院': ['软件', '计科', '网络'],
  '大数据与人工智能学院': ['人工智能', '大数据', '智科'],
}

/** 学生相关教学班：选课班级或已有项目所在班级；教师/管理员为全部（服务端已按权限过滤）。 */
export function relevantClasses(s: State) {
  return s.user.role === 'student'
    ? s.teachingClasses.filter(c =>
        s.enrollments.some(e => e.studentId === s.user.id && e.classId === c.id)
        || s.projects.some(p => s.tasks.some(t => t.id === p.taskId && t.classId === c.id)))
    : s.teachingClasses
}
