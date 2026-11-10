import { defineStore } from 'pinia'
import { ref } from 'vue'
import { announcementApi, type Announcement } from '@/api/announcement'

/** 同一登录会话内，两次自动检查未读公告的最小间隔 */
export const RECHECK_INTERVAL_MS = 5 * 60 * 1000

export const useAnnouncementStore = defineStore('announcement', () => {
  const unread = ref<Announcement[]>([])
  const popupVisible = ref(false)
  const checkedToken = ref('')
  const lastCheckedAt = ref(0)
  let pending: Promise<void> | null = null

  /**
   * 检查未读公告并在有未读时弹窗。
   * 换了登录凭证会立即检查；同一凭证下最多每 RECHECK_INTERVAL_MS 检查一次。
   */
  function checkUnread(token: string): Promise<void> {
    if (!token || popupVisible.value) return Promise.resolve()
    const sameSession = checkedToken.value === token
    if (sameSession && Date.now() - lastCheckedAt.value < RECHECK_INTERVAL_MS) return Promise.resolve()
    if (pending) return pending

    checkedToken.value = token
    lastCheckedAt.value = Date.now()
    pending = announcementApi
      .getUnread()
      .then((list) => {
        // 请求期间已登出或切换账号，丢弃结果
        if (checkedToken.value !== token) return
        unread.value = list
        popupVisible.value = list.length > 0
      })
      .catch((error) => {
        console.error('获取系统公告失败:', error)
      })
      .finally(() => {
        pending = null
      })
    return pending
  }

  async function markRead(ids: number[]) {
    const unique = [...new Set(ids)]
    if (unique.length === 0) return
    unread.value = unread.value.filter((item) => !unique.includes(item.id))
    try {
      await announcementApi.markRead(unique)
    } catch (error) {
      console.error('标记公告已读失败:', error)
    }
  }

  function reset() {
    unread.value = []
    popupVisible.value = false
    checkedToken.value = ''
    lastCheckedAt.value = 0
  }

  return { unread, popupVisible, checkUnread, markRead, reset }
})
