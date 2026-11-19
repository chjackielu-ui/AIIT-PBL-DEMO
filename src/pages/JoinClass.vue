<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {GraduationCap,CheckCircle2} from 'lucide-vue-next'
import {useStore} from '../store'
import {api} from '../api'
import {run} from '../lib'
const route=useRoute(),router=useRouter(),store=useStore()
const invitation=ref<{classId:string;name:string;courseName:string;academicYear:string;semester:string}>(),error=ref(''),busy=ref(false)
const joined=computed(()=>store.state?.enrollments.some(e=>e.classId===invitation.value?.classId && e.studentId===store.user?.id))
onMounted(async()=>{try{invitation.value=await api('/class-invites/'+encodeURIComponent(String(route.params.token)))}catch(e){error.value=e instanceof Error?e.message:String(e)}})
async function join(){busy.value=true;await run(async()=>{await store.act('joinClass',{token:String(route.params.token)})},'已加入班级');busy.value=false}
function login(){router.push({path:'/login',query:{redirect:route.fullPath}})}
</script>
<template><div class="join-class-page"><section class="section join-class-card"><GraduationCap :size="32" class="join-class-icon" /><h1>加入教学班级</h1><el-alert v-if="error" :title="error" type="error" :closable="false" /><template v-else-if="invitation"><h2>{{invitation.name}}</h2><p>{{invitation.courseName}}</p><p class="muted">{{invitation.academicYear}} · {{invitation.semester}}</p><template v-if="store.user?.role==='student'"><div class="info-strip">以 {{store.user.name}}（{{store.user.number || store.user.id}}）的身份加入</div><template v-if="joined"><p class="success-text"><CheckCircle2 :size="18" /> 你已加入本班</p><el-button type="primary" @click="router.push('/student/tasks?classId='+invitation.classId)">进入课程</el-button></template><el-button v-else type="primary" :loading="busy" @click="join">确认加入班级</el-button></template><template v-else><p class="muted">请使用学生身份登录后确认入班。</p><el-button type="primary" @click="login">学生登录</el-button></template></template><p v-else class="muted">正在读取班级信息…</p></section></div></template>
<style scoped>.join-class-page{min-height:100dvh;background:#f8fafc;display:flex;align-items:center;justify-content:center;padding:20px}.join-class-card{width:460px;max-width:100%;margin:0}.join-class-icon{color:var(--primary);margin-bottom:16px}.join-class-card h1{margin-bottom:20px}.join-class-card h2{margin-bottom:8px}.join-class-card .info-strip{margin:20px 0}.join-class-card .el-button{width:100%}</style>
