<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useStore} from '../store'

import {classPath} from '../teacher'
const route = useRoute()
const store = useStore()
defineProps<{label?: string}>()
const tc = computed(() => store.state?.teachingClasses.find(c => c.id === route.params.classId))
const course = computed(() => store.state?.courses.find(c => c.id === tc.value?.courseId))
const teacherNames = computed(() =>
  (tc.value?.teacherIds || []).map(id => store.state?.users.find(u => u.id === id)?.name).join('、'))
</script>
<template>
  <div v-if="tc" class="teacher-context">
    <div class="section-head">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item to="/teacher/classes">教学班级</el-breadcrumb-item>
        <el-breadcrumb-item :to="classPath(tc.id)">{{tc.name}}</el-breadcrumb-item>
        <el-breadcrumb-item v-if="label">{{label}}</el-breadcrumb-item>
      </el-breadcrumb>
      <span>{{tc.academicYear}} · {{tc.semester}}</span>
    </div>
    <div class="context-course">{{course?.name}}<span>任课教师：{{teacherNames}}</span></div>
  </div>
</template>
