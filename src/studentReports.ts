import {courseCredits, curriculumCourse, graduationCredits, softwareCohort} from './curriculum'
import type {State} from './types'
import {termOrder} from './academic'

export function studentTranscript(state:State, classId?:string) {
  const account = state.user
  const administrativeClass = state.classes.find(cls => cls.id === account.classId)
  const cohort = softwareCohort(state)
  const rows = (state.courseGrades || []).flatMap(grade => {
    if (classId && classId !== grade.classId) return []
    const score = grade.rows.find(row => row.studentId === account.id)
    const teachingClass = state.teachingClasses.find(cls => cls.id === grade.classId)
    if (!score || !teachingClass) return []
    const course = state.courses.find(course => course.id === teachingClass.courseId)
    const plan = course && curriculumCourse(course.name)
    return [{
      classId: grade.classId,
      courseKey: plan?.code || teachingClass.courseId,
      courseCode: plan?.code,
      courseName: plan?.name || course?.name || teachingClass.name,
      teachingClass: teachingClass.name,
      academicYear: teachingClass.academicYear,
      semester: teachingClass.semester,
      credits: courseCredits(course, cohort),
      score: score.total,
    }]
  }).sort((a,b) => b.academicYear.localeCompare(a.academicYear) || termOrder(b.semester) - termOrder(a.semester) || a.courseName.localeCompare(b.courseName, 'zh-CN'))
  const totalByCourse = new Map<string,number>()
  const passedByCourse = new Map<string,number>()
  for (const row of rows) {
    if (row.credits == null) continue
    totalByCourse.set(row.courseKey, Math.max(row.credits, totalByCourse.get(row.courseKey) ?? 0))
    if (row.score != null && row.score >= 60)
      passedByCourse.set(row.courseKey, Math.max(row.credits, passedByCourse.get(row.courseKey) ?? 0))
  }
  return {
    name: account.name,
    number: account.number,
    className: administrativeClass?.name || [...new Set(rows.map(row => row.teachingClass))].join('、'),
    major: administrativeClass?.major || (cohort ? '软件工程' : undefined),
    college: administrativeClass?.college,
    cohort,
    graduation: graduationCredits(cohort),
    total: totalByCourse.size ? [...totalByCourse.values()].reduce((sum, credits) => sum + credits, 0) : undefined,
    earned: totalByCourse.size ? [...passedByCourse.values()].reduce((sum, credits) => sum + credits, 0) : undefined,
    unknownCredits: new Set(rows.filter(row => row.credits == null && !totalByCourse.has(row.courseKey)).map(row => row.courseKey)).size,
    rows,
  }
}

export const learningIndicators = [
  {key:'completionRate',title:'任务通过率',unit:'%',formula:'本人已通过的任务数 ÷ 该教学班已发布的需提交任务总数 × 100。未参与项目的任务也计入分母。',source:'教师验收结果'},
  {key:'submissionRate',title:'任务提交率',unit:'%',formula:'本人已有提交记录的任务数 ÷ 需提交任务总数 × 100。整组提交对每位成员计一次。',source:'最近提交记录'},
  {key:'onTimeRate',title:'按时提交率',unit:'%',formula:'最近版本在截止日及之前提交的任务数 ÷ 有有效提交时间的任务数 × 100。缺少时间的历史材料不参与计算。',source:'提交日期与截止日期'},
  {key:'averageScore',title:'已通过任务均分',unit:'分',formula:'本人已通过且有教师评分的任务分数之和 ÷ 这些任务数。分别提交取本人分数，项目验收取教师确认的个人成绩。这是等权学习诊断均分，项目成绩另按任务权重计算。',source:'教师评分'},
  {key:'uploads',title:'资料上传份数',unit:'份',formula:'本人在该教学班项目中上传并保留在资料库的文件数。',source:'资料库上传记录'},
] as const

export function studentLearningRows(state:State, classId?:string) {
  return (state.learningAnalytics || []).filter(row => !classId || row.classId === classId).map(row => {
    const tc = state.teachingClasses.find(item => item.id === row.classId)
    const course = state.courses.find(item => item.id === tc?.courseId)
    return {
      ...row, courseName: (course && curriculumCourse(course.name)?.name) || course?.name || '课程',
      teachingClass: tc?.name || row.classId, term: [tc?.academicYear, tc?.semester].filter(Boolean).join(' '),
    }
  })
}
