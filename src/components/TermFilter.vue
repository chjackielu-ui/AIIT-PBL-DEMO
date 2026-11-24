<script setup lang="ts">
import {computed} from 'vue'
import type {TeachingClass} from '../types'
import {semesters, latestTerm} from '../academic'
const props = defineProps<{classes: TeachingClass[]}>()
const year = defineModel<string>('year', {required: true})
const semester = defineModel<string>('semester', {required: true})
const years = computed(() =>
  [...new Set([year.value, ...props.classes.map(c => c.academicYear)])].sort().reverse())
function changeYear() {
  semester.value = latestTerm(props.classes.filter(c => c.academicYear === year.value)).semester
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

<style scoped>
@media(max-width:600px){.term-filter .el-select{width:clamp(106px,26vw,145px)}}
</style>
