import type {Directive} from 'vue'
import {ElMessage} from 'element-plus'

export function hasFiles(event: DragEvent) {
  return event.dataTransfer?.types.includes('Files')
}

function matches(file: File, accept: string) {
  const type = file.type.toLowerCase(), name = file.name.toLowerCase()
  return !accept || accept.toLowerCase().split(',').map(value => value.trim()).some(value =>
    value.startsWith('.') ? name.endsWith(value) : value.endsWith('/*') ? type.startsWith(value.slice(0, -1)) : type === value)
}

const cleanup = new WeakMap<HTMLElement, () => void>()

export const fileDrop: Directive<HTMLElement, string | undefined> = {
  mounted(element, {value}) {
    const input = () => element.querySelector<HTMLInputElement>(value || 'input[type=file]')
    const reset = () => element.classList.remove('file-drop-active')
    const over = (event: DragEvent) => {
      if (!hasFiles(event)) return
      event.preventDefault()
      event.stopPropagation()
      const enabled = !!input() && !input()?.disabled
      if (event.dataTransfer) event.dataTransfer.dropEffect = enabled ? 'copy' : 'none'
      element.classList.toggle('file-drop-active', enabled)
    }
    const leave = (event: DragEvent) => {
      if (!(event.relatedTarget instanceof Node && element.contains(event.relatedTarget))) reset()
    }
    const drop = (event: DragEvent) => {
      if (!hasFiles(event)) return
      event.preventDefault()
      event.stopPropagation()
      reset()
      const target = input(), files = Array.from(event.dataTransfer?.files ?? [])
      if (!target || target.disabled || !files.length) return
      if ((!target.multiple && files.length > 1) || files.some(file => !matches(file, target.accept))) {
        ElMessage.warning(target.multiple ? '请拖入支持的文件格式' : '请拖入一个受支持格式的文件')
        return
      }
      const transfer = new DataTransfer()
      for (const file of files) transfer.items.add(file)
      target.files = transfer.files
      target.dispatchEvent(new Event('change', {bubbles:true}))
    }
    element.addEventListener('dragover', over)
    element.addEventListener('dragleave', leave)
    element.addEventListener('drop', drop)
    cleanup.set(element, () => {
      element.removeEventListener('dragover', over)
      element.removeEventListener('dragleave', leave)
      element.removeEventListener('drop', drop)
    })
  },
  unmounted(element) {cleanup.get(element)?.(); cleanup.delete(element)},
}
