<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import type {User} from '../types'

const props = withDefaults(defineProps<{user: User; size?: number; portrait?: boolean}>(), {size: 34})
const src = computed(() => props.user.avatarVersion ? '/api/profile/avatar?v=' + props.user.avatarVersion : '')
const failed = ref(false)
watch(src, () => { failed.value = false })
</script>

<template>
  <span class="account-avatar" :class="{portrait}" :style="portrait ? undefined : {width: size + 'px', height: size + 'px', fontSize: Math.max(12, size * .38) + 'px'}" aria-hidden="true">
    <img v-if="src && !failed" :src="src" alt="" @error="failed = true">
    <span v-else>{{user.name.slice(0, 1)}}</span>
  </span>
</template>

<style scoped>
.account-avatar {display:inline-grid; flex:none; place-items:center; border-radius:50%; overflow:hidden; background:#e9f0fb; color:var(--primary); font-weight:650}
.account-avatar img {display:block; width:100%; height:100%; object-fit:cover}
.account-avatar.portrait {width:100%;height:100%;border-radius:0;background:white}
.account-avatar.portrait img {object-position:50% 22%}
</style>
