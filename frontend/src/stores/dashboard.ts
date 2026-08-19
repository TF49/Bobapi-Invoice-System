import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi, type DashboardStats } from '@/api/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const stats = ref<DashboardStats | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchStats() {
    loading.value = true
    error.value = null
    try {
      stats.value = await dashboardApi.getStats()
    } catch (e: any) {
      error.value = e.message || '获取统计数据失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  return { stats, loading, error, fetchStats }
})