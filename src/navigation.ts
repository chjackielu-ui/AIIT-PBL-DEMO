import type {Role} from './types'

const roots:Record<Role,string[]>={
 student:['/student/dashboard','/student/classes','/student/submissions','/student/tasks','/student/projects','/student/library','/student/personal','/student/portfolio','/student/learning','/student/discussions'],
 teacher:['/teacher/library','/teacher/dashboard','/teacher/classes','/teacher/task-register','/teacher/reviews/projects','/teacher/reviews/achievements','/teacher/discussions'],
 admin:['/admin/dashboard','/admin/users','/admin/grading-rules','/admin/import','/admin/projects','/admin/course-import'],
}
export function navigationRoot(path:string,role:Role) {
 return [...roots[role],'/notifications'].find(root=>path===root || path.startsWith(root+'/'))
}
export function validOrigin(value:unknown,role:Role) {
 return typeof value==='string' && [...roots[role],'/notifications'].includes(value)?value:undefined
}

