<script setup lang="ts">
import MarkdownText from './MarkdownText.vue'
import {computed, ref, watch} from 'vue'
import type {Project} from '../types'
import {fileDownloadUrl} from '../lib'
import FilePreview from './FilePreview.vue'

const props = defineProps<{project:Project;fileIds:string[];description:string}>()
const selected = ref('')
const files = computed(() => props.project.files.filter(f => props.fileIds.includes(f.id)))
watch(files, fs => {if (!fs.some(f => f.id === selected.value)) selected.value = fs[0]?.id || ''}, {immediate:true})
const file = computed(() => files.value.find(f => f.id === selected.value))
</script>

<template>
  <section class="review-preview">
    <h3>提交内容</h3><MarkdownText :text="description || '暂无提交说明'" />
    <div v-if="file" class="toolbar">
      <el-select v-model="selected" aria-label="选择预览文件"><el-option v-for="f in files" :key="f.id" :value="f.id" :label="f.name" /></el-select>
      <a :href="fileDownloadUrl(project.id,file.id)">下载原文件</a>
    </div>
    <FilePreview v-if="file" :file="file" :url="'/api/projects/'+project.id+'/files/'+file.id" />
    <el-empty v-else description="未附文件，仅文字提交" :image-size="64" />
  </section>
</template>
