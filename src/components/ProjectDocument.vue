<script setup lang="ts">
import {safeLink} from '../lib'

interface ProjectDoc {
  name: string
  description: string
  tags: string
  created: string
  status: string
  course?: string
  teachingClass?: string
  members: {name: string; role: string}[]
  summary?: string
  result?: string
  comment?: string
  link?: string
  images: {id: string; name: string}[]
}

const props = defineProps<{data: ProjectDoc; sharing?: boolean; imageUrl: (id: string) => string}>()
const link = safeLink(props.data.link || '')
</script>
<template>
  <article class="print-document">
    <div class="document-title">
      <span class="document-eyebrow">{{sharing ? '项目成果展示' : '项目介绍'}}</span>
      <h1>{{data.name}}</h1>
      <p>{{data.course}} · {{data.teachingClass}} · {{data.created}}</p>
      <p>{{data.status}} · {{data.tags}}</p>
    </div>
    <section>
      <h2>项目简介</h2>
      <p class="pre-wrap">{{data.description}}</p>
    </section>
    <section>
      <h2>团队介绍</h2>
      <div class="public-members">
        <div v-for="(member, i) in data.members" :key="i">
          <strong>{{member.name}}</strong>
          <span>{{member.role}}</span>
        </div>
      </div>
    </section>
    <section v-if="data.summary">
      <h2>已审核最终成果</h2>
      <p class="pre-wrap">{{data.summary}}</p>
      <p class="pre-wrap">{{data.result}}</p>
      <div class="public-images">
        <figure v-for="img in data.images" :key="img.id">
          <img :src="imageUrl(img.id)" :alt="img.name" />
          <figcaption>{{img.name}}</figcaption>
        </figure>
      </div>
      <a v-if="link" :href="link" target="_blank" rel="noopener noreferrer">外部作品链接</a>
      <h3 v-if="data.comment">教师评价</h3>
      <p v-if="data.comment" class="pre-wrap">{{data.comment}}</p>
    </section>
    <p v-else class="muted">尚无审核通过的最终成果。</p>
  </article>
</template>
