<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute} from 'vue-router'
import {useStore} from '../store'
import {latestTerm} from '../academic'
import {teachingClasses, classIntentOf, classIntents} from '../teacher'
import TermFilter from '../components/TermFilter.vue'
import PageHead from '../components/PageHead.vue'
import ClassGrid from '../components/ClassGrid.vue'

const route = useRoute()
const store = useStore()
const s = computed(() => store.state!)

const all = computed(() => teachingClasses(s.value))
const intent = computed(() => classIntentOf(route.query.intent) ? classIntents[classIntentOf(route.query.intent)!] : undefined)

const query = ref('')
const year = ref(latestTerm(all.value).academicYear)
const semester = ref(latestTerm(all.value).semester)

const filtered = computed(() => all.value.filter(c =>
  (c.name + ' ' + s.value.courses.find(x => x.id === c.courseId)?.name).includes(query.value)
  && c.academicYear === year.value && c.semester === semester.value))

</script>
<template>
  <PageHead :title="intent?.title || '我的实训班级'" />
  <div class="toolbar">
    <el-input v-model="query" clearable placeholder="搜索教学班级或课程" />
    <TermFilter :classes="all" v-model:year="year" v-model:semester="semester" />
    <span>{{filtered.length}} 个教学班级</span>
  </div>
  <ClassGrid :classes="filtered" :destination="intent?.destination" />
</template>
