import assert from 'node:assert/strict'
import {client as createClient} from './lib/api-client.mjs'
function client(id) {const call=createClient();return {call,login:()=>call('/login',{id})}}
const teacher=client('t1'),one=client('s1'),two=client('s2'),outside=client('s6')
await Promise.all([teacher.login(),one.login(),two.login(),outside.login()])
const act=(c,op,data,status)=>c.call('/actions',{op,...data},status)
const rubric=[{id:'answer',dimension:'答辩效果',title:'问题回答',description:'准确清楚',maxPoints:25},{id:'design',dimension:'项目内涵',title:'设计依据',description:'方案合理',maxPoints:25}]
const task={id:'',name:'学生升级 HTTP 验收',classId:'tc1',courseId:'c1',taskKind:'training',projectType:'group',description:'验证真实提交与评分',min:2,max:5,crossClass:false,teamDue:'2099-01-01',due:'2099-01-02',status:'已发布',milestones:[{id:'work',name:'汇报',description:'提交 PPT',due:'2099-02-01',criteria:'满足目标',submissionMode:'individual',requireMaterials:true,scoreWeight:10,rubric,deliverables:[{id:'slides',title:'PPT',extensions:['.ppt','.pptx'],minFiles:1,maxFiles:2,required:true}]}]}
task.id=(await act(teacher,'saveTask',{task})).id
const pid=(await act(one,'createProject',{taskId:task.id,name:'成员独立提交验收',description:'验证材料版本',goal:'评分可追溯',memberIds:['s1','s2']})).id
await act(teacher,'reviewProject',{projectId:pid,pass:true,comment:'通过申请'})
const data=new FormData();data.append('category','文档');data.append('file',new Blob(['isolated fixture'],{type:'application/octet-stream'}),'report.pptx')
const file=await two.call(`/projects/${pid}/files`,data)
const submit={projectId:pid,milestoneId:'work',description:'我的 PPT',fileIds:[file.id],deliverables:{slides:[file.id]}}
await act(outside,'submitPhase',submit,403)
await act(two,'submitPhase',{...submit,deliverables:{}},400)
await act(two,'submitPhase',submit)
await act(one,'submitPhase',submit)
let project=(await teacher.call('/state')).projects.find(p=>p.id===pid)
assert.equal(project.submissions.length,2)
const member=project.submissions.find(s=>s.submitterId==='s2')
await act(two,'submitPhase',{...submit,baseSubmissionId:'stale'},400)
await act(two,'submitPhase',{...submit,baseSubmissionId:member.id,description:'新版 PPT'})
project=(await teacher.call('/state')).projects.find(p=>p.id===pid)
assert.equal(project.submissions.find(s=>s.id===member.id).status,'已更新')
const latest=project.submissions.at(-1)
const grading={rubric,reviewers:[{name:'教师',weight:70,points:{answer:20,design:25}},{name:'专家',weight:30,points:{answer:15,design:20}}]}
await act(teacher,'reviewPhase',{projectId:pid,submissionId:member.id,pass:true,grading,comment:'旧版'},400)
await act(teacher,'reviewPhase',{projectId:pid,submissionId:latest.id,pass:true,grading:{...grading,rubric:[]},comment:'错误规则'},400)
await act(teacher,'reviewPhase',{projectId:pid,submissionId:latest.id,pass:true,grading,score:100,comment:'按量表确认'})
project=(await two.call('/state')).projects.find(p=>p.id===pid)
assert.equal(project.submissions.at(-1).score,84)
assert.equal(project.submissions.at(-1).grading.reviewers.length,2)
assert.equal(project.submissions.find(s=>s.submitterId==='s1').status,'待验收')
const stats=(await two.call('/state')).learningAnalytics.find(c=>c.classId==='tc1')
assert(stats.mine.scored>=1);assert(stats.samples.averageScore>=1);assert(!JSON.stringify(stats).includes('s1'))
assert(!(await outside.call('/state')).projects.some(p=>p.id===pid))
console.log('PASS HTTP：任意成员上传、分别提交、清单校验、版本冲突、旧版禁止批改、服务端量表重算、规则快照、匿名学情汇总')
console.log(JSON.stringify({taskId:task.id,projectId:pid,milestoneId:'work'}))
