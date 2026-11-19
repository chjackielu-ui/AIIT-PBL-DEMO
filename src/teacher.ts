import type {State} from './types'
import {pendingSubmissions} from './projectPresentation'

/** 教师任教的全部教学班级（服务端已按任教关系过滤）。 */
export function teachingClasses(s: State) {
  return s.teachingClasses.filter(c => c.teacherIds.includes(s.user.id))
}

/** 班级指标：待办（申请+阶段+结项）、项目、学生数；classId 为空时汇总全部任教班级。 */
export function classMetrics(s: State, classId?: string) {
  const classIds = new Set(teachingClasses(s).filter(c => !classId || c.id === classId).map(c => c.id))
  const tasks = s.tasks.filter(t => classIds.has(t.classId))
  const taskIds = new Set(tasks.map(t => t.id))
  const projects = s.projects.filter(p => taskIds.has(p.taskId))
  const applications = projects.filter(p => p.status === '待审核').length
  const stages = projects.reduce((n, p) => n + pendingSubmissions(p,tasks.find(t=>t.id===p.taskId)?.milestones || []).length, 0)
  const grading = projects.filter(p => p.status === '结项待审核').length
  const archived = projects.filter(p => p.status === '已归档').length
  return {
    tasks,
    projects,
    applications,
    stages,
    grading,
    archived,
    pending: applications + stages + grading,
    students: new Set(s.enrollments.filter(e => classIds.has(e.classId)).map(e => e.studentId)).size,
  }
}

export function classPath(classId: string) {
  return `/teacher/classes/${classId}`
}

export function taskPath(classId: string, id: string) {
  return `${classPath(classId)}/tasks/${id}`
}

export function projectPath(s: State, id: string, tab = '') {
  const p = s.projects.find(p => p.id === id)
  const t = s.tasks.find(t => t.id === p?.taskId)
  return t ? `${taskPath(t.classId,t.id)}?project=${encodeURIComponent(id)}${tab ? '&sourceTab=' + encodeURIComponent(tab) : ''}` : '/teacher/classes'
}

/** 班级上下文入口（进入班级后由页面使用）。 */
export type ClassSection = 'tasks' | 'projects' | 'reviews/projects' | 'reviews/milestones' | 'reviews/final'

export function classSection(classId: string, section: ClassSection) {
  return `${classPath(classId)}/${section}`
}

/** 教学班级选择页意图：标题、说明与卡片目标区块。 */
export type ClassIntent = 'publish' | 'projects' | 'milestones' | 'final' | 'all'

export const classIntents: Record<ClassIntent, {title: string; hint: string; destination: string}> = {
  publish: {title: '选择发布任务的教学班级', hint: '选择教学班级发布实训任务。', destination: '/tasks/create'},
  projects: {title: '选择教学班级 · 申请审核', hint: '项目申请按其课程任务所属教学班级归集。', destination: '/reviews/projects'},
  milestones: {title: '选择教学班级 · 阶段验收', hint: '进入班级后查看待验收材料和提交历史。', destination: '/reviews/milestones'},
  final: {title: '选择教学班级 · 结项评分', hint: '进入班级后确认贡献、个人成绩和项目总评。', destination: '/reviews/final'},
  all: {title: '选择教学班级 · 项目与资料', hint: '查看班级内的项目进度、当前资料和成果。', destination: '/projects'},
}

export function intentPath(kind: ClassIntent) {
  if (kind === 'projects') return '/teacher/reviews/projects'
  if (kind === 'milestones' || kind === 'final') return '/teacher/reviews/achievements' + (kind === 'final' ? '?type=final' : '')
  return `/teacher/classes?intent=${kind}`
}

/** 合法审核视图类型，兼容旧审核入口与返回地址。 */
export const reviewKinds = ['projects', 'milestones', 'final'] as const
export type ReviewKind = typeof reviewKinds[number]

export function isReviewKind(value: unknown): value is ReviewKind {
  return typeof value === 'string' && (reviewKinds as readonly string[]).includes(value)
}

export function classIntentOf(value: unknown): ClassIntent | undefined {
  return typeof value === 'string' && Object.prototype.hasOwnProperty.call(classIntents, value) ? value as ClassIntent : undefined
}
