<template>
  <div id="app">
    <router-view />
    <AnnouncementPopup v-if="announcementsEnabled" />
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import AnnouncementPopup from '@/components/AnnouncementPopup.vue'
import { useAnnouncementStore } from '@/stores/announcement'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const announcementStore = useAnnouncementStore()

/** 仅在已登录且处于需要鉴权的页面时检查/展示系统公告 */
const announcementsEnabled = computed(() => Boolean(userStore.token) && route.meta.requiresAuth === true)

watch(
  [announcementsEnabled, () => userStore.token, () => route.fullPath],
  ([enabled, token]) => {
    if (enabled && token) {
      void announcementStore.checkUnread(token)
    } else if (!token) {
      announcementStore.reset()
    }
  },
  { immediate: true }
)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  margin: 0;
}

#app {
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
</style>
