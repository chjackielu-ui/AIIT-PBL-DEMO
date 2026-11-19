import {ElMessage, ElMessageBox} from 'element-plus'

export const roleName: Record<string, string> = {student: '学生', teacher: '教师', admin: '管理员'}

export async function run(fn: () => Promise<unknown>, message = '已保存') {
  try {
    await fn()
    if (message) ElMessage.success(message)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

export async function confirmAction(text: string, fn: () => Promise<unknown>, success?: string) {
  try {
    await ElMessageBox.confirm(text, '操作确认', {confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'})
    await run(fn, success ?? '已保存')
  } catch {}
}

export function printPage(kind: string, id = '') {
  window.open(`/print/${kind}/${id}`, '_blank', 'noopener')
}

export function openLink(url: string) {
  window.open(url, '_blank', 'noopener')
}

export function safeLink(url: string) {
  try {
    const u = new URL(url)
    return ['http:', 'https:'].includes(u.protocol) ? url : undefined
  } catch {
    return undefined
  }
}

export const stamp = (s: string) => s?.replace('T', ' ').slice(0, 16)

/** 文件下载地址（资料库 / 讨论附件 / 概览下载三处共用）。 */
export function fileDownloadUrl(projectId: string, fileId: string) {
  return `/api/projects/${projectId}/files/${fileId}?download=true`
}

/** 文件大小展示：<1KB 用字节，≥1MB 用 MB，其余 KB。 */
export function humanSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024).toFixed(1)} KB`
}

/** 本地时区（Asia/Shanghai）的 YYYY-MM-DD，避免 toISOString 的 UTC 偏移。 */
export function localDate(offsetDays = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 只允许站内路径的 redirect 目标校验（防开放重定向）。 */
export function safeRedirect(value: string | null) {
  if (!value) return ''
  return value.startsWith('/') && !value.startsWith('//') ? value : ''
}
