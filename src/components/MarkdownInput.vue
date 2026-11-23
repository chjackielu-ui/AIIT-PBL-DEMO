<script setup lang="ts">
import {ref,useAttrs} from 'vue'
import {ElMessage} from 'element-plus'
import {downloadMarkdown} from '../markdown'
import MarkdownText from './MarkdownText.vue'
defineOptions({inheritAttrs:false})
const model=defineModel<string>()
const props=defineProps<{disabled?:boolean;readonly?:boolean}>()
const preview=ref(false), attrs=useAttrs()
async function read(e:Event){
 const input=e.target as HTMLInputElement,file=input.files?.[0]
 input.value=''
 if(!file || props.disabled || props.readonly)return
 try{
  if(file.size>2*1024*1024)throw Error('Markdown 文件不能超过 2 MB')
  const value=await file.text()
  if(attrs.maxlength && value.length>Number(attrs.maxlength))throw Error('文档超过此字段的字数限制')
  model.value=value
 }catch(e){ElMessage.error((e as Error).message)}
}
</script>
<template><div class="markdown-input">
 <MarkdownText v-if="preview || disabled || readonly" :text="model || ''"/>
 <el-input v-else v-bind="$attrs" type="textarea" v-model="model"/>
 <div class="md-controls">
 <button v-if="!disabled && !readonly" type="button" @click="preview=!preview">{{preview?'继续编辑':'预览 Markdown'}}</button>
 <label v-if="!disabled && !readonly">导入 .md<input type="file" accept=".md,.markdown,text/markdown" @change="read"/></label>
 <button v-if="model" type="button" @click="downloadMarkdown('文档',model)">导出 .md</button>
 </div></div></template>
<style scoped>.markdown-input{width:100%;min-width:0}.md-controls{display:flex;align-items:center;gap:12px;line-height:22px}.md-controls button,.md-controls label{font:inherit;font-size:12px;cursor:pointer;background:none;padding:0;border:0;color:var(--muted)}.md-controls input{width:1px;height:1px;position:absolute;opacity:0}.md-controls label:focus-within{outline:1px solid currentColor}</style>
