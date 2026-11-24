import type {Course, State} from './types'

interface PlanCourse {
  code: string
  name: string
  credits: number
  term: string
  challenge: string
  level: string
}

// 2022 级软件工程专业培养方案，第 60–61 页。
export const softwareProjectCourses: PlanCourse[] = [
  ['CSE103', '软件工程专业导论', 1.5, '1-1', 'C0', '1L1D'],
  ['CQD101', '创新与创意能力', 2, '1-1', 'PBL', '1L1D'],
  ['CSE205', '计算思维导论（C语言）', 3, '1-1', 'PBL', '1L1D'],
  ['CSE204', '面向对象程序设计（C++）', 3, '1-2', 'PBL', '1L1D'],
  ['CSE501', '计算思维综合训练', 4, '1-3', 'C1', '1L2D'],
  ['CSE202', '数据结构与算法', 3.5, '2-1', 'PBL', '1L1D'],
  ['CSE301', '计算机网络基础', 3.5, '2-1', 'PBL', '2L1D'],
  ['CSE304', '数据库系统', 3.5, '2-1', 'PBL', '2L1D'],
  ['CSE308', '计算机系统基础', 3, '2-1', 'PBL', '2L1D'],
  ['CSE303', '操作系统原理', 3.5, '2-2', 'PBL', '2L1D'],
  ['CSE309', 'WEB前端开发技术I', 3, '2-2', 'PBL', '2L2D'],
  ['CSE502', '专业技术综合实践', 4, '2-3', 'C2', '2L2D'],
  ['CSE310', '软件质量保证与测试', 2, '3-1', 'PBL', '2L2D'],
  ['CSE305', '软件工程', 2.5, '3-1', 'PBL', '3L2D'],
  ['CSE410', '前端开发技术基础', 4, '3-1', 'PBL', '3L2D'],
  ['CSE411', '服务器端开发技术', 4, '3-1', 'PBL', '3L2D'],
  ['CSE412', '前端应用开发与设计', 4, '3-1', 'PBL', '3L3D'],
  ['CSE413', '系统测试方案设计与过程管理', 4, '3-1', 'PBL', '3L2D'],
  ['CSE414', '自动测试设计技术', 4, '3-1', 'PBL', '3L2D'],
  ['CSE415', '软件测试高级技术', 4, '3-1', 'PBL', '3L3D'],
  ['CSE416', '软件开发基础', 4, '3-1', 'PBL', '3L2D'],
  ['CSE417', '数据库与Linux安全技术', 4, '3-1', 'PBL', '3L2D'],
  ['CSE418', '高级运维技术应用与开发', 4, '3-1', 'PBL', '3L3D'],
  ['CSE419', '智能软件开发技术基础', 4, '3-1', 'PBL', '3L2D'],
  ['CSE420', '智能软件开发应用与数据采集技术', 4, '3-1', 'PBL', '3L2D'],
  ['CSE421', 'Python人工智能开发技术', 4, '3-1', 'PBL', '3L3D'],
  ['IPC501', '企业项目综合实践', 4, '3-2', 'C3', '3L4D'],
  ['CSE598', '企业实习', 6, '4-1', 'C4', '4L4D'],
  ['CSE599', '毕业设计（论文）', 10, '4-2', 'C4', '4L4D'],
].map(([code, name, credits, term, challenge, level]) => ({
  code: String(code), name: String(name), credits: Number(credits),
  term: String(term), challenge: String(challenge), level: String(level),
}))

const normalize = (value:string) => value.normalize('NFKC').replace(/\s+/g, '').toLowerCase()
const aliases: Record<string,string[]> = {
  CSE205: ['计算机思维导论（C语言）', 'C语言'],
  CSE501: ['计算机思维综合训练'],
  CSE502: ['专业技术综合实验'],
}

export function curriculumCourse(title:string) {
  const name = normalize(title.match(/(?:^|_)软件_([^_]+)(?:_|$)/)?.[1] || title)
  return softwareProjectCourses.find(course =>
    [course.name, course.code, ...(aliases[course.code] || [])].some(label => name === normalize(label)))
}

export function softwareCohort(state:State) {
  const cls = state.classes.find(item => item.id === state.user.classId)
  const softwareClass = !cls?.major || ['软件', '软件工程'].includes(cls.major.trim())
  if (!softwareClass) return undefined
  const shortYear = cls?.name.match(/软件(?:工程)?[\s-]*(20)?(22|24)(?=级|\d{2}班)/)?.[2]
  if (shortYear) return Number('20' + shortYear)
  const importedYears = new Set(state.teachingClasses.filter(teachingClass =>
    state.enrollments.some(entry => entry.studentId === state.user.id && entry.classId === teachingClass.id))
    .map(teachingClass => state.courses.find(course => course.id === teachingClass.courseId)?.name
      .match(/(?:^|_)安信工(22|24)级[^_]*_.*_软件_/)?.[1]).filter(Boolean))
  return importedYears.size === 1 ? Number('20' + [...importedYears][0]) : undefined
}

export function courseCredits(course:Course|undefined, cohort:number|undefined) {
  if (course?.credits != null && Number.isFinite(course.credits) && course.credits >= 0) return course.credits
  const plan = course && curriculumCourse(course.name)
  // The 2024 transcript confirms credits for these courses independently of the 2022 plan.
  const confirmedFor2024 = plan && ['CSE205', 'CSE204', 'CSE501', 'CSE202', 'CSE301', 'CSE304', 'CSE308', 'CSE303', 'CSE309', 'CSE502', 'CQD101', 'CSE103'].includes(plan.code)
  return cohort === 2022 || (cohort === 2024 && confirmedFor2024) ? plan?.credits : undefined
}

export function graduationCredits(cohort:number|undefined) {
  return cohort === 2022 ? 174 : cohort === 2024 ? 168 : undefined
}
