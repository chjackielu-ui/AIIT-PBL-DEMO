<script setup lang="ts">
import {computed} from 'vue'
import type {TeachingClass} from '../types'
const props = defineProps<{classes: TeachingClass[]}>()
const year = defineModel<string>('year', {required: true})
const semester = defineModel<string>('semester', {required: true})
const years = computed(() =>
  [...new Set([year.value, ...props.classes.map(c => c.academicYear)])].sort().reverse())
/** 学期选项按所选学年实际存在的学期生成，避免选到空学期。 */
const semesters = computed(() => {
  const set = new Set(props.classes.filter(c => c.academicYear === year.value).map(c => c.semester))
  if (!set.size) return semester.value ? [semester.value] : ['第一学期']
  return [...set].sort((a, b) => (b === '第二学期' ? 1 : 0) - (a === '第二学期' ? 1 : 0))
})
function changeYear() {
  const terms = props.classes.filter(c => c.academicYear === year.value).map(c => c.semester)
  semester.value = terms.includes('第二学期') ? '第二学期' : (terms[0] || '第一学期')
}
</script>
<template>
  <div class="term-filter">
    <el-select v-model="year" aria-label="学年" @change="changeYear">
      <el-option v-for="y in years" :key="y" :value="y" />
    </el-select>
    <el-select v-model="semester" aria-label="学期">
      <el-option v-for="s in semesters" :key="s" :value="s" />
    </el-select>
  </div>
</template>
