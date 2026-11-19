<script setup lang="ts">
import {computed, nextTick, ref} from 'vue'
import {Search, Upload, FileText} from 'lucide-vue-next'
import {useStore} from '../store'

import {fileDownloadUrl, humanSize, stamp} from '../lib'
import type {Project, ProjectFile} from '../types'
import PageHead from '../components/PageHead.vue'
import FileLibrary from '../components/FileLibrary.vue'
import DirectUpload from '../components/DirectUpload.vue'
const store=useStore()
const query=ref(''), projectId=ref(''), category=ref(''), activeId=ref(''), uploadOpen=ref(false), uploadProject=ref('')
const library=ref<InstanceType<typeof FileLibrary>>()
const projects=computed(()=>store.state!.projects.filter(p=>p.memberIds.includes(store.user!.id)))
const writable=computed(()=>projects.value.filter(p=>!['待审核','待修改','已归档','已关闭'].includes(p.status)))
const all=computed(()=>projects.value.flatMap(p=>p.files.map(f=>({p,f}))))
const rows=computed(()=>all.value.filter(({p,f})=>(!projectId.value||p.id===projectId.value)&&(!category.value||f.category===category.value)&&(f.name+' '+p.name).toLowerCase().includes(query.value.toLowerCase())).sort((a,b)=>b.f.time.localeCompare(a.f.time)))
const active=computed(()=>projects.value.find(p=>p.id===activeId.value))
const locked=(p:Project)=>['待审核','待修改','已归档','已关闭'].includes(p.status)
async function inspect(p:Project,f:ProjectFile,edit=false){activeId.value=p.id;await nextTick();if(edit)library.value?.editFile(f);else library.value?.open(f)}
function startUpload(){uploadProject.value=writable.value.find(p=>p.id===projectId.value)?.id || writable.value[0]?.id || '';uploadOpen.value=true}
</script>
<template>
 <PageHead title="资料库"><el-button type="primary" :disabled="!writable.length" @click="startUpload"><Upload :size="16" />上传资料</el-button></PageHead>
 <div class="library-overview"><span><strong>{{all.length}}</strong>份资料</span><span><strong>{{projects.length}}</strong>个项目</span><span class="muted">任务提交与聊天上传的文件自动收录，按项目保留访问权限。</span></div>
 <div class="toolbar"><el-input v-model="query" clearable placeholder="搜索资料或项目名称"><template #prefix><Search :size="16" /></template></el-input><el-select v-model="projectId" clearable placeholder="全部项目" aria-label="筛选资料所属项目"><el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" /></el-select><el-select v-model="category" clearable placeholder="全部分类" aria-label="筛选资料分类"><el-option v-for="v in ['文档','图片','代码','压缩包','其他']" :key="v" :value="v" /></el-select><span class="muted">{{rows.length}} 份资料</span></div>
 <el-table :data="rows"><el-table-column label="资料名称" min-width="220"><template #default="{row}"><button class="text-button file-name" @click="inspect(row.p,row.f)"><FileText :size="16" />{{row.f.name}}</button></template></el-table-column><el-table-column label="所属项目" min-width="160"><template #default="{row}"><RouterLink :to="'/student/projects/'+row.p.id+'?tab=files'">{{row.p.name}}</RouterLink></template></el-table-column><el-table-column label="分类" width="85"><template #default="{row}">{{row.f.category}}</template></el-table-column><el-table-column label="大小" width="100"><template #default="{row}">{{humanSize(row.f.size)}}</template></el-table-column><el-table-column label="上传时间" width="145"><template #default="{row}">{{stamp(row.f.time)}}</template></el-table-column><el-table-column label="操作" width="190"><template #default="{row}"><div class="actions"><el-button text type="primary" @click="inspect(row.p,row.f)">预览</el-button><el-button v-if="row.f.userId===store.user?.id && !locked(row.p)" text @click="inspect(row.p,row.f,true)">编辑</el-button><a :href="fileDownloadUrl(row.p.id,row.f.id)">下载</a></div></template></el-table-column><template #empty>暂无匹配资料；上传资料或提交任务后会自动出现在这里。</template></el-table>
 <FileLibrary v-if="active" :key="active.id" ref="library" :project="active" :readonly="locked(active)" dialogs-only />
 <el-dialog v-model="uploadOpen" title="上传资料" width="520px"><el-form label-position="top"><el-form-item label="归档到项目"><el-select v-model="uploadProject" aria-label="上传资料所属项目"><el-option v-for="p in writable" :key="p.id" :value="p.id" :label="p.name" /></el-select></el-form-item><DirectUpload v-if="uploadProject" :key="uploadProject" :project-id="uploadProject" /><p class="muted">文件将保存到所选项目，可用于任务提交和项目讨论。</p></el-form><template #footer><el-button @click="uploadOpen=false">完成</el-button></template></el-dialog>
</template>
