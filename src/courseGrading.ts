import type {CourseDimension,GradingRule} from './types'

export function courseDimensions(rule?:GradingRule):CourseDimension[]{
 return rule?.dimensions?.map(d=>({...d})) || [
  {id:'attendance',name:'点名',weight:rule?.attendance ?? 20,source:'manual'},
  {id:'homework',name:'作业',weight:rule?.homework ?? 20,source:'manual'},
  {id:'project',name:'实训进度',weight:rule?.project ?? 60,source:'project'},
 ]
}
export function courseFormula(dimensions:CourseDimension[]){return '总评 = '+dimensions.map(d=>`${d.name || '未命名维度'}成绩 × ${d.weight ?? 0}%`).join(' + ')}
export function courseRuleIssue(dimensions:CourseDimension[]){
 const names=dimensions.map(d=>d.name.trim())
 if(!dimensions.length || dimensions.length>20)return '请设置 1–20 个评分维度'
 if(names.some(n=>!n) || new Set(names).size!==names.length)return '请填写不重复的维度名称'
 if(dimensions.some(d=>typeof d.weight!=='number' || !Number.isFinite(d.weight) || d.weight<0 || d.weight>100))return '各维度权重需为 0–100%'
 if(Math.abs(dimensions.reduce((sum,d)=>sum+d.weight,0)-100)>0.001)return '请将所有维度权重调整至合计 100%'
 return ''
}
