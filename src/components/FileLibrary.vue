<script setup lang="ts">
import {computed,ref,shallowRef,watch} from 'vue'
import {useRoute} from 'vue-router'
import {ChevronRight,Download,Ellipsis,FileText,Folder as FolderIcon,FolderOpen,FolderKanban,FolderPlus,HardDrive,LockKeyhole,PanelLeft,Search,UploadCloud} from 'lucide-vue-next'
import {ElMessage} from 'element-plus'
import {useStore} from '../store'
import {confirmAction,humanSize,run} from '../lib'
import {fileCategories,uploadProjectFile} from '../projectUploads'
import {hasFiles} from '../fileDrop'
import {directoryIndex,trail,type LibraryDirectory,type LibraryEntry,type LibraryScope} from '../libraryDirectory'
import type {Project} from '../types'
import FilePreviewDialog from './FilePreviewDialog.vue'

const props=defineProps<{project:Pick<Project,'id'|'files'|'folders'>;readonly:boolean;personal?:boolean}>()
const store=useStore(),route=useRoute()
const index=computed(()=>directoryIndex(props.project,{id:props.project.id,personal:!!props.personal,readonly:props.readonly},props.personal?store.state?.projects || []:[]))
const currentKey=ref(''),query=ref(''),filter=ref('')
const current=computed(()=>index.value.directories.get(currentKey.value) || index.value.root)
const crumbs=computed(()=>trail(current.value))
const pathLabel=(dir:LibraryDirectory)=>trail(dir).map(d=>d.name).join(' / ')
const treeOpen=ref(window.matchMedia('(min-width: 761px)').matches)
const expanded=ref(new Set([index.value.root.key]))
function toggle(dir:LibraryDirectory){
 if(expanded.value.has(dir.key))expanded.value.delete(dir.key)
 else expanded.value.add(dir.key)
}
const branches=computed(()=>{
 const rows:{dir:LibraryDirectory;depth:number}[]=[]
 function visit(dir:LibraryDirectory,depth:number){
   rows.push({dir,depth})
   if(expanded.value.has(dir.key))dir.children.forEach(c=>visit(c,depth+1))
 }
 visit(index.value.root,0)
 return rows
})
function navigate(dir:LibraryDirectory){
 currentKey.value=dir.key
 query.value='';filter.value=''
 for(const parent of trail(dir))expanded.value.add(parent.key)
}
const needle=computed(()=>query.value.trim().toLocaleLowerCase())
const searching=computed(()=>!!(needle.value || filter.value))
const visible=computed(()=>{
 const folders:LibraryDirectory[]=[],files:LibraryEntry[]=[]
 function collect(dir:LibraryDirectory){
   for(const child of dir.children){
     if(!filter.value && child.name.toLocaleLowerCase().includes(needle.value))folders.push(child)
     if(searching.value)collect(child)
   }
   for(const entry of dir.files){
     if((!filter.value || entry.file.category===filter.value) && entry.file.name.toLocaleLowerCase().includes(needle.value))files.push(entry)
   }
 }
 collect(current.value)
 files.sort((a,b)=>b.file.time.localeCompare(a.file.time))
 return {folders,files}
})
const selected=ref<string[]>([]),classIds=ref<string[]>([]),distribute=ref(false)
const isTeacher=computed(()=>props.personal && store.user?.role==='teacher')
const allowDownload=ref(true),editDownload=ref(true)
const busy=ref(false),saving=ref(false),uploadInput=ref<HTMLInputElement>()
const preview=shallowRef<LibraryEntry>(),editing=shallowRef<LibraryEntry>(),moving=shallowRef<LibraryEntry>()
const folderEditor=ref(false),editingFolder=shallowRef<LibraryDirectory>(),editorParent=shallowRef<LibraryDirectory>()
const newName=ref(''),editName=ref(''),editCategory=ref(''),destination=ref('')
const dropTarget=ref<string>(),dragged=shallowRef<LibraryEntry>()
const endpoint=(entry:LibraryEntry)=>{
 const scope=entry.directory.scope
 return (scope.personal?'/api/library':'/api/projects/'+scope.id)+'/files/'+entry.file.id
}
watch(()=>route.query.file,id=>{
 const entry=index.value.files.find(e=>e.file.id===id)
 if(entry){navigate(entry.directory);preview.value=entry}
},{immediate:true})
function action(scope:LibraryScope,op:string,data:Record<string,unknown>){
 if(scope.readonly)throw Error('此项目资料只读')
 return scope.personal
   ?store.act(op==='editFile'?'personalEdit':'personalFolder',{...data,action:op})
   :store.act(op,{...data,projectId:scope.id})
}
async function sendFiles(){
 if(saving.value)return
 saving.value=true
 await run(async()=>{
   await store.act('distributeFiles',{fileIds:selected.value,classIds:classIds.value})
   selected.value=[];classIds.value=[];distribute.value=false
 },'已分发到班级资料库')
 saving.value=false
}
async function upload(batch:File[],dir:LibraryDirectory){
 if(dir.scope.readonly || busy.value || !batch.length)return
 busy.value=true
 let done=0
 try{
   for(const file of batch){
     try{await uploadProjectFile(dir.scope.id,file,dir.localId,dir.scope.personal,allowDownload.value);done++}
     catch(e){ElMessage.error(file.name+'：'+(e as Error).message)}
   }
 }finally{await store.load();busy.value=false}
 if(done)ElMessage.success('已上传 '+done+' 份资料')
}
function choose(event:Event){
 const input=event.target as HTMLInputElement
 void upload(Array.from(input.files || []),current.value)
 input.value=''
}
function drag(event:DragEvent,entry:LibraryEntry){
 if(!event.dataTransfer || entry.directory.scope.readonly)return
 dragged.value=entry
 event.dataTransfer.setData('application/x-project-library-file',entry.key)
 event.dataTransfer.effectAllowed='move'
}
function canDrop(event:DragEvent,dir:LibraryDirectory){
 return !dir.scope.readonly && !busy.value && (hasFiles(event) || !!dragged.value && dragged.value.directory.scope===dir.scope)
}
function over(event:DragEvent,dir:LibraryDirectory){
 if(!event.dataTransfer)return
 event.preventDefault();event.stopPropagation()
 const allowed=canDrop(event,dir)
 event.dataTransfer.dropEffect=allowed?(dragged.value?'move':'copy'):'none'
 dropTarget.value=allowed?dir.key:undefined
}
function leave(event:DragEvent){
 if(event.relatedTarget instanceof Node && (event.currentTarget as Element).contains(event.relatedTarget))return
 dropTarget.value=undefined
}
function drop(event:DragEvent,dir:LibraryDirectory){
 event.preventDefault();event.stopPropagation();dropTarget.value=undefined
 if(!canDrop(event,dir))return
 if(dragged.value)void move(dragged.value,dir.localId)
 else void upload(Array.from(event.dataTransfer?.files || []),dir)
 dragged.value=undefined
}
const destinations=computed(()=>Array.from(index.value.directories.values()).filter(d=>d.localId && moving.value && d.scope.id===moving.value.directory.scope.id && d.scope.personal===moving.value.directory.scope.personal))
async function move(entry:LibraryEntry,folderId:string){
 if(entry.directory.localId===folderId || saving.value)return
 saving.value=true
 await run(async()=>{
   await action(entry.directory.scope,'moveFile',{fileId:entry.file.id,folderId})
   moving.value=undefined
 },'资料已移动')
 saving.value=false
}
function folderCommand(command:string,dir:LibraryDirectory){
 if(!dir.localId || dir.projectRoot || dir.scope.readonly)return
 if(command==='delete'){
   void confirmAction('删除空文件夹「'+dir.name+'」？',()=>action(dir.scope,'deleteFolder',{folderId:dir.localId}),'文件夹已删除')
 }else showFolderEditor(dir)
}
function showFolderEditor(dir?:LibraryDirectory){
 editingFolder.value=dir
 editorParent.value=dir?.parent || current.value
 newName.value=dir?.name || ''
 folderEditor.value=true
}
async function saveFolder(){
 const parent=editorParent.value,dir=editingFolder.value
 if(!parent || !newName.value.trim() || saving.value)return
 saving.value=true
 await run(async()=>{
   await action(parent.scope,dir?'renameFolder':'createFolder',{folderId:dir?.localId,parentId:parent.localId,name:newName.value.trim()})
   folderEditor.value=false;query.value='';filter.value=''
 },dir?'文件夹已重命名':'文件夹已新建')
 saving.value=false
}
function fileAction(command:string,entry:LibraryEntry){
 if(command==='move'){moving.value=entry;destination.value=entry.directory.localId;return}
 editing.value=entry;editName.value=entry.file.name;editCategory.value=entry.file.category;editDownload.value=entry.file.downloadable!==false
}
async function saveFile(){
 const entry=editing.value
 if(!entry || !editName.value.trim() || saving.value)return
 saving.value=true
 await run(async()=>{
   await action(entry.directory.scope,'editFile',{fileId:entry.file.id,name:editName.value.trim(),category:editCategory.value,downloadable:editDownload.value})
   editing.value=undefined
 },'资料已更新')
 saving.value=false
}
</script>

<template>
 <section class="file-desk" @dragend="dragged=undefined;dropTarget=undefined">
  <div class="desk-toolbar">
   <button class="desk-icon tree-toggle" :class="{active:treeOpen}" type="button" aria-label="目录面板" :aria-expanded="treeOpen" title="展开或收起目录" @click="treeOpen=!treeOpen"><PanelLeft :size="17"/></button>
   <el-input v-model="query" class="desk-search" placeholder="搜索文件和文件夹" aria-label="搜索文件和文件夹" clearable><template #prefix><Search :size="15"/></template></el-input>
   <el-select v-model="filter" class="type-filter" clearable placeholder="全部文件类型" aria-label="筛选文件类型"><el-option v-for="item in fileCategories" :key="item" :value="item"/></el-select>
   <div v-if="!current.scope.readonly" class="desk-actions">
    <template v-if="isTeacher"><el-checkbox v-model="allowDownload">允许学生下载</el-checkbox><el-button :disabled="!selected.length" @click="distribute=true">分发到班级{{selected.length?'（'+selected.length+'）':''}}</el-button></template>
    <el-button @click="showFolderEditor()"><FolderPlus :size="15"/>新建文件夹</el-button>
    <el-button type="primary" :loading="busy" @click="uploadInput?.click()"><UploadCloud :size="15"/>上传文件</el-button>
    <input ref="uploadInput" type="file" multiple hidden @change="choose"/>
   </div>
  </div>
  <div class="desk-body" :class="{split:treeOpen}">
   <nav v-if="treeOpen" aria-label="资料库目录" class="desk-tree">
    <div v-for="{dir,depth} in branches" :key="dir.key" class="tree-row" :class="{chosen:dir.key===current.key,over:dir.key===dropTarget}" :style="{'--depth':depth}" @dragover="over($event,dir)" @dragleave="leave" @drop="drop($event,dir)">
     <button v-if="dir.children.length" class="branch-toggle" type="button" :aria-label="(expanded.has(dir.key)?'折叠':'展开')+dir.name" :aria-expanded="expanded.has(dir.key)" @click="toggle(dir)"><ChevronRight :size="13" :class="{down:expanded.has(dir.key)}"/></button><span v-else class="branch-spacer"></span>
     <button class="tree-label" type="button" :title="dir.name" :aria-current="dir.key===current.key?'location':undefined" @click="navigate(dir)">
      <component :is="!dir.parent?HardDrive:dir.projectRoot?FolderKanban:dir.key===current.key?FolderOpen:FolderIcon" :size="16"/><span>{{dir.name}}</span>
     </button>
    </div>
   </nav>
   <div class="desk-content" :class="{over:current.key===dropTarget}" @dragover="over($event,current)" @dragleave="leave" @drop="drop($event,current)">
    <nav class="file-crumbs" aria-label="资料路径"><template v-for="(dir,i) in crumbs" :key="dir.key"><ChevronRight v-if="i" :size="13"/><button class="text-button" :title="dir.name" :aria-current="dir.key===current.key?'location':undefined" @click="navigate(dir)">{{dir.name}}</button></template><small v-if="current.scope.readonly"><LockKeyhole :size="12"/>只读</small></nav>
    <div v-if="visible.folders.length || visible.files.length" class="desk-grid">
     <article v-for="dir in visible.folders" :key="dir.key" class="desk-entry" :class="{over:dir.key===dropTarget}" @dragover="over($event,dir)" @dragleave="leave" @drop="drop($event,dir)">
      <button class="entry-open" type="button" :title="pathLabel(dir)" @click="navigate(dir)">
       <span class="entry-art" :class="{project:dir.projectRoot}"><component :is="dir.projectRoot?FolderKanban:FolderIcon" :size="26" :stroke-width="1.6"/></span>
       <span class="entry-text"><strong>{{dir.name}}</strong><small>{{dir.projectRoot?'项目 · ':''}}{{dir.children.length+dir.files.length}} 项</small></span>
      </button>
      <span v-if="dir.projectRoot" class="entry-lock" title="项目文件夹" aria-label="项目文件夹"><LockKeyhole :size="13"/></span>
      <el-dropdown v-else-if="!dir.scope.readonly" class="entry-menu" trigger="click" @command="(cmd:string)=>folderCommand(cmd,dir)"><button class="desk-icon" :aria-label="dir.name+' 文件夹操作'"><Ellipsis :size="16"/></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="rename">重命名</el-dropdown-item><el-dropdown-item command="delete" :disabled="!!(dir.children.length || dir.files.length)">删除空文件夹</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
     </article>
     <article v-for="entry in visible.files" :key="entry.key" class="desk-entry file" :class="{selectable:isTeacher}" :draggable="!entry.directory.scope.readonly" @dragstart="drag($event,entry)">
      <el-checkbox v-if="isTeacher" v-model="selected" :value="entry.file.id" class="entry-select" :aria-label="'选择 '+entry.file.name"/>
      <button class="entry-open" type="button" :title="entry.file.name" @click="preview=entry"><span class="entry-art paper"><FileText :size="23"/></span><span class="entry-text"><strong>{{entry.file.name}}</strong><small>{{searching?pathLabel(entry.directory)+' · ':''}}{{humanSize(entry.file.size)}}</small></span></button>
      <div class="entry-menu"><a class="desk-icon" :href="endpoint(entry)+'?download=true'" :aria-label="'下载 '+entry.file.name" title="下载" download><Download :size="14"/></a>
       <el-dropdown v-if="!entry.directory.scope.readonly" trigger="click" @command="(cmd:string)=>fileAction(cmd,entry)"><button class="desk-icon" :aria-label="entry.file.name+' 资料操作'"><Ellipsis :size="16"/></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="move">移动到文件夹</el-dropdown-item><el-dropdown-item v-if="store.user?.role==='teacher' || entry.file.userId===store.user?.id" command="edit">编辑资料</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </div>
     </article>
    </div>
    <el-empty v-else :description="searching?'没有匹配的资料':'此文件夹为空'" :image-size="48"/>
   </div>
  </div>
 </section>
  <el-dialog v-model="distribute" title="分发到班级资料库" width="min(460px,94vw)">
    <p>已选择 {{selected.length}} 份资料</p>
    <el-select v-model="classIds" multiple filterable placeholder="选择接收资料的教学班"><el-option v-for="tc in store.state?.teachingClasses" :key="tc.id" :label="tc.name" :value="tc.id" /></el-select>
    <template #footer><el-button @click="distribute=false">取消</el-button><el-button type="primary" :disabled="!classIds.length" :loading="saving" @click="sendFiles">确认分发</el-button></template>
  </el-dialog>
  <FilePreviewDialog :file="preview?.file" :url="preview ? endpoint(preview) : ''" @close="preview=undefined" />
  <el-dialog v-model="folderEditor" :title="editingFolder ? '重命名文件夹' : '新建文件夹'" width="min(420px, 92vw)">
    <el-form label-position="top" @submit.prevent="saveFolder"><el-form-item label="文件夹名称"><el-input v-model="newName" maxlength="80" /></el-form-item></el-form>
    <template #footer><el-button @click="folderEditor=false">取消</el-button><el-button type="primary" :disabled="!newName.trim()" :loading="saving" @click="saveFolder">{{editingFolder?'保存':'新建'}}</el-button></template>
  </el-dialog>
  <el-dialog :model-value="!!moving" title="移动资料" width="min(440px, 92vw)" @close="moving=undefined">
    <el-form label-position="top"><el-form-item label="目标文件夹"><el-select v-model="destination" filterable><el-option value="" label="资料根目录" /><el-option v-for="folder in destinations" :key="folder.key" :value="folder.localId" :label="pathLabel(folder)" /></el-select></el-form-item></el-form>
    <template #footer><el-button @click="moving=undefined">取消</el-button><el-button type="primary" :loading="saving" :disabled="destination===(moving?.file.folderId || '')" @click="moving && move(moving,destination)">移动</el-button></template>
  </el-dialog>
  <el-dialog :model-value="!!editing" title="编辑资料" width="min(440px, 92vw)" @close="editing=undefined">
    <el-form label-position="top"><el-checkbox v-if="editing?.directory.scope.personal && store.user?.role==='teacher'" v-model="editDownload">允许学生下载</el-checkbox><el-form-item label="文件名称"><el-input v-model="editName" /></el-form-item><el-form-item label="分类"><el-select v-model="editCategory"><el-option v-for="item in fileCategories" :key="item" :value="item" /></el-select></el-form-item></el-form>
    <template #footer><el-button @click="editing=undefined">取消</el-button><el-button type="primary" :loading="saving" @click="saveFile">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>
.file-desk{min-width:0;background:#f9fbfe;border:1px solid var(--line);border-radius:10px;color:#263d58}
.desk-toolbar,.desk-actions,.file-crumbs,.entry-menu,.tree-label,.branch-toggle,.desk-icon,.entry-open{display:flex;align-items:center}
.desk-toolbar{flex-wrap:wrap;gap:8px;padding:10px 12px;border-bottom:1px solid var(--line)}
.desk-toolbar .desk-search{flex:1 1 152px;min-width:132px;max-width:310px}.desk-toolbar .type-filter{width:128px}
.desk-actions{margin-left:auto;gap:8px;flex-wrap:wrap}.desk-actions .el-button{margin:0}.desk-actions .el-checkbox{margin-right:2px}
.desk-icon,.branch-toggle,.tree-label,.entry-open{border:0;background:transparent;color:inherit;cursor:pointer}
.desk-icon,.branch-toggle{justify-content:center;padding:0;border-radius:4px}.desk-icon{width:24px;height:26px}.tree-toggle{height:32px;width:32px;border:1px solid var(--line)}.tree-toggle.active,.desk-icon:hover,.branch-toggle:hover{background:#e8eff9;color:#254f87}
.desk-body{display:grid;min-height:270px}.desk-body.split{grid-template-columns:clamp(160px,20%,224px) minmax(0,1fr)}
.desk-tree{padding:9px 6px;display:flex;flex-direction:column;gap:2px;border-right:1px solid var(--line);max-height:64vh;overflow:auto}
.tree-row{display:flex;align-items:center;padding:0 3px 0 calc(min(var(--depth),6)*12px);min-height:31px;border-radius:5px}
.tree-row.chosen{color:#254f87;background:#e8eff9;font-weight:600}.tree-row:hover{background:#eef4fb}.tree-label{gap:7px;text-align:left;min-width:0;flex:1;font:inherit;font-size:13px;padding:6px 2px}
.tree-label span,.entry-text strong,.entry-text small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.tree-label svg,.file-crumbs svg{flex:none}
.branch-toggle,.branch-spacer{width:18px;height:24px;flex:none}.branch-toggle .down{transform:rotate(90deg)}
.desk-content{min-width:0;padding:9px 12px 14px}.file-crumbs{flex-direction:row;flex-wrap:wrap;gap:3px;min-height:30px;margin-bottom:7px;color:#61768e}
.file-crumbs .text-button{font-size:13px;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;display:block}.file-crumbs [aria-current]{font-weight:650;color:#273f5c}.file-crumbs small{display:inline-flex;align-items:center;gap:3px}
.desk-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(196px,100%),1fr));gap:8px}
.desk-entry{display:flex;align-items:center;position:relative;min-width:0;background:white;border:1px solid #dce5f0;border-radius:7px;padding-right:4px}
.desk-entry:hover,.desk-entry:focus-within{background:#f3f7fe;border-color:#b7cce6}
.entry-open{gap:9px;text-align:left;min-width:0;flex:1;min-height:65px;padding:8px}.entry-art{display:grid;place-items:center;flex:none;width:35px;height:35px;border-radius:7px;background:#edf3fc;color:#5079a6}.entry-art.project{color:#3d6598;background:#e6effb}.entry-art.paper{background:#f2f5f9}
.entry-text{display:grid;gap:3px;min-width:0}.entry-text strong{font-size:13px}.entry-text small{font-size:11px;color:#65758a}
.entry-lock{color:#768aa0;padding:4px}.entry-menu{flex-direction:column;flex:none}.entry-menu .desk-icon{height:23px}.entry-select{margin-left:8px;margin-right:0}.over{outline:2px solid #7da5d4;outline-offset:-2px}
@media(max-width:760px){.desk-body.split{grid-template-columns:minmax(0,1fr)}.desk-tree{border-right:0;border-bottom:1px solid var(--line);max-height:190px}.desk-toolbar{gap:6px}.desk-actions{margin-left:0}.desk-content{padding:8px}.desk-toolbar .type-filter{width:118px}.desk-grid{grid-template-columns:repeat(auto-fill,minmax(min(160px,100%),1fr))}}
</style>
