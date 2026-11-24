<script setup lang="ts">
import {computed} from 'vue'
import {graduationCredits, softwareProjectCourses} from '../curriculum'

const {cohort} = defineProps<{cohort?:number}>()
const requirements = computed(() => graduationCredits(cohort))
const terms = [
  ['1-1','大一秋季'],['1-2','大一春季'],['1-3','大一夏季小学期'],
  ['2-1','大二秋季'],['2-2','大二春季'],['2-3','大二夏季小学期'],
  ['3-1','大三秋季'],['3-2','大三春季'],['4-1','大四秋季'],['4-2','大四春季'],
]
const challenges = softwareProjectCourses.filter(course => course.challenge !== 'PBL')
</script>

<template>
  <section class="section curriculum">
    <details>
      <summary>软件工程培养方案 · 项目学习串</summary>
      <p class="muted">2022 级培养方案第 60–61 页 · 最低毕业学分 174<template v-if="cohort === 2024">；2024 级教务成绩表示例最低毕业学分 {{ requirements }}</template></p>
      <ol class="challenge-path" aria-label="软件工程分级挑战项目">
        <li v-for="course in challenges" :key="course.code">
          <small>{{ course.challenge }} · {{ course.level }}</small>
          <strong>{{ course.name }}</strong>
          <span>{{ course.credits }} 学分</span>
        </li>
      </ol>
      <div class="curriculum-legend">
        <p><strong>L · 项目目标层次</strong>　1L 初步感知、2L 巩固提升、3L 深化拓展、4L 持久发展</p>
        <p><strong>D · 项目设计维度</strong>　1D 课程内、2D 学期课程间、3D 跨学期课程间、4D 跨学科</p>
        <p><strong>Cx · 挑战项目级别</strong>　C0 是入门级，C1–C4 是各学年的分级挑战项目。</p>
      </div>
      <details class="course-plan-list">
        <summary>查看全部 29 门项目学习课程</summary>
        <dl v-for="[term, label] in terms" :key="term">
          <dt>{{ label }}</dt>
          <dd v-for="course in softwareProjectCourses.filter(row => row.term === term)" :key="course.code">{{ course.name }} <small>{{ course.level }} · {{ course.credits }} 学分</small></dd>
        </dl>
      </details>
    </details>
  </section>
</template>

<style scoped>
.curriculum{margin-top:12px;padding:12px 16px}
.curriculum summary{font-weight:650;cursor:pointer;color:#294572}
.curriculum summary + p{margin:10px 0}
.challenge-path{padding:0;margin:14px 0;list-style:none;display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:8px}
.challenge-path li{border-top:3px solid #6484bc;border-radius:4px;background:#f1f5fb;padding:8px;display:grid;gap:3px}
.challenge-path small,.challenge-path span,.course-plan-list small{font-size:12px;color:var(--muted)}
.curriculum-legend p{margin:4px 0;font-size:13px}
.course-plan-list{margin-top:10px}.course-plan-list dl{margin:10px 0}.course-plan-list dt{font-weight:650}.course-plan-list dd{margin:4px 0 0;padding-left:12px}
@media(max-width:900px){.challenge-path{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:510px){.challenge-path{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
