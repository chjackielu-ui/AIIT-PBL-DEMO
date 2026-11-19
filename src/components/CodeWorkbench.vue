<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, watch} from 'vue'
import * as monaco from 'monaco-editor/editor/editor.api.js'
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker'
import 'monaco-editor/languages/definitions/javascript/register.js'
import 'monaco-editor/languages/definitions/typescript/register.js'
import 'monaco-editor/languages/definitions/python/register.js'
import 'monaco-editor/languages/definitions/java/register.js'
import 'monaco-editor/languages/definitions/html/register.js'
import 'monaco-editor/languages/definitions/css/register.js'
import 'monaco-editor/languages/definitions/sql/register.js'
import 'monaco-editor/languages/definitions/markdown/register.js'
import 'monaco-editor/languages/definitions/yaml/register.js'
import 'monaco-editor/languages/definitions/cpp/register.js'
import 'monaco-editor/languages/definitions/go/register.js'
import 'monaco-editor/languages/definitions/rust/register.js'
import {jsonDefaults} from 'monaco-editor/languages/features/json/register.js'
import {codeLanguage} from '../discussion'

const props=defineProps<{value:string;original?:string;name:string;diff?:boolean;inline?:boolean;language?:string}>()
const emit=defineEmits<{changes:[count:number]}>()
self.MonacoEnvironment = {getWorker: () => new EditorWorker()}
// Read-only JSON needs tokenization; diagnostics and language servers are unnecessary here.
jsonDefaults.setModeConfiguration({tokens:true})
const host=ref<HTMLElement>()
let editor: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor | undefined
let models: monaco.editor.ITextModel[]=[]
let listener: monaco.IDisposable | undefined
function dispose(){listener?.dispose();editor?.dispose();models.forEach(m=>m.dispose());models=[]}
function mount(){
 if(!host.value)return
 dispose()
 const language=props.language || codeLanguage(props.name)
 const modified=monaco.editor.createModel(props.value,language)
 models=[modified]
 const options={automaticLayout:true,theme:'vs',fontSize:12,lineHeight:21,minimap:{enabled:false},scrollBeyondLastLine:false,readOnly:true,wordWrap:'on' as const,padding:{top:12,bottom:12},renderLineHighlight:'none' as const,contextmenu:false}
 if(props.diff){
  const original=monaco.editor.createModel(props.original || '',language);models.push(original)
  const diff=monaco.editor.createDiffEditor(host.value,{...options,renderSideBySide:!props.inline,useInlineViewWhenSpaceIsLimited:false,originalEditable:false,ignoreTrimWhitespace:false,renderOverviewRuler:false,enableSplitViewResizing:true,diffWordWrap:'on'})
  diff.setModel({original,modified});editor=diff
  listener=diff.onDidUpdateDiff(()=>emit('changes',diff.getLineChanges()?.length || 0))
 }else{editor=monaco.editor.create(host.value,options);editor.setModel(modified)}
}
onMounted(mount)
watch(()=>[props.value,props.original,props.diff,props.inline,props.name,props.language],mount,{flush:'post'})
onBeforeUnmount(dispose)
</script>
<template><div ref="host" class="code-workbench" :aria-label="diff?'代码差异对比':'代码预览'" /></template>
