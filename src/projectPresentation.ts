import type {Contribution, Milestone, Project, State, Submission} from './types'

/** 阶段验收截止：取任务全部阶段（含最终验收）的最晚截止日期；异常数据回退任务级申请截止。 */
export function projectDeadline(s:State, p:Project) {
  return s.tasks.find(t => t.id === p.taskId)?.milestones.map(m => m.due).sort().at(-1)
    || s.tasks.find(t => t.id === p.taskId)?.due
    || '未设置'
}

/** 项目进度：完成的工作项 + 已验收通过的非最终阶段，占全部工作项与阶段的比例；已归档为 100%。
 *  最终验收阶段在提交结项（p.final 存在）时计入完成，口径与 README「工作项+阶段验收状态计算」一致。 */
export function projectProgress(s:State, p:Project) {
  if (p.status === '已归档') return 100
  const phases = s.tasks.find(t => t.id === p.taskId)?.milestones.filter(m => m.isFinal || m.requiresSubmission !== false) || []
  const accepted = phases.filter(m =>
    m.isFinal
      ? !!p.final
      : phaseStatus(p, m) === '已通过'
  ).length
  const total = p.items.length + phases.length
  return total ? Math.round((p.items.filter(i => i.status === '已完成').length + accepted) / total * 100) : 0
}

/** 某阶段的最近一次提交；没有提交记录时返回 undefined。 */
export function latestSubmission(p: Project, milestoneId: string, memberId?: string, mode?: Milestone['submissionMode']): Submission | undefined {
  return p.submissions.filter(s => s.milestoneId === milestoneId
    && (!mode || (s.submissionMode || 'shared') === mode)
    && (!memberId || s.submitterId === memberId)).at(-1)
}

export function phaseSubmission(p:Project, m:Milestone, memberId?:string) {
  return latestSubmission(p,m.id,m.submissionMode==='individual'?memberId:undefined,m.submissionMode || 'shared')
}

/** 只统计当前仍需批改的最新版本，已删除任务及旧提交方式的记录保留为历史。 */
export function pendingSubmissions(p:Project,milestones:Milestone[]) {
  if(!['进行中','结项待修改'].includes(p.status))return []
  return milestones.filter(m=>!m.isFinal && m.requiresSubmission!==false).flatMap(m=>
    (m.submissionMode==='individual'?p.memberIds:[undefined]).map(id=>phaseSubmission(p,m,id))
  ).filter((sub):sub is Submission=>sub?.status==='待验收')
}

/** 阶段（含最终验收）在项目中的当前状态，统一三处展示的文案与判定顺序：
 *  最终阶段：结项待审核→待验收、结项待修改→退回修改、final 已通过→已通过，否则未提交；
 *  普通阶段：最近一次提交状态，无提交为未提交。 */
export function phaseStatus(p: Project, m: Milestone, memberId?:string): string {
  if (!m.isFinal && m.requiresSubmission === false) return '无需提交'
  if (m.isFinal) {
    if (p.status === '结项待审核') return '待验收'
    if (p.status === '结项待修改') return '退回修改'
    if (p.final?.approved) return '已通过'
    return '未提交'
  }
  if (m.submissionMode === 'individual' && !memberId) {
    const statuses=p.memberIds.map(id=>phaseSubmission(p,m,id)?.status || '未提交')
    if(statuses.every(s=>s==='已通过'))return '已通过'
    if(statuses.includes('退回修改'))return '退回修改'
    if(statuses.includes('待验收'))return '待验收'
    return '未提交'
  }
  return phaseSubmission(p, m, memberId)?.status || '未提交'
}

/** 阶段分数：最终阶段取项目总评，普通阶段取最近一次提交分数；未评分为 undefined。 */
export function phaseScore(p: Project, m: Milestone, memberId?:string): number | undefined {
  if (m.isFinal) return memberId ? p.final?.contributions.find(c=>c.userId===memberId)?.score : p.final?.projectScore
  if(m.submissionMode==='individual' && !memberId){
    const scores=p.memberIds.map(id=>phaseSubmission(p,m,id)?.score)
    return scores.length && scores.every((score):score is number=>typeof score==='number') ? Math.round(scores.reduce((a,b)=>a+b,0)/scores.length*100)/100 : undefined
  }
  return phaseSubmission(p, m, memberId)?.score
}

/** 需要本人提交或重交的任务数；教师待批材料不计入学生待办。 */
export function pendingSubmissionCount(s:State,p:Project,userId?:string) {
  if(!['进行中','结项待修改'].includes(p.status))return 0
  return (s.tasks.find(t=>t.id===p.taskId)?.milestones || []).filter(m=>(m.isFinal || m.requiresSubmission!==false)
    && ['未提交','退回修改'].includes(phaseStatus(p,m,userId))).length
}

/** 看板待办包含需提交任务与本人未完成工作项。 */
export function pendingTaskCount(s:State,p:Project,userId?:string) {
  if(!['进行中','结项待修改'].includes(p.status))return 0
  return pendingSubmissionCount(s,p,userId)+p.items.filter(i=>i.status!=='已完成' && (!userId || i.ownerId===userId)).length
}

/** 最终验收阶段的状态徽章（与 phaseStatus 的 final 分支一致，供无 Milestone 对象的场景使用）。 */
export function finalStageStatus(p: Project): string {
  if (p.status === '结项待审核') return '待验收'
  if (p.status === '结项待修改') return '退回修改'
  if (p.final?.approved) return '已通过'
  return '未提交'
}

/** 项目协作只读状态集合（申请锁定 / 退回修改 / 归档 / 关闭）。 */
export const lockedStatuses = ['待审核', '待修改', '已归档', '已关闭'] as const

/** 项目展示模型：打印页与公开分享页共用同一组白名单字段（与后端 publicProject 对齐）。 */
export function projectPresentation(s: State, p: Project) {
  const t = s.tasks.find(t => t.id === p.taskId)
  const final = p.final?.approved ? p.final : undefined
  return {
    name: p.name,
    description: p.description,
    goal: p.goal,
    tags: p.tags,
    created: p.created,
    status: p.status,
    course: s.courses.find(c => c.id === t?.courseId)?.name,
    teachingClass: s.teachingClasses.find(c => c.id === t?.classId)?.name,
    members: p.contributions.map(c => ({
      name: s.users.find(u => u.id === c.userId)?.name || c.userId,
      role: c.role,
    })),
    summary: final?.summary,
    result: final?.description,
    comment: final?.comment,
    link: final?.link,
    images: final?.imageIds.map(id => ({id, name: p.files.find(f => f.id === id)?.name || id})) || [],
  }
}

export interface PortfolioRecord {
  id: string
  name: string
  year: string
  date: string
  course: string
  teachingClass: string
  role: string
  description: string
  contribution: string
  summary: string
  score?: number
  comment: string
  status: string
  history: boolean
}

/** 个人档案记录：项目经历（职责取 contribution.role、贡献取 description，与成员表的字段语义一致）
 *  与历史补录合并，按日期倒序。档案页与打印页共用。 */
export function portfolioRecords(s: State, userId: string): PortfolioRecord[] {
  const projects = s.projects.map(p => {
    const c: Contribution | undefined = p.contributions.find(c => c.userId === userId)
    const t = s.tasks.find(t => t.id === p.taskId)
    const tc = s.teachingClasses.find(c => c.id === t?.classId)
    return {
      id: p.id,
      name: p.name,
      year: (tc?.academicYear || s.teachingClasses[0]?.academicYear || '').replace('-', '–'),
      date: p.created,
      course: s.courses.find(co => co.id === t?.courseId)?.name || '',
      teachingClass: tc?.name || '',
      role: c?.role || '成员',
      description: c?.description || '尚未填写职责',
      contribution: c?.description || '',
      summary: p.final?.summary ?? p.description,
      score: c?.score,
      comment: c?.comment || '',
      status: p.status,
      history: false,
    }
  })
  const history = s.history.filter(h => h.userId === userId).map(h => ({
    ...h,
    course: '历史补录 · 未经教师认证',
    teachingClass: '',
    contribution: h.contribution,
    score: undefined as number | undefined,
    comment: '',
    status: '历史补录',
    history: true,
  }))
  return [...projects, ...history].sort((a, b) => b.date.localeCompare(a.date))
}

export function projectCourseScore(p: Project, milestones: Milestone[], memberId?: string) {
  const required = milestones.filter(m => m.isFinal || m.requiresSubmission !== false)
  const parts = required.map(m => ({
    weight: m.scoreWeight ?? 1, score: phaseStatus(p,m,memberId) === '已通过' ? phaseScore(p,m,memberId) : undefined,
  }))
  if (!parts.some(p => p.score !== undefined)) return undefined
  const totalWeight = parts.reduce((sum,m) => sum + m.weight, 0)
  const weighted = parts.reduce((sum,m) => sum + (m.score ?? 0) * m.weight, 0)
  return totalWeight > 0 ? Math.round(weighted / totalWeight * 100) / 100 : undefined
}
