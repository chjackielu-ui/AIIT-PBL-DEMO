<script setup lang="ts">
import {api, request} from '../api'
import {computed, onBeforeUnmount, ref} from 'vue'
import {Upload, Search, FileText, Download, Eye} from 'lucide-vue-next'
import hljs from 'highlight.js/lib/common'
import {ElMessage} from 'element-plus'
import {useStore} from '../store'
import type {Project, ProjectFile} from '../types'
import {run, stamp, humanSize, fileDownloadUrl} from '../lib'

const props = defineProps<{project: Project; readonly: boolean; dialogsOnly?:boolean}>()
const store = useStore()
const s = computed(() => store.state!)
const query = ref('')
const category = ref('文档')
const filter = ref('')
const busy = ref(false)
const input = ref<HTMLInputElement>()
const preview = ref<ProjectFile>()
const url = ref('')
const code = ref('')
const text = ref('')
const loading = ref(false)
const editing = ref<ProjectFile>()
const editName = ref('')
const editCategory = ref('文档')
function editFile(f:ProjectFile){editing.value=f;editName.value=f.name;editCategory.value=f.category}
async function saveFile(){await run(async()=>{await store.act('editFile',{projectId:props.project.id,fileId:editing.value?.id,name:editName.value,category:editCategory.value});editing.value=undefined},'资料已更新')}
/** 预览请求序号：并发点击时丢弃过期响应，避免错配与 blob URL 泄漏。 */
let seq = 0

const rows = computed(() => props.project.files.filter(f =>
  f.name.toLowerCase().includes(query.value.toLowerCase()) && (!filter.value || f.category === filter.value)))

const endpoint = (f: ProjectFile) => `/api/projects/${props.project.id}/files/${f.id}`

/** 预览类型：扩展名优先、MIME 兜底，避免两套规则漂移。 */
function classify(f: ProjectFile): 'image' | 'pdf' | 'text' | 'other' {
  const name = f.name.toLowerCase()
  if (/\.(png|jpe?g|gif|svg|webp|bmp|ico)$/.test(name) || f.type.startsWith('image/')) return 'image'
  if (/\.pdf$/.test(name) || f.type === 'application/pdf') return 'pdf'
  if (/\.(txt|md|json|js|ts|vue|java|py|css|html|xml|sql|csv|yml|yaml|c|cpp|h)$/.test(name) || f.type.startsWith('text/')) return 'text'
  return 'other'
}

const TEXT_PREVIEW_LIMIT = 2 * 1024 * 1024

function clear() {
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  code.value = ''
  text.value = ''
}

async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 20 * 1024 * 1024 || file.size === 0) {
    ElMessage.error('请选择 1 字节至 20 MB 的文件')
    return
  }
  busy.value = true
  await run(async () => {
    const data = new FormData()
    data.append('file', file)
    data.append('category', category.value)
    await api(`/projects/${props.project.id}/files`, data)
    await store.load()
    // 让上传结果立即可见：清除可能挡住新文件的筛选
    filter.value = ''
    query.value = file.name
  }, '文件已上传到本机服务端')
  busy.value = false
  if (input.value) input.value.value = ''
}

async function open(f: ProjectFile) {
  clear()
  preview.value = f
  loading.value = true
  const mySeq = ++seq
  await run(async () => {
    const kind = classify(f)
    if (kind === 'text') {
      // 文本类先看 Content-Length，超限直接短路，不下载整个文件
      const head = await request(endpoint(f), {method: 'HEAD'})
      const length = Number(head.headers.get('Content-Length') || '0')
      if (length > TEXT_PREVIEW_LIMIT) {
        if (mySeq !== seq) return
        text.value = '文件超过 2 MB，请下载后查看。'
        return
      }
    }
    const res = await request(endpoint(f))
    if (!res.ok) {
      const byStatus: Record<number, string> = {
        401: '会话已失效，请重新选择身份',
        403: '无权访问该文件',
        404: '文件不存在',
      }
      throw Error(byStatus[res.status] || '文件读取失败，请确认 Java 服务仍在运行')
    }
    const blob = await res.blob()
    if (mySeq !== seq) return // 过期响应：丢弃
    if (kind === 'image' || kind === 'pdf') {
      url.value = URL.createObjectURL(blob)
    } else if (kind === 'text') {
      if (blob.size > TEXT_PREVIEW_LIMIT) {
        text.value = '文件超过 2 MB，请下载后查看。'
        return
      }
      text.value = await blob.text()
      if (!text.value.trim()) {
        text.value = '（空文件）'
      } else {
        code.value = hljs.highlightAuto(text.value).value
      }
    } else {
      text.value = '此文件格式暂不支持在线预览，请下载到本地查看。'
    }
  }, '')
  if (mySeq === seq) loading.value = false
}

defineExpose({open,editFile})
onBeforeUnmount(clear)
</script>
<template>
 <template v-if="!dialogsOnly">
  <div class="toolbar">
    <el-input v-model="query" placeholder="搜索文件名" clearable>
      <template #prefix><Search :size="16" /></template>
    </el-input>
    <span class="muted">分类：</span>
    <el-select v-model="filter" clearable placeholder="全部分类" aria-label="筛选分类">
      <el-option v-for="v in ['文档', '图片', '代码', '压缩包', '其他']" :key="v" :value="v" />
    </el-select>
    <template v-if="!readonly">
      <span class="muted">上传为：</span>
      <el-select v-model="category" aria-label="上传文件分类">
        <el-option v-for="v in ['文档', '图片', '代码', '压缩包', '其他']" :key="v" :value="v" />
      </el-select>
      <el-button type="primary" :loading="busy" @click="input?.click()"><Upload :size="16" />上传文件</el-button>
      <input ref="input" type="file" hidden @change="upload" />
    </template>
  </div>
  <el-table :data="rows">
    <el-table-column label="文件名称" min-width="230">
      <template #default="{row: f}"><button class="text-button file-name" @click="open(f)"><FileText :size="16" />{{f.name}}</button></template>
    </el-table-column>
    <el-table-column prop="category" label="分类" width="85" />
    <el-table-column label="大小" width="100" show-overflow-tooltip>
      <template #default="{row: f}">{{humanSize(f.size)}}</template>
    </el-table-column>
    <el-table-column label="上传者" width="110">
      <template #default="{row: f}">{{s.users.find(u => u.id === f.userId)?.name}}</template>
    </el-table-column>
    <el-table-column label="上传时间" width="150">
      <template #default="{row: f}">{{stamp(f.time)}}</template>
    </el-table-column>
    <el-table-column label="操作" width="250">
      <template #default="{row: f}">
        <div class="actions">
          <el-button v-if="!readonly && (store.user?.role==='teacher' || f.userId===store.user?.id)" @click="editFile(f)">编辑</el-button>
          <el-button @click="open(f)"><Eye :size="16" />预览</el-button>
          <a :href="fileDownloadUrl(props.project.id, f.id)" download><el-button><Download :size="16" />下载</el-button></a>
        </div>
      </template>
    </el-table-column>
    <template #empty><el-empty description="暂无符合条件的资料" :image-size="56" /></template>
  </el-table>
 </template>
  <el-dialog :model-value="!!editing" title="编辑资料" width="500px" @close="editing=undefined"><el-form label-position="top"><el-form-item label="文件名称"><el-input v-model="editName" /></el-form-item><el-form-item label="分类"><el-select v-model="editCategory"><el-option v-for="v in ['文档','图片','代码','压缩包','其他']" :key="v" :value="v" /></el-select></el-form-item></el-form><template #footer><el-button type="primary" @click="saveFile">保存</el-button></template></el-dialog>
  <el-dialog :model-value="!!preview" :title="preview?.name" width="900px" @close="preview = undefined; clear()">
    <div v-loading="loading" class="file-preview">
      <img v-if="url && preview && classify(preview) === 'image'" :src="url" :alt="preview.name" />
      <iframe v-else-if="url" :src="url" title="PDF 预览" />
      <pre v-else-if="code"><code class="hljs" v-html="code" /></pre>
      <p v-else class="pre-wrap">{{text}}</p>
    </div>
    <template #footer>
      <a v-if="preview" :href="fileDownloadUrl(props.project.id, preview.id)" download><el-button type="primary">下载文件</el-button></a>
      <el-button @click="preview = undefined; clear()">关闭</el-button>
    </template>
  </el-dialog>
</template>
