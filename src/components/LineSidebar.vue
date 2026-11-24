<script setup lang="ts">
import {useTemplateRef} from 'vue'

// https://vue-bits.dev/components/line-sidebar provides the pointer-proximity interaction.
const {items, active} = defineProps<{items:{id:string;label:string;preview:string}[];active:string}>()
const emit = defineEmits<{select:[id:string]}>()
const list = useTemplateRef<HTMLUListElement>('list')

function proximity(event:PointerEvent) {
  if (event.pointerType === 'touch' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  for (const child of list.value?.children || []) {
    const row = child as HTMLElement
    const {top, height} = row.getBoundingClientRect()
    const factor = Math.max(0, 1-Math.abs(event.clientY-top-height/2)/84)
    row.style.setProperty('--effect', String(factor*factor*(3-2*factor)))
  }
}
function reset() {
  for (const row of list.value?.children || []) (row as HTMLElement).style.removeProperty('--effect')
}
</script>

<template>
  <nav aria-label="个人时间线月份" class="line-sidebar">
    <ul ref="list" @pointermove="proximity" @pointerleave="reset">
      <li v-for="item in items" :key="item.id">
        <button type="button" :aria-current="item.id === active ? 'true' : undefined" :title="item.label + ' · ' + item.preview" @click="emit('select', item.id)">
          <span class="tick" aria-hidden="true" />
          <span class="label">{{ item.label }}</span>
          <span class="hint">{{ item.preview }}</span>
        </button>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.line-sidebar{border-right:1px solid var(--line);padding:4px 10px 4px 0;min-width:0}
.line-sidebar ul{max-height:360px;overflow:auto;padding:0;margin:0;list-style:none}
.line-sidebar li{--effect:0}
.line-sidebar button{display:grid;grid-template-columns:18px minmax(0,1fr);text-align:left;gap:1px 5px;width:100%;border:0;background:none;color:#43566e;padding:7px 5px;border-radius:5px}
.line-sidebar button[aria-current],.line-sidebar button:hover,.line-sidebar button:focus-visible{background:#edf3fc;color:#254787}
.tick{height:2px;width:15px;align-self:center;background:#7393bd;transform-origin:left;transform:scaleX(calc(.55 + var(--effect)*.6));transition:transform 140ms ease}
[aria-current] .tick{background:#3456a3;transform:scaleX(1.15)}
.label,.hint{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;transform:translateX(calc(var(--effect)*3px));transition:transform 140ms ease}
.label{font-weight:650}.hint{grid-column:2;color:#55677f;font-size:12px}
@media(prefers-reduced-motion:reduce){.tick,.label,.hint{transition:none;transform:none}}
@media(max-width:620px){.line-sidebar{border-right:0;border-bottom:1px solid var(--line);padding:0 0 8px}.line-sidebar ul{display:flex;overflow:auto}.line-sidebar li{flex:0 0 148px}}
</style>
