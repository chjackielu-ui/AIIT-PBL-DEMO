<script setup lang="ts">
import {computed} from 'vue'
import type {User} from '../types'
import AccountAvatar from './AccountAvatar.vue'

const {members, expanded = false} = defineProps<{members: User[]; expanded?: boolean}>()
const names = computed(() => members.map(member => member.name).join('、'))
</script>

<template>
  <ul v-if="expanded && members.length" class="team-roster" aria-label="团队成员">
    <li v-for="member in members" :key="member.id">
      <AccountAvatar :user="member" :size="26" />{{ member.name }}
    </li>
  </ul>
  <span v-else-if="members.length" class="team-stack" role="img" :title="names" :aria-label="'团队成员：' + names">
    <AccountAvatar v-for="member in members.slice(0, 3)" :key="member.id" :user="member" :size="26" />
    <span v-if="members.length > 3" class="team-extra" aria-hidden="true">+{{ members.length - 3 }}</span>
  </span>
</template>

<style scoped>
.team-stack,.team-roster,.team-roster li{display:flex;align-items:center}
.team-stack :deep(.account-avatar){border:2px solid white}
.team-stack :deep(.account-avatar + .account-avatar){margin-left:-6px}
.team-extra{margin-left:4px;color:var(--muted);font-size:12px}
.team-roster{flex-wrap:wrap;gap:8px 14px;list-style:none;margin:0;padding:0}
.team-roster li{gap:5px;font-size:13px}
</style>
