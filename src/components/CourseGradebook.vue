<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useStore} from '../store'

import {run} from '../lib'
import {courseFormula} from '../courseGrading'
const props=defineProps<{classId:string}>(),store=useStore()
const grade=computed(()=>store.state?.courseGrades?.find(g=>g.classId===props.classId))
const student=computed(()=>store.user?.role==='student'),teacher=computed(()=>store.user?.role==='teacher')
const scores=ref<Record<string,Record<string,number|null>>>({}),busy=ref(false)
watch(grade,g=>{scores.value=Object.fromEntries((g?.rows || []).map(r=>[r.studentId,Object.fromEntries((g?.dimensions || []).filter(d=>d.source==='manual').map(d=>[d.id,r.scores[d.id] ?? null]))]))},{immediate:true})
function total(sid:string){
 let value=0
 for(const d of grade.value?.dimensions || []){
  const score=d.source==='project'?grade.value?.rows.find(r=>r.studentId===sid)?.scores[d.id]:scores.value[sid]?.[d.id]
  if(score==null && d.weight>0)return null
  value+=(score ?? 0)*d.weight/100
 }
 return Math.round(value*100)/100
}
async function save(){busy.value=true;await run(()=>store.act('saveCourseGrades',{classId:props.classId,dimensions:grade.value?.dimensions,scores:scores.value}),'课程成绩已保存');busy.value=false}
</script>
<template><section v-if="grade" class="section course-gradebook"><div class="section-head"><h2>{{student?'我的课程成绩':'班级课程成绩'}}</h2><el-button v-if="teacher && grade.rows.length" type="primary" :loading="busy" @click="save">保存课程成绩</el-button></div><p class="muted">{{courseFormula(grade.dimensions)}}。各项均为百分制；未录入的成绩显示“待录入”，不直接按零分计算总评。</p><p v-if="grade.dimensions.some(d=>d.source==='project')" class="muted">实训进度按本班已发布任务的个人项目成绩取平均；没有已通过的评分时，该维度留空。新增维度后，成绩列与总评公式会同步更新。</p><el-table :data="grade.rows"><el-table-column v-if="!student" label="学生" width="120" fixed><template #default="{row}">{{store.state?.users.find(u=>u.id===row.studentId)?.name || row.studentId}}</template></el-table-column><el-table-column v-for="d in grade.dimensions" :key="d.id" :label="`${d.name}（${d.weight}%）`" min-width="185"><template #default="{row}"><el-input-number v-if="teacher && d.source==='manual'" v-model="scores[row.studentId][d.id]" :min="0" :max="100" :precision="2" :controls="false" :aria-label="`${store.state?.users.find(u=>u.id===row.studentId)?.name}的${d.name}成绩`" placeholder="待录入" /><span v-else>{{row.scores[d.id] ?? (d.source==='project'?'暂无已通过评分':'待录入')}}{{d.source==='project'?' · 自动汇总':''}}</span></template></el-table-column><el-table-column label="课程总评" width="120"><template #default="{row}"><strong>{{teacher?(total(row.studentId) ?? '待录入'):row.total ?? '待录入'}}</strong></template></el-table-column><template #empty>学生扫码或通过表格加入本班后，可在这里录入课程成绩</template></el-table></section></template>
<style scoped>.course-gradebook p{font-size:13px;line-height:1.7}.course-gradebook .el-input-number{width:140px;max-width:100%}.course-gradebook strong{color:#1e3a5f}</style>
