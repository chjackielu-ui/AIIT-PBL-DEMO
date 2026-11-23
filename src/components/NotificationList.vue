<script setup lang="ts">
import type {Notice} from '../types'
import {stamp} from '../lib'
import {useNotificationActions} from '../notifications'

defineProps<{notices: Notice[]}>()
const {open} = useNotificationActions()
</script>
<template>
  <RouterLink v-for="n in notices" :key="n.id" :to="n.path" class="notice-summary" @click.prevent="open(n)">
    <strong>{{n.title}}</strong>
    <span>{{n.text}}</span>
    <small class="muted">{{stamp(n.time)}}</small>
  </RouterLink>
</template>
