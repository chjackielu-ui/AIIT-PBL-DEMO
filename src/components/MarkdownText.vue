<script setup lang="ts">
import {computed} from 'vue'
import {marked} from 'marked'
import DOMPurify from 'dompurify'
const props=defineProps<{text?:string}>()
const html=computed(()=>DOMPurify.sanitize(marked.parse(props.text || '',{async:false,breaks:true}),{
 ALLOWED_TAGS:['p','br','h1','h2','h3','h4','h5','h6','strong','em','del','ul','ol','li','blockquote','pre','code','hr','table','thead','tbody','tr','th','td','a'],
 ALLOWED_ATTR:['href','title','start']
}))
</script>
<template><div class="md-body" v-html="html" /></template>
<style scoped>.md-body{line-height:1.55;overflow-wrap:anywhere;min-width:0;overflow:auto}.md-body :deep(p){margin:.35em 0}.md-body :deep(pre){overflow:auto;background:#f5f7fa;padding:8px}.md-body :deep(td),.md-body :deep(th){border:1px solid var(--line);padding:3px 8px}.md-body :deep(table){border-collapse:collapse}</style>
