import type {GradingDetails,RubricPoint,ScoreReviewer} from './types'

export const newReviewer=(name=''):ScoreReviewer=>({name,weight:100,score:undefined,points:{},comment:''})
export function calculateGrade(grade:GradingDetails,rubric:RubricPoint[]) {
 if(!grade.reviewers.length)throw Error('至少需要一位评分人员')
 if(Math.abs(grade.reviewers.reduce((sum,r)=>sum+(r.weight || 0),0)-100)>0.001)throw Error('评分人员权重合计必须为 100%')
 const names=new Set<string>(),maximum=rubric.reduce((sum,p)=>sum+p.maxPoints,0)
 let total=0
 for(const r of grade.reviewers){
  if(!r.name.trim() || names.has(r.name.trim()))throw Error('评分人员不能为空或重复')
  names.add(r.name.trim())
  if(!Number.isFinite(r.weight) || r.weight<=0 || r.weight>100)throw Error('评分人员权重需大于 0 且不超过 100')
  let value=r.score
  if(rubric.length){
   let points=0
   for(const p of rubric){const n=r.points[p.id];if(typeof n!=='number' || !Number.isFinite(n) || n<0 || n>p.maxPoints)throw Error(`${p.title}：请填写 0–${p.maxPoints} 的分数`);points+=n}
   value=points/maximum*100
  }
  if(typeof value!=='number' || !Number.isFinite(value) || value<0 || value>100)throw Error('请填写 0–100 的分数')
  total+=value*r.weight/100
 }
 return Math.round(total*100)/100
}
