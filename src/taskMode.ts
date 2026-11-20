import type {CourseTask} from './types'
export const isIndividual=(task?:CourseTask)=>task?.projectType==='individual' || (!task?.projectType && task?.min===1 && task?.max===1)
export const taskModeName=(task?:CourseTask)=>task?.taskKind==='exercise'?'班级任务':isIndividual(task)?'个人项目':'小组集体项目'

const clock = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
})
const now = () => clock.format(new Date()).replace(' ', 'T')
export const taskStarted = (task?: CourseTask) =>
  !task?.startAt || now() >= task.startAt.replace(' ', 'T')
export function beforeDeadline(value?: string) {
  if (!value) return false
  const due = value.replace(' ', 'T')
  return now() <= (due.length === 10 ? due + 'T23:59:59' : due.length === 16 ? due + ':00' : due)
}
export function canStartTask(task: CourseTask) {
  if (task.status !== '已发布' || !taskStarted(task)) return false
  return task.taskKind === 'exercise'
    ? beforeDeadline(task.milestones[0]?.due || task.due)
    : beforeDeadline(task.due) && (!!task.startAt || beforeDeadline(task.teamDue))
}
