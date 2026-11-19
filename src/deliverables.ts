import type {DeliverableRequirement} from './types'

export function deliverableFiles(selected:Record<string,string[]>) {
  return [...new Set(Object.values(selected).flat())]
}

export function validateDeliverables(rules:DeliverableRequirement[], selected:Record<string,string[]>) {
  for(const rule of rules){
    const count=selected[rule.id]?.length || 0
    if((rule.required || count>0) && count<rule.minFiles)throw Error(`${rule.title}：至少需要 ${rule.minFiles} 份文件`)
    if(count>rule.maxFiles)throw Error(`${rule.title}：最多选择 ${rule.maxFiles} 份文件`)
  }
}

export function submissionAttachmentIssue(rules:DeliverableRequirement[],selected:Record<string,string[]>,fileIds:string[],required:boolean){
  try{validateDeliverables(rules,selected)}catch(error){return (error as Error).message}
  return required && !fileIds.length?'本任务必须提交附件，请上传并选齐必交材料后再提交。':''
}
