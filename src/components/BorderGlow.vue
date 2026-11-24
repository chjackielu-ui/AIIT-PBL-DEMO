<script setup lang="ts">
import {useTemplateRef} from 'vue'

// https://vue-bits.dev/components/border-glow uses pointer angle and edge proximity to direct the highlight.
const card = useTemplateRef<HTMLDivElement>('card')
function follow(event:PointerEvent) {
  if (!card.value || event.pointerType === 'touch' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const rect = card.value.getBoundingClientRect()
  const x = event.clientX - rect.left - rect.width / 2
  const y = event.clientY - rect.top - rect.height / 2
  const proximity = Math.min(1, Math.max(Math.abs(x)/(rect.width/2), Math.abs(y)/(rect.height/2)))
  card.value.style.setProperty('--edge-angle', Math.atan2(x, -y) * 180 / Math.PI + 'deg')
  card.value.style.setProperty('--edge-opacity', String(proximity))
}
</script>

<template><div ref="card" class="border-glow" @pointermove="follow"><slot /></div></template>

<style scoped>
.border-glow{position:relative;display:grid;border:1px solid #dbe5f2;border-radius:9px;background:#fff;min-width:0}
.border-glow::after{content:'';position:absolute;pointer-events:none;inset:-1px;border-radius:inherit;padding:1px;background:conic-gradient(from calc(var(--edge-angle, 45deg) - 30deg),transparent,#668ac5 30deg,transparent 62deg);mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);mask-composite:exclude;opacity:0;transition:opacity 160ms ease}
@media(hover:hover) and (prefers-reduced-motion:no-preference){.border-glow:hover::after{opacity:var(--edge-opacity,0)}}
.border-glow:focus-within{border-color:#3456a3}
</style>
