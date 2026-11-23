import {api} from './api'
import {isCodeFile} from './discussion'
import type {ProjectFile} from './types'
import {isDocument} from './fileTypes'

export const fileCategories = ['文档', '图片', '代码', '压缩包', '其他']

export async function uploadProjectFile(projectId: string, file: File, folderId = '', personal = false, downloadable = true) {
  if (!file.size || file.size > 20 * 1024 * 1024) throw Error('文件大小应为 1 字节至 20 MB')
  const form = new FormData()
  form.set('file', file)
  form.set('folderId', folderId)
  form.set('downloadable',String(downloadable))
  const meta = {name:file.name,type:file.type}
  form.set('category', file.type.startsWith('image/') ? '图片' : /\.(zip|rar|7z|gz|tar)$/i.test(file.name) ? '压缩包'
    : isDocument(meta) || /\.(txt|md|odt|rtf|xlsx?|csv)$/i.test(file.name) ? '文档'
    : isCodeFile(meta) ? '代码' : '其他')
  return api<ProjectFile>(personal ? '/library/files' : `/projects/${projectId}/files`, form)
}
