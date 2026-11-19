<script setup lang="ts">
import {request} from '../api'
import {computed, ref, watch} from 'vue'
import type {Project} from '../types'
import {fileDownloadUrl, run} from '../lib'
const props = defineProps<{project:Project;fileIds:string[];description:string}>()
const selected = ref('')
const text = ref('')
const files = computed(() => props.project.files.filter(f => props.fileIds.includes(f.id)))
watch(files, fs => {selected.value = fs[0]?.id || ''}, {immediate:true})
const file = computed(() => files.value.find(f => f.id === selected.value))
const kind = computed(() => file.value?.type.startsWith('image/') ? 'image' : file.value?.type === 'application/pdf' || /\.pdf$/i.test(file.value?.name || '') ? 'pdf' : /\.(txt|md|json|js|ts|vue|java|py|css|csv|sql)$/i.test(file.value?.name || '') || file.value?.type.startsWith('text/') ? 'text' : 'other')
let sequence = 0
watch(file, async f => {
 const seq = ++sequence; text.value = ''
 if (!f || kind.value !== 'text') return
 if (f.size > 2*1024*1024) {text.value='文件超过 2 MB，请下载查看';return}
 await run(async () => {const res = await request(`/api/projects/${props.project.id}/files/${f.id}`);if(!res.ok) throw Error('无法读取附件');const value=await res.text();if(seq===sequence) text.value=value},'')
},{immediate:true})
</script>
<template><section class="review-preview"><h3>提交内容</h3><p class="pre-wrap">{{description || '暂无提交说明'}}</p><div class="toolbar"><el-select v-if="files.length" v-model="selected" aria-label="选择预览文件"><el-option v-for="f in files" :key="f.id" :value="f.id" :label="f.name" /></el-select><a v-if="file" :href="fileDownloadUrl(project.id,file.id)">下载原文件</a></div><div class="submission-canvas"><img v-if="file && kind==='image'" :src="`/api/projects/${project.id}/files/${file.id}`" :alt="file.name" /><iframe v-else-if="file && kind==='pdf'" :src="`/api/projects/${project.id}/files/${file.id}`" title="成果预览" /><pre v-else-if="kind==='text'">{{text || '正在读取…'}}</pre><el-empty v-else :description="file ? '此格式请下载查看' : '未附文件，仅文字提交'" :image-size="64" /></div></section></template>
