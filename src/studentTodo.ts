import type {RouteLocationRaw} from 'vue-router'
import {phaseStatus, projectPhases} from './projectPresentation'
import type {CourseTask, Project} from './types'

export interface TodoRow {
  id: string
  title: string
  project: string
  due: string
  status: string
  done: boolean
  personal?: boolean
  to?: RouteLocationRaw
}

const dueTime = (date: string) => date ? (date.length === 10 ? date + 'T23:59:59' : date.replace(' ', 'T')) : '\uffff'

export function studentTodo(projects: Project[], taskById: Map<string, CourseTask>, userId: string) {
  const rows: TodoRow[] = []
  for (const project of projects) {
    const actionable = ['进行中', '结项待修改'].includes(project.status)
    for (const phase of projectPhases(taskById.get(project.taskId), project)) {
      if (!phase.isFinal && phase.requiresSubmission === false) continue
      const status = phaseStatus(project, phase, userId)
      const done = status === '已通过' || status === '待验收'
      if (!done && (!actionable || !['未提交', '退回修改'].includes(status))) continue
      rows.push({id:project.id + '/phase/' + phase.id, title:phase.name, project:project.name, due:phase.due, status, done,
        to:done
          ? {path:'/student/projects/' + project.id, query:{tab:phase.isFinal ? 'final' : 'milestones'}}
          : {path:`/student/projects/${project.id}/submit/${phase.id}`}})
    }
    for (const item of project.items) {
      if (item.ownerId !== userId) continue
      const done = item.status === '已完成'
      if (!done && !actionable) continue
      rows.push({id:project.id + '/item/' + item.id, title:item.title, project:project.name, due:item.due, status:item.status, done,
        to:{path:'/student/projects/' + project.id, query:{tab:'board', item:item.id}}})
    }
  }
  return rows.sort((a, b) => dueTime(a.due).localeCompare(dueTime(b.due)))
}
