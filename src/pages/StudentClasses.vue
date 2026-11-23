<script setup lang="ts">
import {computed,ref} from 'vue'
import {useStore} from '../store'

import PageHead from '../components/PageHead.vue'
const store=useStore(),selected=ref('')
const classes=computed(()=>store.state!.teachingClasses.filter(c=>store.state!.enrollments.some(e=>e.classId===c.id && e.studentId===store.user?.id)))
const courses=computed(()=>store.state!.courses.filter(c=>classes.value.some(tc=>tc.courseId===c.id) && (!selected.value || selected.value===c.id)))
</script>
<template><PageHead title="我的课程与教学班级" /><div class="toolbar"><el-select v-model="selected" clearable aria-label="筛选我的课程" placeholder="全部课程"><el-option v-for="c in store.state?.courses.filter(c=>classes.some(tc=>tc.courseId===c.id))" :key="c.id" :value="c.id" :label="c.name" /></el-select><span class="muted">{{classes.length}} 个选课教学班，可同时参加不同课程或多个班级</span></div><section v-for="course in courses" :key="course.id"><h2>{{course.name}}</h2><div class="dashboard-grid"><section v-for="c in classes.filter(c=>c.courseId===course.id)" :key="c.id" class="section"><small class="muted">{{c.academicYear}} · {{c.semester}}</small><h3>{{c.name}}</h3><p v-if="course.status">课程状态 {{course.status}}</p><p v-if="course.plannedStart" class="muted">计划 {{course.plannedStart.replace('T', ' ')}} 至 {{course.plannedEnd?.replace('T', ' ')}}</p><p>任课教师：{{c.teacherIds.map(id=>store.state?.users.find(u=>u.id===id)?.name).join('、')}}</p><p class="muted">{{store.state?.tasks.filter(t=>t.classId===c.id && t.status==='已发布').length}} 项已发布任务</p><div class="actions wrap"><el-button type="primary" @click="$router.push('/student/tasks?classId='+c.id)">进入课程</el-button><el-button @click="$router.push({path:'/student/tasks',query:{classId:c.id,module:'resources'}})">班级资料库</el-button><el-button @click="$router.push('/student/classes/'+c.id+'/details')">课程说明与规则</el-button></div></section></div></section><el-empty v-if="!classes.length" description="暂无所属教学班级" /></template>
