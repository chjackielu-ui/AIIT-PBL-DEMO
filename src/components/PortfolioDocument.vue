<script setup lang="ts">
import MarkdownText from './MarkdownText.vue'
import {termLabel} from '../academic'
import type {PortfolioRecord} from '../projectPresentation'

defineProps<{records: PortfolioRecord[]; name: string; adminClass?: string}>()
</script>
<template>
  <article class="print-document">
    <div class="document-title">
      <h1>个人项目履历</h1>
      <p>{{name}} · {{adminClass}}</p>
    </div>
    <section v-for="r in records" :key="r.id" class="print-record">
      <div class="section-head">
        <h2>{{r.name}}</h2>
        <time>{{r.date}}</time>
      </div>
      <p class="muted">{{[r.course, r.teachingClass, termLabel(r.year, r.semester), r.role].filter(Boolean).join(' · ')}}</p>
      <h3>个人职责与贡献</h3>
      <p class="pre-wrap">{{r.description || '尚未填写'}}{{r.history ? ' ' + r.contribution : ''}}</p>
      <h3>成果摘要</h3>
      <MarkdownText :text="r.summary" />
      <p v-if="r.score != null">教师确认成绩：{{r.score}} 分 · {{r.comment}}</p>
    </section>
    <p v-if="!records.length">暂无项目经历。</p>
  </article>
</template>
