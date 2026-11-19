<script setup lang="ts">
import {computed} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import Portfolio from './Portfolio.vue'
import LearningAnalysis from './LearningAnalysis.vue'
const route=useRoute(),router=useRouter()
const tab=computed(()=>route.query.tab==='learning' && !route.params.id?'learning':'portfolio')
function select(value:unknown){router.push({path:'/student/personal',query:{tab:String(value)}})}
</script>
<template><div class="personal-page"><h1 class="personal-title">个人情况</h1><el-tabs :model-value="tab" @tab-change="select"><el-tab-pane name="portfolio" label="项目档案" /><el-tab-pane name="learning" label="学情分析" /></el-tabs><LearningAnalysis v-if="tab==='learning'" embedded /><Portfolio v-else embedded /></div></template>
<style scoped>.personal-title{font-size:24px;margin:0 0 12px}</style>
