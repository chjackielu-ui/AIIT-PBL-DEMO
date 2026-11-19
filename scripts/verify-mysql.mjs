import assert from 'node:assert/strict'
import {client} from './lib/api-client.mjs'

assert.equal(process.env.PBL_TEST_RUN, 'isolated', '仅允许对隔离验证库执行写入检查')
const admin = client(), teacher = client(), student = client(), outsider = client()
const login = (c,id,role,password,status=200) => c('/login',{id,role,password},status)
const action = (c,op,data={},status=200) => c('/actions',{op,...data},status)
const adminPassword = process.env.PBL_VERIFY_ADMIN_PASSWORD
assert(adminPassword)
assert.equal((await admin('/health')).storage, 'mysql')
await outsider('/accounts',undefined,404)
await outsider('/state',undefined,401)
await login(admin,'admin','admin',adminPassword)

if (process.argv.includes('--restart')) {
  const state = await admin('/state')
  const project = state.projects.find(p=>p.name==='数据库项目验证')
  assert.equal(project?.status,'已归档')
  assert.equal(project.final.projectScore,88)
  assert.equal(project.messages[0].text,'测试讨论')
  assert.equal(await admin(`/projects/${project.id}/files/${project.files[0].id}`),'数据库附件')
  assert(state.users.some(u=>u.number==='9900112233'))
  console.log('PASS MySQL 重启后保留注册、项目状态、结项评分、讨论与附件')
  process.exit(0)
}

let state = await admin('/state')
assert.equal(state.users.length,7)
for (const key of ['courses','teachingClasses','projects','tasks']) assert.deepEqual(state[key],[])
assert(!JSON.stringify(state).includes('passwordHash'))
await login(student,'9900000001','teacher','学生',401)
await login(teacher,'demo-teacher-1','teacher','学生')
await login(student,'9900000001','student','学生')
await outsider('/register',{number:'9900112233',name:'验证注册',password:'Retest-123'},201)
await outsider('/register',{number:'9900112233',name:'重复注册',password:'Retest-123'},400)
await login(outsider,'9900112233','student','Retest-123')
await outsider('/logout',{})
await outsider('/state',undefined,401)
assert.equal((await teacher('/state')).user.role,'teacher')
console.log('PASS 三角色登录、错误身份、学生注册、学号唯一、退出及空业务库')

const course = {id:'summer26-verify',className:'丛林班',teacherId:'demo-teacher-1',academicYear:'2026',semester:'小学期',
  status:'进行中',plannedStart:'2026-07-08 09:00',plannedEnd:'2026-07-19 17:00',
  gradingRule:{dimensions:[{id:'project',name:'项目',weight:100,source:'project'}]}}
const preview = await admin('/admin/course-import/preview',{rows:[course]})
assert.equal(preview.rows[0].data.courseName,'安信工24级小学期_丛林班_软件_专业技术综合实践_演示教师1')
assert.equal((await admin('/state')).courses.length,0)
await teacher('/admin/course-import',{rows:[course]},403)
await admin('/admin/course-import',{rows:[course,{...course,id:'bad',plannedEnd:'2025-01-01 00:00'}]},400)
assert.equal((await admin('/state')).courses.length,0)
await admin('/admin/course-import',{rows:[course]})
await admin('/admin/course-import',{rows:[course]},400)
const roster = [{number:'9900000001',name:'演示学生1',className:'软件-丛林班'}]
assert.equal((await admin('/admin/roster-import/preview',{rows:roster})).rows[0].error,'')
assert.equal((await admin('/admin/roster-import',{rows:roster})).count,1)
assert.equal((await admin('/admin/roster-import',{rows:roster})).count,0)
assert.equal((await student('/state')).courseGrades[0].rows[0].total,null)
assert.deepEqual((await student('/state')).courseGrades[0].rows[0].scores,{})
assert.equal((await student('/state')).teachingClasses[0].name,'丛林班')
await action(teacher,'createTeachingClass',{},403)
console.log('PASS 课程与教学班批量预览、提交原子性、重复校验、名单复用和未提交成绩留空')

const task = {name:'教师大任务',description:'根任务',classId:course.id,courseId:course.id,teamDue:'2099-11-01',
  due:'2099-11-02',projectType:'individual',min:1,max:1,status:'已发布',milestones:[
    {id:'root',name:'准备',description:'准备',due:'2099-11-02',criteria:'完整',requiresSubmission:false},
    {id:'phase',parentId:'root',name:'阶段验收',description:'提交',due:'2099-11-03',criteria:'达标',requireMaterials:false},
    {id:'final',name:'最终验收',description:'结项',due:'2099-11-04',criteria:'达标',requireMaterials:false,isFinal:true},
  ]}
await action(admin,'setTeacherPermissions',{id:'demo-teacher-1',permissions:[]})
await action(teacher,'saveTask',{task},403)
await action(admin,'setTeacherPermissions',{id:'demo-teacher-1',permissions:['tasks','assessment','discussion']})
await action(student,'saveTask',{task},403)
const taskId = (await action(teacher,'saveTask',{task})).id
state = await student('/state')
const studentId = state.user.id
assert.equal(state.tasks.find(t=>t.id===taskId).milestones[1].parentId,'root')
await action(teacher,'createProject',{taskId},403)
const projectId = (await action(student,'createProject',{taskId,name:'数据库项目验证',description:'实现验证',goal:'验证流程',memberIds:[studentId]})).id
const file = new FormData()
file.append('category','代码')
file.append('file',new Blob(['数据库附件'],{type:'text/plain'}),'fixture.txt')
const uploaded = await student(`/projects/${projectId}/files`,file)
await action(student,'message',{projectId,text:'测试讨论',fileId:uploaded.id})
assert.equal((await teacher(`/projects/${projectId}/discussion`)).messages[0].text,'测试讨论')
await login(outsider,'demo-teacher-2','teacher','学生')
await outsider(`/projects/${projectId}/discussion`,undefined,403)
const submission = {projectId,milestoneId:'phase',description:'首次提交',fileIds:[]}
await action(student,'submitPhase',submission)
let project = (await teacher('/state')).projects[0]
await action(teacher,'reviewPhase',{projectId,submissionId:project.submissions[0].id,score:60,comment:'请重交',pass:false})
await action(student,'submitPhase',{...submission,baseSubmissionId:project.submissions[0].id})
project = (await teacher('/state')).projects[0]
await action(teacher,'reviewPhase',{projectId,submissionId:project.submissions.at(-1).id,score:90,comment:'通过',pass:true})
const final = {summary:'验收',description:'完成',fileIds:[],imageIds:[],contributions:[
  {userId:studentId,role:'开发',description:'实现',evidence:'演示附件',percent:100},
]}
await action(student,'submitFinal',{projectId,final})
await action(teacher,'reviewFinal',{projectId,pass:true,final:{...final,projectScore:88,comment:'通过',
  contributions:final.contributions.map(c=>({...c,score:92,comment:'个人验收通过'}))}})
state = await student('/state')
assert.equal(state.projects[0].status,'已归档')
assert.equal(state.courseGrades[0].rows[0].total,91)
console.log('PASS 教师权限、阶段分支、学生项目、讨论附件、提交退回重交、验收评分与归档')
