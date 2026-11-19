import type {CourseTask} from './types'
export const isIndividual=(task?:CourseTask)=>task?.projectType==='individual' || (!task?.projectType && task?.min===1 && task?.max===1)
export const taskModeName=(task?:CourseTask)=>task?.taskKind==='exercise'?'小练习':isIndividual(task)?'个人项目':'小组集体项目'
