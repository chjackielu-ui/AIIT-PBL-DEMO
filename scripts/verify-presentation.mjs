import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
const code=ts.transpileModule(await fs.readFile('src/projectPresentation.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText
const {pendingSubmissions,phaseStatus,phaseScore,projectCourseScore}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
const milestone={id:'current',submissionMode:'individual',requiresSubmission:true,scoreWeight:1}
const project={status:'进行中',memberIds:['one','two'],submissions:[
 {id:'deleted',milestoneId:'deleted',status:'待验收'},
 {id:'old-mode',milestoneId:'current',submissionMode:'shared',status:'待验收'},
 {id:'one-v1',milestoneId:'current',submissionMode:'individual',submitterId:'one',status:'已更新'},
 {id:'one-v2',milestoneId:'current',submissionMode:'individual',submitterId:'one',status:'待验收'},
 {id:'two-v1',milestoneId:'current',submissionMode:'individual',submitterId:'two',status:'已通过',score:80},
]}
assert.deepEqual(pendingSubmissions(project,[milestone]).map(s=>s.id),['one-v2'])
assert.equal(pendingSubmissions(project,[]).length,0)
assert.equal(phaseStatus(project,milestone,'two'),'已通过')
assert.equal(phaseStatus(project,milestone),'待验收')
assert.equal(phaseScore(project,milestone,'two'),80)
assert.equal(phaseScore(project,milestone),undefined)
project.submissions[3].status='已通过';project.submissions[3].score=100
assert.equal(phaseScore(project,milestone),90)
assert.equal(phaseStatus(project,milestone),'已通过')
project.final={approved:true,projectScore:60,contributions:[{userId:'one',score:75}]}
assert.equal(phaseScore(project,{isFinal:true},'one'),75)
assert.equal(projectCourseScore(project,[milestone,{id:'final',isFinal:true,scoreWeight:3}]),67.5)
project.status='已归档';assert.equal(pendingSubmissions(project,[milestone]).length,0)
console.log('PASS 展示计算：删除任务与旧模式不计待批、成员成绩隔离、全组状态、个人验收成绩、任务权重汇总')
