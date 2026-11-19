import assert from 'node:assert/strict'
import {client} from './lib/api-client.mjs'
const admin=client()
await admin('/login',{id:'a1'})
const state=await admin('/state')
const p=state.projects.find(p=>p.name==='验收项目修订'&&p.final?.approved&&p.files.some(f=>f.name==='Demo.java'))
assert(p,'重启前的项目仍存在');assert.equal(p.messages[0].text,'@李可欣 请确认代码');assert.equal(p.items[0].status,'已完成');assert.equal(p.final.contributions[0].score,95);assert(p.files.some(f=>f.name==='Demo.java'))
const file=p.files.find(f=>f.name==='Demo.java');const content=await admin(`/projects/${p.id}/files/${file.id}`);assert.equal(content,'public class Demo {}');assert(state.users.some(u=>u.name==='导入学生'))
console.log('PASS Java 重启后保留项目、任务状态、消息、评分、导入数据及文件原始内容')
